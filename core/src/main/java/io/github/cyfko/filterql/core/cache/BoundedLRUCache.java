package io.github.cyfko.filterql.core.cache;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A bounded LRU (Least Recently Used) cache implementation using a {@link Deque}
 * for automatic eviction of oldest entries.
 * <p>
 * This cache maintains a maximum size and automatically evicts the least recently
 * used entries when the capacity is exceeded. It uses a simple and efficient
 * implementation without external dependencies.
 * </p>
 *
 * <h2>Implementation Strategy</h2>
 * <ul>
 *   <li><strong>Deque for ordering</strong>: Tracks insertion/access order</li>
 *   <li><strong>Map for storage</strong>: Fast O(1) lookups</li>
 *   <li><strong>ReadWriteLock</strong>: Thread-safe concurrent access</li>
 *   <li><strong>Automatic eviction</strong>: Oldest entries removed when full</li>
 * </ul>
 *
 * <h2>Why No External Libraries?</h2>
 * <p>
 * For FilterQL's caching needs, external libraries like Caffeine are overkill because:
 * </p>
 * <ul>
 *   <li><strong>Finite keyspace</strong>: Number of unique filter structures is bounded
 *       (combinations of properties × operators)</li>
 *   <li><strong>No TTL needed</strong>: Filter structures don't expire (code is static)</li>
 *   <li><strong>Simple eviction</strong>: LRU is sufficient (no complex policies needed)</li>
 *   <li><strong>Minimal overhead</strong>: No stats, no async operations, no complex configuration</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This cache is fully thread-safe using a {@link ReadWriteLock}:
 * </p>
 * <ul>
 *   <li>Multiple concurrent reads (no blocking)</li>
 *   <li>Exclusive writes (blocks other writes and reads)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create cache with max 1000 entries
 * BoundedLRUCache<String, Condition> cache = new BoundedLRUCache<>(1000);
 *
 * // Put entry
 * cache.put("cacheKey", condition);
 *
 * // Get entry (moves to front - LRU)
 * Condition cached = cache.get("cacheKey");
 *
 * // Check size
 * int size = cache.size();
 *
 * // Clear all
 * cache.clear();
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class BoundedLRUCache<K, V> {

    private final int maxSize;
    private final Map<K, V> cache;
    private final Deque<K> accessOrder;
    private final ReadWriteLock lock;

    /**
     * Creates a bounded LRU cache with the specified maximum size.
     *
     * @param maxSize the maximum number of entries to store
     * @throws IllegalArgumentException if maxSize is not positive
     */
    public BoundedLRUCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }

        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize);
        this.accessOrder = new ConcurrentLinkedDeque<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Retrieves a value from the cache.
     * <p>
     * If the key exists, it is moved to the front of the access order (most recently used).
     * </p>
     *
     * @param key the key to look up
     * @return the cached value, or null if not present
     */
    public V get(K key) {
        lock.readLock().lock();
        try {
            V value = cache.get(key);
            if (value != null) {
                // Move to front (most recently used)
                // Note: For true LRU, we'd need write lock here, but for simplicity
                // we accept minor inaccuracy in access order tracking
                updateAccessOrder(key);
            }
            return value;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Stores a key-value pair in the cache.
     * <p>
     * If the cache is at capacity, the least recently used entry is evicted.
     * If the key already exists, its value is updated and it becomes the most recently used.
     * </p>
     *
     * @param key the key to store
     * @param value the value to store
     */
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            // If key already exists, remove from old position
            if (cache.containsKey(key)) {
                accessOrder.remove(key);
            }

            // Add to cache and front of access order
            cache.put(key, value);
            accessOrder.addFirst(key);

            // Evict oldest if over capacity
            while (accessOrder.size() > maxSize) {
                K oldest = accessOrder.removeLast();
                cache.remove(oldest);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Computes and caches a value if the key is not already present.
     * <p>
     * This is an atomic operation: if multiple threads call this method concurrently
     * with the same key, the mapping function is called only once.
     * </p>
     *
     * @param key the key to compute for
     * @param mappingFunction the function to compute the value
     * @return the cached or newly computed value
     */
    public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction) {
        // Fast path: check if already cached (read lock)
        V value = get(key);
        if (value != null) {
            return value;
        }

        // Slow path: compute and cache (write lock)
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            value = cache.get(key);
            if (value != null) {
                return value;
            }

            // Compute value
            value = mappingFunction.apply(key);
            if (value != null) {
                put(key, value);
            }
            return value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the current number of entries in the cache.
     *
     * @return the cache size
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes all entries from the cache.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            accessOrder.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if the cache contains a specific key.
     *
     * @param key the key to check
     * @return true if the key is present
     */
    public boolean containsKey(K key) {
        lock.readLock().lock();
        try {
            return cache.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes a specific key from the cache.
     *
     * @param key the key to remove
     * @return the removed value, or null if not present
     */
    public V remove(K key) {
        lock.writeLock().lock();
        try {
            accessOrder.remove(key);
            return cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the maximum capacity of this cache.
     *
     * @return the maximum size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Updates the access order for a key (moves to front).
     * <p>
     * Note: This is called from read lock context, which is imperfect for true LRU
     * but acceptable for the performance trade-off.
     * </p>
     *
     * @param key the key to update
     */
    private void updateAccessOrder(K key) {
        // This is a best-effort update from read lock
        // For perfect LRU, would need write lock, but that would hurt read performance
        accessOrder.remove(key);
        accessOrder.addFirst(key);
    }

    /**
     * Returns cache statistics as a formatted string.
     *
     * @return statistics string
     */
    public String getStats() {
        lock.readLock().lock();
        try {
            return String.format("BoundedLRUCache[size=%d, maxSize=%d, utilization=%.1f%%]",
                cache.size(),
                maxSize,
                (cache.size() * 100.0) / maxSize
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}

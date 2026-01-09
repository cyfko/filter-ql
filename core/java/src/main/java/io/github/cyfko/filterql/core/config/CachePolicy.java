package io.github.cyfko.filterql.core.config;

/**
 * Configuration for caching strategy.
 * <p>
 * Controls performance optimization through caching.
 * </p>
 *
 * <h2>Configurable Limits</h2>
 * <ul>
 *   <li><strong>cacheEnabled</strong>: Enable FilterTree caching (default: true)</li>
 *   <li><strong>cacheSize</strong>: Maximum cache entries (default: 1000)</li>
 * </ul>
 *
 * <h2>Preset Configurations</h2>
 * <pre>{@code
 * // Default (balanced for most use cases)
 * CachePolicy config = CachePolicy.defaults();
 *
 * // Strict (for public APIs with untrusted input)
 * CachePolicy config = CachePolicy.strict();
 *
 * // Relaxed (for internal trusted systems)
 * CachePolicy config = CachePolicy.relaxed();
 *
 * // None (no cache at all)
 * CachePolicy config = CachePolicy.none();
 *
 * // Custom
 * CachePolicy config = CachePolicy.builder()
 *     .cacheEnabled(true)
 *     .cacheSize(2000)
 *     .build();
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public record CachePolicy(
        boolean cacheEnabled,
        int cacheSize
) {

    /**
     * Canonical constructor with validation.
     */
    public CachePolicy {
        if (cacheSize <= 0) {
            throw new IllegalArgumentException("cacheSize must be positive, got: " + cacheSize);
        }
    }

    /**
     * Default configuration for usability.
     * <p>
     * Suitable for most production use cases with moderate complexity requirements.
     * </p>
     * <ul>
     *   <li>Cache Enabled: true</li>
     *   <li>Cache Size: 1000 entries</li>
     * </ul>
     *
     * @return default configuration
     */
    public static CachePolicy defaults() {
        return new CachePolicy(
                true,   // cacheEnabled
                1000     // cacheSize
        );
    }

    /**
     * Strict configuration for public APIs and untrusted input.
     * <p>
     * Recommended for APIs exposed to external clients or untrusted sources.
     * </p>
     * <ul>
     *   <li>Cache Enabled: true</li>
     *   <li>Cache Size: 500 entries (smaller due to stricter limits)</li>
     * </ul>
     *
     * @return strict configuration
     */
    public static CachePolicy strict() {
        return new CachePolicy(
                true,   // cacheEnabled
                500     // cacheSize
        );
    }

    /**
     * Relaxed configuration for internal trusted systems.
     * <p>
     * Suitable for internal applications and trusted batch processes.
     * </p>
     * <ul>
     *   <li>Cache Enabled: true</li>
     *   <li>Cache Size: 2000 entries (larger for complex queries)</li>
     * </ul>
     *
     * @return relaxed configuration
     */
    public static CachePolicy relaxed() {
        return new CachePolicy(
                true,   // cacheEnabled
                2000     // cacheSize
        );
    }

    /**
     * No cache configuration: caching is completely disabled.
     * <p>
     * Useful for scenarios where caching overhead should be minimized or when
     * each filter request is unique (no benefit from caching).
     * </p>
     * <ul>
     *   <li>Cache Enabled: false</li>
     *   <li>Cache Size: 1 (minimal, unused)</li>
     * </ul>
     *
     * @return a CachePolicy with caching disabled
     */
    public static CachePolicy none() {
        return new CachePolicy(false, 1);
    }

    public static CachePolicy custom(int cacheSize) {
        return new CachePolicy(true, cacheSize);
    }
}

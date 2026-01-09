package io.github.cyfko.filterql.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable pagination configuration for query execution.
 *
 * <p>
 * Specifies page number, page size, and optional multi-field sorting. All components
 * are validated during construction with defensive immutability for thread-safety.
 * </p>
 *
 * <h2>Component Details</h2>
 * <dl>
 *   <dt><strong>{@code page}</strong></dt>
 *   <dd>Zero-based page index (0 = first page). Must be {@code >= 0}.</dd>
 *
 *   <dt><strong>{@code size}</strong></dt>
 *   <dd>Number of records per page. Must be {@code > 0}. Common values: 10, 20, 50, 100.</dd>
 *
 *   <dt><strong>{@code sort}</strong></dt>
 *   <dd>Ordered list of {@link SortBy} instances defining multi-column sorting.
 *       {@code null} indicates no sorting (natural order). Empty list normalized to {@code null}.</dd>
 * </dl>
 *
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>{@code size > 0} - Prevents invalid page sizes</li>
 *   <li>{@code page >= 0} - Prevents negative page indices</li>
 *   <li>Sort list defensively copied and made unmodifiable</li>
 *   <li>Empty sort lists normalized to {@code null}</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // First page, 20 records, no sorting
 * Pagination p1 = new Pagination(0, 20, null);
 *
 * // Page 2 (records 21-40), sorted by name ASC
 * Pagination p2 = new Pagination(1, 20, List.of(new SortBy("name", "ASC")));
 *
 * // Multi-column sort: name ASC, then createdAt DESC
 * Pagination p3 = new Pagination(0, 50, List.of(
 *     new SortBy("name", "ASC"),
 *     new SortBy("createdAt", "DESC")
 * ));
 * }</pre>
 *
 * @param page Zero-based page number ({@code >= 0})
 * @param size Number of records per page ({@code > 0})
 * @param sort Immutable list of {@link SortBy}s (null = no sorting)
 *
 * @throws IllegalArgumentException if {@code size <= 0} or {@code page < 0}
 *
 * @author Frank KOSSI
 * @since 6.0.0
 * @see SortBy
 * @see FilterRequest
 */
public record Pagination(int page, int size, List<SortBy> sort) {

    /**
     * Canonical constructor with comprehensive validation and defensive copying.
     *
     * <p>
     * Performs immediate validation of page/size constraints and creates defensive
     * immutable copy of the sort list. Empty sort lists are normalized to {@code null}.
     * </p>
     *
     * <p><strong>Validation Sequence:</strong></p>
     * <ol>
     *   <li>Check {@code size > 0}</li>
     *   <li>Check {@code page >= 0}</li>
     *   <li>Defensive copy sort list → {@code Collections.unmodifiableList()}</li>
     *   <li>Normalize empty sort → {@code null}</li>
     * </ol>
     * </p>
     *
     * @throws IllegalArgumentException if {@code size <= 0}: "Page size must be positive. Provided: {size}"
     * @throws IllegalArgumentException if {@code page < 0}: "Page number cannot be negative. Provided: {page}"
     */
    public Pagination {
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must be positive. Provided: " + size);
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative. Provided: " + page);
        }

        // Defensive copy and immutability for thread-safety
        if (sort != null && !sort.isEmpty()) {
            sort = Collections.unmodifiableList(new ArrayList<>(sort));
        } else {
            sort = null;  // Normalize empty lists to null
        }
    }

    /**
     * Returns {@code true} if sorting is configured.
     *
     * @return {@code true} if {@code sort != null}, {@code false} otherwise
     */
    public boolean hasSort() {
        return sort != null;
    }

    /**
     * Calculates the zero-based offset for database queries.
     *
     * <p>Example: {@code page=2, size=20} → {@code offset=40}</p>
     *
     * @return {@code page * size} (never negative)
     */
    public int offset() {
        return Math.multiplyExact(page, size);
    }

    /**
     * Calculates the one-based page number for display purposes.
     *
     * @return {@code page + 1}
     */
    public int displayPage() {
        return page + 1;
    }

    /**
     * Debug-friendly string representation.
     *
     * @return Formatted string showing page, size, and sort configuration
     */
    @Override
    public String toString() {
        return String.format("Pagination{page=%d, size=%d, sort=%s}",
                page, size, sort);
    }
}

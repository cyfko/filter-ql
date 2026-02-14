package io.github.cyfko.filterql.spring.pagination;

/**
 * Immutable record representing pagination metadata for REST API responses.
 * <p>
 * This record holds key pagination details such as the current page, total pages, page size, and element count.
 * It is typically used to provide pagination summary data within paginated API response payloads.
 * </p>
 *
 * @param currentPage   the current 0-based page index
 * @param totalPages    the total number of pages available
 * @param pageSize      the size of each page
 * @param totalElements the total number of elements across all pages
 * @param hasNext       if there is a next page available
 * @param hasPrevious   if there is a previous page
 */
public record PaginationInfo(
        int currentPage,
        int totalPages,
        int pageSize,
        long totalElements,
        boolean hasNext,
        boolean hasPrevious
) {
    /**
     * Creates pagination metadata given explicit values.
     *
     * @param currentPage    the current 0-based page index
     * @param pageSize       the size of each page
     * @param totalElements  the total number of elements across all pages
     */
    public PaginationInfo(int currentPage, int pageSize, long totalElements) {
        this(currentPage,
                (int) Math.ceil((double) totalElements / pageSize),
                pageSize,
                totalElements,
                currentPage < ((int) Math.ceil((double) totalElements / pageSize)) - 1,
                currentPage > 0
        );
    }
}
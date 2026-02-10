package io.github.cyfko.filterql.spring.pagination;

import org.springframework.data.domain.Page;

/**
 * Immutable record representing pagination metadata for REST API responses.
 * <p>
 * This record extracts key pagination details from a Spring Data {@link Page}
 * instance such as the current page, total pages, page size, and element count.
 * It is typically used to provide pagination summary data within paginated API response payloads.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Page<UserDto> page = userService.findAll(PageRequest.of(0, 20));
 * PaginationInfo info = PaginationInfo.from(page);
 *
 * return new PagedResponse<>(page.getContent(), info);
 * }</pre>
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
                currentPage > 0);
    }

    /**
     * Creates a {@link PaginationInfo} instance based on the provided {@link Page}.
     *
     * @param page a Spring Data {@code Page} object containing paginated data
     * @return a populated {@code PaginationInfo} instance
     */
    public static PaginationInfo from(Page<?> page) {
        return new PaginationInfo(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}
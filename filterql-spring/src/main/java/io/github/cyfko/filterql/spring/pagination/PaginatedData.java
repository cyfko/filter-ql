package io.github.cyfko.filterql.spring.pagination;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable record encapsulating a list of data and associated pagination metadata.
 *
 * <p>This record stores a list of data elements of generic type {@code T} along with
 * {@link PaginationInfo} summarizing paging details such as current page, total pages,
 * page size, and total elements.</p>
 *
 * <h2>Usage</h2>
 * <p>
 * Typically used to wrap paginated query results for REST responses or service layers,
 * facilitating clean separation of data and pagination state.
 * </p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * Page<UserDto> page = userService.findUsers(PageRequest.of(0, 20));
 * PaginatedData<UserDto> response = new PaginatedData<>(page);
 * }</pre>
 *
 * <h2>Mapping</h2>
 * <p>
 * Supports transformation of contained data elements using the {@link #map(Function)} method,
 * returning a new {@code PaginatedData} instance with mapped data but the same pagination info.
 * Use the static {@link #of(Page, Function)} method for converting and mapping simultaneously.
 * </p>
 *
 * @param <T> the type of data contained in the page
 */
public record PaginatedData<T>(
        List<T> data,
        PaginationInfo pagination
) {
    /**
     * Creates a paginated data instance with explicit data and pagination info.
     *
     * @param data       the list of data items for this page
     * @param pagination the pagination metadata
     */
    public PaginatedData(List<T> data, PaginationInfo pagination) {
        this.data = List.copyOf(data);
        this.pagination = pagination;
    }

    /**
     * Creates a paginated data instance from a stream of data and pagination info.
     *
     * @param dataStream a stream of data items
     * @param pagination the pagination metadata
     */
    public PaginatedData(Stream<T> dataStream, PaginationInfo pagination) {
        this(dataStream.collect(Collectors.toList()), pagination);
    }

    /**
     * Creates a paginated data instance from a Spring Data {@link Page}.
     * Extracts content and pagination metadata automatically.
     *
     * @param page the Spring Data page to convert
     */
    public PaginatedData(Page<T> page) {
        this(page.getContent(), PaginationInfo.from(page));
    }

    /**
     * Transforms the content data to another type applying the given mapper function,
     * preserving the pagination metadata.
     *
     * @param <R>    the target element type after mapping
     * @param mapper the mapping function to convert data elements
     * @return a new {@code PaginatedData} instance with mapped content and original pagination info
     */
    public <R> PaginatedData<R> map(Function<T, R> mapper) {
        return new PaginatedData<>(data.stream().map(mapper), pagination);
    }

    /**
     * Convenience factory method combining page extraction and mapping in one step.
     *
     * @param <U>    the source element type in the page
     * @param <R>    the target element type after mapping
     * @param page   the Spring Data page instance to convert and map
     * @param mapper the function to convert elements from U to R
     * @return a new {@code PaginatedData} with mapped content and pagination info
     */
    public static <U, R> PaginatedData<R> of(Page<U> page, Function<U, R> mapper) {
        return new PaginatedData<>(page.getContent().stream().map(mapper).collect(Collectors.toList()), PaginationInfo.from(page));
    }
}

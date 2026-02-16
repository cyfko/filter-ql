package io.github.cyfko.filterql.spring.pagination;

import java.util.Map;

/**
 * Functional interface for transforming a raw {@code Map<String, Object>} query
 * result
 * row into a typed domain object.
 * <p>
 * This interface is used with
 * {@link io.github.cyfko.filterql.spring.service.FilterQlService#search(Class, io.github.cyfko.filterql.core.model.FilterRequest, ResultMapper)}
 * to provide custom mapping logic when the caller needs full control over how
 * result
 * maps are converted to DTOs.
 * </p>
 *
 * <p>
 * <strong>Usage example:</strong>
 * </p>
 * 
 * <pre>{@code
 * ResultMapper<UserDTO> mapper = row -> new UserDTO(
 *         (Long) row.get("id"),
 *         (String) row.get("username"),
 *         (String) row.get("email"));
 * PaginatedData<UserDTO> result = filterQlService.search(UserDTO.class, request, mapper);
 * }</pre>
 *
 * <p>
 * <strong>Alternative:</strong> For interface-based projections, prefer
 * {@link io.github.cyfko.filterql.spring.service.FilterQlService#searchAs(Class, io.github.cyfko.filterql.core.model.FilterRequest)
 * searchAs}
 * which uses dynamic proxies to create typed interface implementations
 * automatically,
 * eliminating the need for a manual mapper.
 * </p>
 *
 * @param <R> the target type produced by the mapping
 * @see io.github.cyfko.filterql.spring.service.FilterQlService
 * @author Frank KOSSI
 * @since 4.0.0
 */
public interface ResultMapper<R> {
    R map(Map<String, Object> item);
}

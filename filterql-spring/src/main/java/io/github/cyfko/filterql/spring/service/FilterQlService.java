package io.github.cyfko.filterql.spring.service;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.filterql.spring.pagination.ResultMapper;

import java.util.Map;

/**
 * Main service for using FilterQL in Spring Boot applications without direct
 * manipulation of PropertyRef enums.
 * <p>
 * Provides high-level methods for dynamic filtering, pagination, and query
 * execution using raw JSON filter requests.
 * Integrates with Spring Data JPA repositories and leverages generated
 * {@link JpaFilterContext} for type safety and validation.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 * <li>Injectable Spring {@code @Service} for controllers and business
 * logic</li>
 * <li>Accepts raw JSON filter requests mapped to {@link FilterRequest}</li>
 * <li>Supports both paginated and non-paginated queries</li>
 * <li>Handles context resolution and Specification generation internally</li>
 * </ul>
 *
 * <h2>Extension Points</h2>
 * <ul>
 * <li>Override for custom query logic or security filtering</li>
 * <li>Extend for additional result formats or projections</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public interface FilterQlService {
        /**
         * Searches entities using the given property reference enum and filter request,
         * returning paginated results as raw key-value maps.
         *
         * <p>
         * Each result row is a {@code Map<String, Object>} where keys are the
         * projection
         * field names and values are the projected data.
         * </p>
         *
         * @param refClass      the generated property reference enum class
         *                      (e.g., {@code UserDTO_.class})
         * @param filterRequest the filter request containing filters, pagination, and
         *                      DSL
         * @param <P>           the property reference enum type
         * @return paginated results as maps of field name to value
         */
        <P extends Enum<P> & PropertyReference> PaginatedData<Map<String, Object>> search(Class<P> refClass,
                        FilterRequest<P> filterRequest);

        /**
         * Searches entities and maps each result row using the given
         * {@link ResultMapper}.
         *
         * <p>
         * This overload allows transforming raw projection maps into typed DTOs or
         * other
         * custom representations.
         * </p>
         *
         * @param projectionClass the projection interface class
         * @param filterRequest   the filter request
         * @param resultMapper    mapper to transform each {@code Map<String, Object>}
         *                        result
         * @param <R>             the target result type
         * @param <P>             the property reference enum type
         * @return paginated results mapped by the given mapper
         */
        <R, P extends Enum<P> & PropertyReference> PaginatedData<R> search(Class<R> projectionClass,
                        FilterRequest<P> filterRequest, ResultMapper<R> resultMapper);

        /**
         * Searches and returns results as typed projection interface instances.
         *
         * <p>
         * The returned objects are dynamic proxies implementing
         * {@code projectionInterface}.
         * Non-projected fields throw
         * {@link io.github.cyfko.filterql.spring.projection.FieldNotProjectedException}
         * on access. Jackson serialization includes only projected fields.
         * </p>
         *
         * @param projectionInterface the projection interface (annotated with
         *                            {@code @Projection})
         * @param filterRequest       the filter request
         * @param <T>                 the projection interface type
         * @param <P>                 the property reference enum type
         * @return paginated results as dynamic proxy implementations of the projection
         *         interface
         */
        <T, P extends Enum<P> & PropertyReference> PaginatedData<T> searchAs(Class<T> projectionInterface,
                        FilterRequest<P> filterRequest);
}
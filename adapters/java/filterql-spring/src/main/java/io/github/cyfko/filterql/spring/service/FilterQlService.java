package io.github.cyfko.filterql.spring.service;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.filterql.spring.pagination.ResultMapper;

import java.util.Map;

/**
 * Main service for using FilterQL in Spring Boot applications without direct manipulation of PropertyRef enums.
 * <p>
 * Provides high-level methods for dynamic filtering, pagination, and query execution using raw JSON filter requests.
 * Integrates with Spring Data JPA repositories and leverages generated {@link JpaFilterContext} for type safety and validation.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Injectable Spring {@code @Service} for controllers and business logic</li>
 *   <li>Accepts raw JSON filter requests mapped to {@link FilterRequest}</li>
 *   <li>Supports both paginated and non-paginated queries</li>
 *   <li>Handles context resolution and Specification generation internally</li>
 * </ul>
 *
 * <h2>Extension Points</h2>
 * <ul>
 *   <li>Override for custom query logic or security filtering</li>
 *   <li>Extend for additional result formats or projections</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public interface FilterQlService {
    <P extends Enum<P> & PropertyReference> PaginatedData<Map<String,Object>> search(Class<P> refClass, FilterRequest<P> filterRequest);
    <R,P extends Enum<P> & PropertyReference> PaginatedData<R> search(Class<R> projectionClass, FilterRequest<P> filterRequest, ResultMapper<R> resultMapper);
}
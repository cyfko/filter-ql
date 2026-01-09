package io.github.cyfko.filterql.jpa.mappings;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Functional interface for resolving JPA predicates from virtual fields.
 * <p>
 * This interface receives all necessary components for building a JPA predicate:
 * the entity root, criteria query, criteria builder, and the filter parameter value.
 * It enables the creation of sophisticated filter logic that goes beyond simple
 * property path mapping.
 * </p>
 * 
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Complex business rules involving multiple entity properties</li>
 *   <li>Custom search algorithms (full-text search, fuzzy matching, etc.)</li>
 *   <li>Cross-entity filtering with joins and subqueries</li>
 *   <li>Calculated fields and aggregations</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Keep mappings stateless and thread-safe</li>
 *   <li>Validate input parameter and provide meaningful error messages</li>
 *   <li>Consider performance implications of complex queries</li>
 *   <li>Use appropriate indexes for custom filter paths</li>
 *   <li>Document business logic and expected parameter types</li>
 * </ul>
 *
 * @param <E> the entity type this mapping applies to
 * @author FilterQL Team
 * @since 2.0.0
 */
@FunctionalInterface
public interface PredicateResolverMapping<E> extends ReferenceMapping<E> {

    /**
     * Resolves a JPA predicate using the provided components and filter parameter.
     * <p>
     * This method is called during query execution to build the actual JPA predicate.
     * The parameter value has already been typed and converted by TypeConversionUtils.
     * </p>
     *
     * @param root the JPA root entity
     * @param query the criteria query being constructed
     * @param cb the criteria builder for creating predicates
     * @param params the filter parameter values
     * @return the resolved JPA predicate
     * @throws IllegalArgumentException if the parameter is invalid
     * @since 2.0.0
     */
    Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, Object[] params);
}


package io.github.cyfko.filterql.spring.security;

import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.api.PropertyReference;

import java.util.List;

/**
 * Contract for providing security-related filters to be automatically injected into FilterQL queries.
 * <p>
 * Implementations supply a list of type-safe {@link FilterDefinition} instances that are always
 * combined (AND) with user-supplied filters, ensuring that security constraints are enforced
 * at the query level.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Spring Security integration: restrict data access based on user roles, permissions, or context</li>
 *   <li>Multi-tenant filtering: enforce tenant isolation in queries</li>
 *   <li>Business rule enforcement: inject mandatory filters (e.g., only active entities)</li>
 * </ul>
 *
 * <h2>Type Parameters</h2>
 * <ul>
 *   <li>{@code P} - Enum type representing property references, must implement {@link PropertyReference}</li>
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 *   <li>Functional interface: can be implemented as a lambda or method reference</li>
 *   <li>Should return an immutable list for thread safety</li>
 *   <li>Returning an empty list means no security filters are applied</li>
 * </ul>
 *
 * @param <P> Enum type for property references (must implement {@link PropertyReference})
 * @author cyfko
 * @since 1.0
 */
@FunctionalInterface
public interface SecurityFilterProvider<P extends Enum<P> & PropertyReference> {

    /**
     * Provides a list of security filters to be injected automatically into FilterQL queries.
     * <p>
     * All returned filters are combined with user filters using logical AND.
     * </p>
     *
     * @return list of security {@link FilterDefinition} instances, or an empty list if none
     */
    List<FilterDefinition<P>> provideSecurityFilters();
}


package io.github.cyfko.filterql.core.spi;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.function.Supplier;

/**
 * Functional interface for resolving filter conditions into JPA Criteria API predicates.
 * <p>
 * {@code PredicateResolver} represents a <strong>deferred predicate generator</strong>:
 * it encapsulates the logic to create JPA {@link Predicate} instances on-demand,
 * when provided with the appropriate query context (root, query, criteria builder).
 * This deferred approach enables structural caching and lazy evaluation.
 * </p>
 *
 * <h2>Architectural Note</h2>
 * <p>
 * ⚠️ <strong>Note:</strong> This interface resides in the core SPI package but uses JPA types
 * ({@code jakarta.persistence.*}). While this creates a coupling to JPA, it reflects the
 * current architectural design where JPA Criteria API serves as the primary query abstraction.
 * </p>
 * <p>
 * For non-JPA backends, adapter implementations must bridge from this JPA-centric interface
 * to their specific query technologies. See {@code ADR-003} for future architectural
 * improvements planned for v5.0.
 * </p>
 *
 * <h2>Core Characteristics</h2>
 * <ul>
 *   <li><strong>Deferred Execution:</strong> Predicates generated on-demand, not eagerly created</li>
 *   <li><strong>Type Safety:</strong> Generic entity type {@code <E>} ensures compile-time validation</li>
 *   <li><strong>Stateless:</strong> Should be immutable and thread-safe (no mutable state)</li>
 *   <li><strong>Composability:</strong> Can be combined via {@code CriteriaBuilder.and()}, {@code .or()}</li>
 *   <li><strong>Reusability:</strong> Same resolver instance can be used across multiple queries</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 *
 * <p><em>Basic Usage (in JPA adapter):</em></p>
 * <pre>{@code
 * // Create a simple predicate resolver
 * PredicateResolver<User> ageFilter = (root, query, cb) ->
 *     cb.greaterThan(root.get("age"), 25);
 *
 * // Use in criteria query
 * CriteriaBuilder cb = entityManager.getCriteriaBuilder();
 * CriteriaQuery<User> query = cb.createQuery(User.class);
 * Root<User> root = query.from(User.class);
 *
 * query.where(ageFilter.resolve(root, query, cb));
 * List<User> results = entityManager.createQuery(query).getResultList();
 * }</pre>
 *
 * <p><em>Complex Composition:</em></p>
 * <pre>{@code
 * // Combine multiple resolvers
 * PredicateResolver<User> nameFilter = (root, query, cb) ->
 *     cb.like(root.get("name"), "John%");
 * PredicateResolver<User> activeFilter = (root, query, cb) ->
 *     cb.equal(root.get("active"), true);
 *
 * // Compose into complex condition
 * PredicateResolver<User> combinedFilter = (root, query, cb) -> cb.and(
 *     nameFilter.resolve(root, query, cb),
 *     activeFilter.resolve(root, query, cb)
 * );
 * }</pre>
 *
 * <p><em>Integration with FilterQL:</em></p>
 * <pre>{@code
 * // FilterQL generates PredicateResolvers automatically via adapter implementations
 * FilterResolver resolver = FilterResolver.of(context);
 * FilterRequest<UserPropertyRef> request = FilterRequest.builder()
 *     .filter("nameFilter", new FilterDefinition<>(UserPropertyRef.NAME, Op.MATCHES, "John%"))
 *     .filter("ageFilter", new FilterDefinition<>(UserPropertyRef.AGE, Op.GT, 25))
 *     .combineWith("nameFilter & ageFilter")
 *     .build();
 *
 * // Get executable predicate resolver (adapter-specific implementation)
 * PredicateResolver<User> predicateResolver = resolver.resolve(User.class, request);
 *
 * // Execute query
 * query.where(predicateResolver.resolve(root, query, cb));
 * }</pre>
 *
 * <p><strong>Performance Considerations:</strong></p>
 * <ul>
 *   <li>Lightweight - no heavyweight object creation until resolution</li>
 *   <li>Cacheable - can be stored and reused across multiple queries</li>
 *   <li>Lazy evaluation - predicates built only when needed</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>PredicateResolver implementations should be stateless and thread-safe.
 * Multiple threads can safely call the resolve method concurrently.</p>
 *
 * @param <E> the entity type this predicate resolver applies to
 * @author Frank KOSSI
 * @since 2.0.0
 */
@FunctionalInterface
public interface PredicateResolver<E> {

    /**
     * Resolves this condition into a query predicate.
     * <p>
     * This method is called during query execution to convert the logical
     * condition into a concrete predicate that can be used in query construction.
     * </p>
     *
     * <p><strong>Implementation Guidelines:</strong></p>
     * <ul>
     *   <li>Method should be stateless and thread-safe</li>
     *   <li>Use the provided builder parameter for predicate construction</li>
     *   <li>Access entity properties through the root parameter</li>
     *   <li>Handle null values and edge cases gracefully</li>
     * </ul>
     *
     * <p><strong>Example Implementation (in adapter module):</strong></p>
     * <pre>{@code
     * PredicateResolver<User> resolver = (root, query, cb) -> {
     *     // Simple equality check
     *     return cb.equal(root.get("status"), UserStatus.ACTIVE);
     * };
     *
     * // Or more complex logic
     * PredicateResolver<User> complexResolver = (root, query, cb) -> {
     *     Predicate nameCondition = cb.like(root.get("name"), "John%");
     *     Predicate ageCondition = cb.greaterThan(root.get("age"), 18);
     *     return cb.and(nameCondition, ageCondition);
     * };
     * }</pre>
     *
     * @param root The root entity in the criteria query, providing access to entity attributes
     * @param query The criteria query being constructed, useful for subqueries and query metadata
     * @param cb The criteria builder for creating predicates and expressions
     * @return A predicate representing this condition, ready for use in query construction
     * @throws IllegalArgumentException if the resolver cannot create a predicate with the given parameters
     */
    Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb);
}


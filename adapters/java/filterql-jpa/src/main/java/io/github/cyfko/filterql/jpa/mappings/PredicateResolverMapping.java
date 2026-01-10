package io.github.cyfko.filterql.jpa.mappings;

import io.github.cyfko.filterql.core.spi.PredicateResolver;

/**
 * Functional interface for resolving JPA predicates from virtual fields.
 * <p>
 * This interface receives the operator code and arguments, and returns a
 * {@link PredicateResolver} for deferred predicate generation. This enables
 * sophisticated filter logic that goes beyond simple property path mapping.
 * </p>
 * 
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Complex business rules involving multiple entity properties</li>
 *   <li>Custom search algorithms (full-text search, fuzzy matching, etc.)</li>
 *   <li>Cross-entity filtering with joins and subqueries</li>
 *   <li>Calculated fields and aggregations</li>
 *   <li>Custom operators (SOUNDEX, GEO_WITHIN, etc.)</li>
 * </ul>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Keep mappings stateless and thread-safe</li>
 *   <li>Validate input parameters inside the returned PredicateResolver</li>
 *   <li>Provide meaningful error messages</li>
 *   <li>Consider performance implications of complex queries</li>
 *   <li>Use appropriate indexes for custom filter paths</li>
 *   <li>Document business logic and expected parameter types</li>
 * </ul>
 *
 * <h2>Example: SOUNDEX operator</h2>
 * <pre>{@code
 * // Helper method for SOUNDEX on any field
 * static PredicateResolverMapping<User> soundexMapping(String fieldName) {
 *     return (op, args) -> (root, query, cb) -> {
 *         if (!"SOUNDEX".equals(op)) {
 *             throw new IllegalArgumentException("Expected SOUNDEX operator");
 *         }
 *         String searchValue = (String) args[0];
 *         return cb.equal(
 *             cb.function("SOUNDEX", String.class, root.get(fieldName)),
 *             cb.function("SOUNDEX", String.class, cb.literal(searchValue))
 *         );
 *     };
 * }
 * 
 * // Usage in JpaFilterContext
 * (ref, op) -> switch (ref) {
 *     case NAME -> "name";  // Simple path
 *     case LAST_NAME -> soundexMapping("lastName");  // Custom operator
 * }
 * }</pre>
 *
 * @param <E> the entity type this mapping applies to
 * @author Frank KOSSI
 * @since 4.0.0
 */
@FunctionalInterface
public interface PredicateResolverMapping<E> extends ReferenceMapping<E> {

    /**
     * Resolves a {@link PredicateResolver} given the operator code and arguments.
     * <p>
     * This method is called during query construction to obtain a deferred predicate
     * generator. The actual predicate is built later when the PredicateResolver's
     * {@code resolve()} method is called with the JPA context.
     * </p>
     * 
     * <p><strong>Guarantees:</strong></p>
     * <ul>
     *     <li>{@code op} is never null nor blank.</li>
     *     <li>{@code args} is never null.</li>
     *     <li>{@code args} contains the single element if the initial operator's argument was a scalar value.</li>
     *     <li>{@code args} contains all elements if the initial operator's argument was a collection.</li>
     * </ul>
     *
     * @param op the filter operator to apply (e.g., "EQ", "LIKE", "SOUNDEX")
     * @param args the arguments of the filter's operator
     * @return the PredicateResolver for deferred predicate generation
     * @throws IllegalArgumentException if the operator or arguments are invalid
     * @since 4.0.0
     */
    PredicateResolver<E> map(String op, Object[] args);
}


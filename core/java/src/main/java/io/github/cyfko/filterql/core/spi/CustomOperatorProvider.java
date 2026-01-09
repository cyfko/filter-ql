package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.PropertyReference;

import java.util.Set;

/**
 * Contract for providing implementations of custom filter operators.
 * <p>
 * Implementations indicate which operator codes they support and provide
 * methods to resolve {@link FilterDefinition} instances into executable
 * {@link PredicateResolver} instances for query construction.
 * </p>
 * <p>
 * <strong>Validation Strategy:</strong> Implementations should perform value validation
 * inside the returned {@link PredicateResolver}, at query execution time. This allows
 * for clear error messages with full context and avoids premature validation.
 * </p>
 *
 * <h3>Example Implementation:</h3>
 * <pre>{@code
 * public class StartsWithOperatorProvider implements CustomOperatorProvider {
 *     @Override
 *     public Set<String> supportedOperators() {
 *         return Set.of("STARTS_WITH");
 *     }
 *
 *     @Override
 *     public <P extends Enum<P> & PropertyReference> 
 *     PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
 *         return (root, query, cb) -> {
 *             // Value validation at execution time
 *             if (!(definition.value() instanceof String prefix) || prefix.isBlank()) {
 *                 throw new IllegalArgumentException("STARTS_WITH requires non-blank String value");
 *             }
 *             
 *             String fieldName = definition.ref().name().toLowerCase();
 *             return cb.like(root.get(fieldName), prefix + "%");
 *         };
 *     }
 * }
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public interface CustomOperatorProvider {

    /**
     * Returns the set of operator codes supported by this provider.
     * <p>
     * Each code must be unique across all registered providers and should use
     * UPPER_SNAKE_CASE convention for consistency.
     * </p>
     *
     * @return a non-null, non-empty set of operator code strings
     */
    Set<String> supportedOperators();

    /**
     * Resolves a {@link FilterDefinition} into a {@link PredicateResolver} suitable for query construction.
     * <p>
     * <strong>Implementation Guidelines:</strong>
     * </p>
     * <ul>
     *   <li>Validate the value inside the returned {@link PredicateResolver} when building the predicate</li>
     *   <li>Throw {@link IllegalArgumentException} with clear messages for invalid values</li>
     *   <li>Map property reference to entity field path (handle camelCase, nested paths)</li>
     *   <li>Build the appropriate query predicate using the provided CriteriaBuilder</li>
     * </ul>
     *
     * <h4>Example with Value Validation:</h4>
     * <pre>{@code
     * public <P extends Enum<P> & PropertyReference>
     * PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
     *     return (root, query, cb) -> {
     *         Object value = definition.value();
     *         
     *         // Validate value at execution
     *         if (!(value instanceof List<?> list) || list.size() != 2) {
     *             throw new IllegalArgumentException(
     *                 "BETWEEN operator requires List with exactly 2 elements");
     *         }
     *         
     *         String fieldName = definition.ref().name().toLowerCase();
     *         return cb.between(root.get(fieldName), 
     *             (Comparable) list.get(0), 
     *             (Comparable) list.get(1));
     *     };
     * }
     * }</pre>
     *
     * @param <P> the enum type of the property reference, extending {@link PropertyReference}
     * @param definition the filter definition containing filtering criteria (ref, operator, value)
     * @return a {@link PredicateResolver} capable of producing the corresponding query predicate
     */
    <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition);
}

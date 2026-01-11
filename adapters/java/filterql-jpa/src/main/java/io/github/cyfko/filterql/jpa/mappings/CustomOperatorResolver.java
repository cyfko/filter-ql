package io.github.cyfko.filterql.jpa.mappings;

import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.validation.PropertyReference;

/**
 * Strategy for resolving filter operations into JPA predicates.
 * <p>
 * This functional interface provides a centralized way to handle custom operators
 * or override default operator behavior for specific properties. It is called
 * <strong>before</strong> the default resolution mechanism (path-based or 
 * {@link PredicateResolverMapping}).
 * </p>
 *
 * <h2>Resolution Flow</h2>
 * <pre>
 * toCondition(ref, op, args)
 *         │
 *         ▼
 * ┌───────────────────────────────┐
 * │ customOperatorResolver.resolve│
 * │     returns non-null?         │
 * └───────────────────────────────┘
 *       │                    │
 *      YES                  NO (null)
 *       │                    │
 *       ▼                    ▼
 * ┌──────────────┐   ┌──────────────────────┐
 * │ Use returned │   │ Default mechanism    │
 * │   resolver   │   │ (path / Mapping)     │
 * └──────────────┘   └──────────────────────┘
 * </pre>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li><strong>Custom operators:</strong> Implement operators like SOUNDEX, LEVENSHTEIN,
 *       GEO_WITHIN, FULL_TEXT that work across multiple properties</li>
 *   <li><strong>Override behavior:</strong> Customize how standard operators (EQ, MATCHES, etc.)
 *       work for specific properties</li>
 *   <li><strong>Cross-cutting concerns:</strong> Apply tenant filtering, soft-delete handling,
 *       or audit restrictions transparently</li>
 *   <li><strong>Dynamic behavior:</strong> Change operator semantics based on runtime context
 *       (user role, feature flags, etc.)</li>
 * </ul>
 *
 * <h2>Example: Custom SOUNDEX Operator</h2>
 * <pre>{@code
 * CustomOperatorResolver<UserProperty> soundexResolver = (ref, op, args) -> {
 *     if (!"SOUNDEX".equals(op)) {
 *         return null;  // Delegate to default handling
 *     }
 *     
 *     // Map property reference to field path
 *     String fieldPath = switch (ref) {
 *         case FIRST_NAME -> "firstName";
 *         case LAST_NAME -> "lastName";
 *         default -> throw new IllegalArgumentException(
 *             "SOUNDEX not supported for " + ref);
 *     };
 *     
 *     return (root, query, cb) -> cb.equal(
 *         cb.function("SOUNDEX", String.class, root.get(fieldPath)),
 *         cb.function("SOUNDEX", String.class, cb.literal((String) args[0]))
 *     );
 * };
 * 
 * JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
 *         UserProperty.class, 
 *         mappingBuilder
 *     ).withCustomOperatorResolver(soundexResolver);
 * }</pre>
 *
 * <h2>Example: Override MATCHES for Email</h2>
 * <pre>{@code
 * CustomOperatorResolver<UserProperty> emailOverride = (ref, op, args) -> {
 *     // Override MATCHES for EMAIL to be case-insensitive and domain-aware
 *     if (ref == UserProperty.EMAIL && "MATCHES".equals(op)) {
 *         return (root, query, cb) -> {
 *             String pattern = ((String) args[0]).toLowerCase();
 *             return cb.like(cb.lower(root.get("email")), pattern);
 *         };
 *     }
 *     return null;  // Use default for everything else
 * };
 * }</pre>
 *
 * <h2>Example: Combining Multiple Custom Operators</h2>
 * <pre>{@code
 * CustomOperatorResolver<ProductProperty> multiResolver = (ref, op, args) -> {
 *     return switch (op) {
 *         case "SOUNDEX" -> handleSoundex(ref, args);
 *         case "GEO_WITHIN" -> handleGeoWithin(ref, args);
 *         case "FULL_TEXT" -> handleFullText(ref, args);
 *         default -> null;  // Standard operators use default handling
 *     };
 * };
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Implementations should be stateless or use thread-safe constructs if they
 * access shared state. The resolver may be called concurrently from multiple threads.
 * </p>
 *
 * @param <P> the property reference enum type (must implement {@link PropertyReference})
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see PredicateResolverMapping
 * @see io.github.cyfko.filterql.jpa.JpaFilterContext#withCustomOperatorResolver
 */
@FunctionalInterface
public interface CustomOperatorResolver<P extends Enum<P> & PropertyReference> {

    /**
     * Resolves a filter operation into a {@link PredicateResolver}.
     * <p>
     * This method is called for every filter operation before the default
     * resolution mechanism. Return a {@link PredicateResolver} to handle the
     * operation, or {@code null} to delegate to the default path-based or
     * {@link PredicateResolverMapping} handling.
     * </p>
     *
     * <p><strong>Contract:</strong></p>
     * <ul>
     *   <li>{@code ref} is never {@code null}</li>
     *   <li>{@code op} is never {@code null} nor blank</li>
     *   <li>{@code args} is never {@code null} (but may be empty)</li>
     *   <li>Returning {@code null} delegates to default handling</li>
     *   <li>Returning a non-null resolver bypasses default handling entirely</li>
     * </ul>
     *
     * @param ref  the property reference being filtered
     * @param op   the operator code (standard like "EQ", "MATCHES" or custom like "SOUNDEX")
     * @param args the filter arguments (converted from the filter value)
     * @return a {@link PredicateResolver} to handle this operation, 
     *         or {@code null} to use default handling
     * @throws IllegalArgumentException if the operation cannot be handled and 
     *         should not be delegated (e.g., unsupported property for this operator)
     */
    PredicateResolver<?> resolve(P ref, String op, Object[] args);
}

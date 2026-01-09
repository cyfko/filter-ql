package io.github.cyfko.filterql.core.api;

/**
 * Framework-agnostic interface representing a composable filter condition.
 * <p>
 * This interface provides a fluent API for combining filter conditions using boolean logic
 * operators (AND, OR, NOT). It follows the <strong>Composite pattern</strong>, allowing
 * conditions to be nested and combined to form arbitrarily complex boolean expressions.
 * </p>
 *
 * <h2>Core Concepts</h2>
 * <ul>
 *   <li><strong>Immutability:</strong> All operations return new {@code Condition} instances,
 *       leaving the original unchanged</li>
 *   <li><strong>Composability:</strong> Conditions can be combined freely using {@code and()},
 *       {@code or()}, and {@code not()}</li>
 *   <li><strong>Backend-Agnostic:</strong> The interface makes no assumptions about the underlying
 *       query technology (database, search engine, API, etc.)</li>
 *   <li><strong>Type-Safety:</strong> Compile-time validation through PropertyReference enums</li>
 * </ul>
 *
 * <h2>Boolean Algebra</h2>
 * <p>
 * Conditions support standard boolean operations with conventional semantics:
 * </p>
 * <ul>
 *   <li><strong>AND:</strong> Both conditions must be satisfied</li>
 *   <li><strong>OR:</strong> At least one condition must be satisfied</li>
 *   <li><strong>NOT:</strong> The condition must not be satisfied</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create individual conditions (structure only, values provided later)
 * Condition nameCondition = context.toCondition("nameKey", UserProperty.NAME, "EQ");
 * Condition ageCondition = context.toCondition("ageKey", UserProperty.AGE, "GT");
 * Condition statusCondition = context.toCondition("statusKey", UserProperty.STATUS, "EQ");
 *
 * // Combine conditions using boolean operators
 * // Represents: name = X AND (age > Y OR status = Z)
 * Condition complexCondition = nameCondition
 *     .and(ageCondition.or(statusCondition));
 *
 * // Negation
 * Condition notDeleted = deletedCondition.not();  // NOT(deleted = true)
 *
 * // Complex nesting
 * // Represents: (name = X AND age > Y) AND NOT(deleted = true)
 * Condition result = nameCondition.and(ageCondition).and(notDeleted);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Implementations must be <strong>immutable</strong> and therefore inherently thread-safe.
 * Multiple threads can safely share and compose the same {@code Condition} instances.
 * </p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Each operation ({@code and()}, {@code or()}, {@code not()}) must return a new instance</li>
 *   <li>Original {@code Condition} instances must never be modified</li>
 *   <li>Implementations should defer actual execution until a backend-specific resolver is invoked</li>
 *   <li>Conditions should be lightweight (minimal state, no heavy objects)</li>
 * </ul>
 *
 * @see FilterContext
 * @see FilterTree
 * @author Frank KOSSI
 * @since 1.0
 */
public interface Condition {
    
    /**
     * Creates a new condition representing the logical AND of this condition and another.
     * <p>
     * The resulting condition will be satisfied only when both this condition 
     * and the other condition are satisfied.
     * </p>
     * 
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * Condition nameFilter = context.toCondition("nameKey", UserProperty.NAME, "EQ");
     * Condition ageFilter = context.toCondition("ageKey", UserProperty.AGE, "GT");
     * Condition combined = nameFilter.and(ageFilter);          // name EQ AND age GT
     * }</pre>
     * 
     * @param other The other condition to combine with this one
     * @return A new condition representing (this AND other)
     * @throws IllegalArgumentException if the other condition is incompatible
     */
    Condition and(Condition other);
    
    /**
     * Creates a new condition representing the logical OR of this condition and another.
     * <p>
     * The resulting condition will be satisfied when either this condition 
     * or the other condition (or both) are satisfied.
     * </p>
     * 
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * Condition activeFilter = context.toCondition("activeKey", UserProperty.STATUS, "EQ");
     * Condition premiumFilter = context.toCondition("premiumKey", UserProperty.TYPE, "eq");  // case-insensitive
     * Condition combined = activeFilter.or(premiumFilter);         // status EQ OR type EQ
     * }</pre>
     * 
     * @param other The other condition to combine with this one
     * @return A new condition representing (this OR other)
     * @throws IllegalArgumentException if the other condition is incompatible
     */
    Condition or(Condition other);
    
    /**
     * Creates a new condition representing the logical negation of this condition.
     * <p>
     * The resulting condition will be satisfied only when this condition is NOT satisfied.
     * </p>
     * 
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * Condition deletedFilter = context.toCondition("deletedKey", UserProperty.DELETED, "EQ");
     * Condition notDeleted = deletedFilter.not();                 // NOT(deleted EQ)
     * }</pre>
     * 
     * @return A new condition representing NOT(this)
     */
    Condition not();
}

package io.github.cyfko.filterql.core.api;

import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;

/**
 * Framework-agnostic interface for bridging filter definitions to backend-specific query execution.
 * <p>
 * {@code FilterContext} serves as the central abstraction that transforms high-level filter
 * definitions (property references, operators, filter keys) into executable {@link Condition}
 * and {@link PredicateResolver} instances. It decouples the core filtering logic from
 * backend-specific query technologies (databases, search engines, APIs, etc.).
 * </p>
 *
 * <h2>Core Responsibilities</h2>
 * <ul>
 *   <li><strong>Condition Generation:</strong> Transform filter metadata into composable {@link Condition} instances</li>
 *   <li><strong>Predicate Resolution:</strong> Convert logical conditions into backend-specific query predicates</li>
 *   <li><strong>Deferred Value Binding:</strong> Support structural caching by separating filter structure from values</li>
 *   <li><strong>Type Validation:</strong> Ensure type compatibility between properties, operators, and values</li>
 * </ul>
 *
 * <h2>Deferred Argument Architecture</h2>
 * <p>
 * This interface supports a <strong>deferred argument pattern</strong> where filter values are
 * separated from filter structure:
 * </p>
 * <ol>
 *   <li><strong>Phase 1 ({@link #toCondition}):</strong> Create {@link Condition} structure WITHOUT values
 *       using only property references, operators, and argument keys</li>
 *   <li><strong>Phase 2 ({@link #toResolver}):</strong> Bind actual values via argument registry
 *       and convert to executable {@link PredicateResolver}</li>
 * </ol>
 * <p>
 * This separation enables <strong>structural caching</strong>: conditions with the same structure
 * but different values can share cached representations, improving performance.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Implementations must be thread-safe for concurrent filter resolution. Condition creation
 * and predicate resolution may occur concurrently across multiple threads.
 * </p>
 *
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Validate property references and operator compatibility in {@link #toCondition}</li>
 *   <li>Make NO assumptions about the semantics of filter keys (they are opaque identifiers)</li>
 *   <li>Ensure {@link Condition} instances are immutable once created</li>
 *   <li>Maintain type safety throughout the conversion pipeline</li>
 *   <li>Support both standard and custom operators</li>
 *   <li>Defer actual query predicate construction until {@link #toResolver} is called</li>
 * </ul>
 *
 * <h2>Backend Adapters</h2>
 * <p>
 * Concrete implementations adapt FilterQL to specific query technologies.
 * Each adapter translates {@link Condition} instances into backend-specific predicates.
 * </p>
 *
 * @see FilterDefinition
 * @see Condition
 * @see PredicateResolver
 * @see PropertyReference
 *
 * @author Frank KOSSI
 * @since 4.0
 */
public interface FilterContext {

    /**
     * Transforms the input {@link FilterDefinition} into a {@link Condition} that can be subsequently composed.
     * <p>
     * This method transforms a filter definition consisting of a property reference and an operator
     * into a concrete condition that can be composed with other {@link Condition}s.
     * The filter argument value is <strong>not</strong> provided here—it will be retrieved later
     * from the argument registry when {@link #toResolver(Condition, QueryExecutionParams)} is called.
     * This deferred approach enables structural caching where conditions with the same structure
     * but different values can share cached representations.
     * </p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * // Create a condition from filter metadata (without the value)
     * // The value will be retrieved later from the argument registry using "emailArg" key
     * Condition condition = context.toCondition("emailArg", UserPropertyRef.EMAIL, "MATCHES");
     * // Or using standard operators:
     * Condition condition2 = context.toCondition("statusArg", UserPropertyRef.STATUS, "EQ");
     * // Custom operators are also supported (case-insensitive):
     * Condition condition3 = context.toCondition("scoreArg", UserPropertyRef.SCORE, "custom_op");
     * }</pre>
     *
     * @param argKey the key used to retrieve the filter argument's value from the argument registry
     *               when {@link #toResolver(Condition, QueryExecutionParams)} is invoked
     * @param ref the reference to the property of interest
     * @param op the operator applied to the property (case-insensitive).
     *           Can be standard operators ("EQ", "NE", "GT", "LT", "GE", "LE", "LIKE", "IN", etc.)
     *           or custom operators defined by the implementation
     * @param <P> the enum type extending {@link PropertyReference}
     * @return the created condition for immediate use
     * @throws FilterDefinitionException if the filter definition cannot be translated to a {@link Condition}
     * @throws NullPointerException if any argument is null
     */
    <P extends Enum<P> & PropertyReference> Condition toCondition(String argKey, P ref, String op) throws FilterDefinitionException;

    /**
     * Converts a logical {@link Condition} tree into an executable {@link PredicateResolver}
     * with runtime execution parameters.
     * <p>
     * This method completes the filter resolution pipeline by:
     * </p>
     * <ol>
     *   <li>Binding argument keys to their concrete values via {@link QueryExecutionParams#arguments()}</li>
     *   <li>Applying optional projection via {@link QueryExecutionParams#projection()}</li>
     *   <li>Transforming the abstract condition tree into a backend-specific predicate resolver</li>
     *   <li>Returning an executor that can generate query predicates on-demand</li>
     * </ol>
     * <p>
     * The returned {@link PredicateResolver} encapsulates backend-specific query generation logic
     * and can be invoked multiple times with different query contexts.
     * </p>
     *
     * <h3>Usage Pattern - Full Entity (No Projection)</h3>
     * <pre>{@code
     * // Phase 1: Create conditions (structure only, values deferred)
     * Condition nameCondition = context.toCondition("nameArg", UserPropertyRef.NAME, "MATCHES");
     * Condition ageCondition = context.toCondition("ageArg", UserPropertyRef.AGE, "GT");
     * Condition combined = nameCondition.and(ageCondition);
     *
     * // Phase 2: Prepare execution params (no projection)
     * QueryExecutionParams params = QueryExecutionParams.of(Map.of(
     *     "nameArg", "%john%",
     *     "ageArg", 25
     * ));
     *
     * // Phase 3: Resolve to entity query
     * PredicateResolver<User> resolver = context.toResolver(combined, params);
     *
     * // Phase 4: Execute with backend-specific context (implementation-defined)
     * }</pre>
     *
     * <h3>Usage Pattern - With Projection (Tuple Query)</h3>
     * <pre>{@code
     * // Phase 1: Create conditions (same as above)
     * Condition condition = ...;
     *
     * // Phase 2: Prepare execution params WITH projection
     * QueryExecutionParams params = QueryExecutionParams.withProjection(
     *     Map.of("activeArg", true),
     *     Set.of("name", "email", "address.city.name")  // DTO fields
     * );
     *
     * // Phase 3: Resolve to tuple query (mean that query result MUST ONLY return projected parts)
     * PredicateResolver<User> resolver = context.toResolver(condition, params);
     *
     * // Phase 4: Execute and get tuples (partial data)
     * }</pre>
     *
     * <h3>Execution Parameters</h3>
     * <p>
     * {@link QueryExecutionParams} encapsulates:
     * </p>
     * <ul>
     *   <li><strong>arguments:</strong> Map of argument keys (from {@link #toCondition}) to values</li>
     *   <li><strong>projection:</strong> Optional set of DTO field paths (null = full entity)</li>
     * </ul>
     * <p>
     * All argument keys referenced in the condition tree must be present in
     * {@code params.arguments()}, otherwise an exception is thrown.
     * </p>
     *
     * @param condition the logical condition tree to convert
     * @param params runtime execution parameters (arguments + optional projection)
     * @return a {@link PredicateResolver} capable of generating backend-specific predicates
     * @throws IllegalArgumentException if {@code condition} is incompatible with this context
     * @throws UnsupportedOperationException if the condition cannot be converted to the target backend
     * @throws NullPointerException if any argument is null
     * @throws FilterDefinitionException if transformation fails due to invalid filter definitions
     * @throws IllegalStateException if an argument key in the condition is missing from the parameter registry,
     *                               or if projection is requested but not supported by this implementation
     * @since 5.0.0
     */
    PredicateResolver<?> toResolver(Condition condition, QueryExecutionParams params) throws FilterDefinitionException;
}

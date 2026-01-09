package io.github.cyfko.filterql.core.model;

import io.github.cyfko.filterql.core.config.ProjectionPolicy;

import java.util.*;

/**
 * Encapsulates runtime parameters for query execution, including filter arguments,
 * optional projection fields, and pagination/sorting options.
 * <p>
 * This class provides a unified container for all execution-time parameters,
 * enabling clean extensibility for future features (query hints, etc.).
 * </p>
 *
 * <h2>Design Principle</h2>
 * <p>
 * Separates <strong>query structure</strong> (Condition, DSL) from <strong>query execution</strong>
 * (values, projection, pagination). This enables:
 * </p>
 * <ul>
 *   <li>✅ <strong>Structural caching</strong> - Cache Condition (structure), vary parameters (values)</li>
 *   <li>✅ <strong>Reusable queries</strong> - Same Condition with different values/projection/pagination</li>
 *   <li>✅ <strong>Extensibility</strong> - Add new params without breaking existing API</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Arguments Only (No Projection)</h3>
 * <pre>{@code
 * // Full entity query - returns all fields
 * QueryExecutionParams params = QueryExecutionParams.builder()
 *     .arguments(Map.of("name", "John", "age", 25))
 *     .build();
 *
 * PredicateResolver<User> resolver = context.toResolver(User.class, condition, params);
 * }</pre>
 *
 * <h3>Example 2: Arguments + Projection</h3>
 * <pre>{@code
 * // Tuple query with specific field projection
 * QueryExecutionParams params = QueryExecutionParams.builder()
 *     .arguments(Map.of("active", true))
 *     .projection(Set.of("id", "name", "email"))
 *     .projectionPolicy(ProjectionPolicy.INCLUDE)
 *     .build();
 *
 * PredicateResolver<Tuple> resolver = context.toResolver(Tuple.class, condition, params);
 * }</pre>
 *
 * <h3>Example 3: Full Configuration with Pagination</h3>
 * <pre>{@code
 * // Complete query with all features
 * QueryExecutionParams params = QueryExecutionParams.builder()
 *     .arguments(Map.of("status", "ACTIVE", "minAge", 18))
 *     .projection(Set.of("id", "name", "status"))
 *     .pagination(new Pagination(0, 20, Sort.by("name")))
 *     .projectionPolicy(ProjectionPolicy.INCLUDE)
 *     .build();
 * }</pre>
 *
 * <h3>Example 4: Simple Factory Method</h3>
 * <pre>{@code
 * // Quick construction for simple cases
 * QueryExecutionParams params = QueryExecutionParams.of(
 *     Map.of("category", "electronics")
 * );
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <strong>immutable</strong> and <strong>thread-safe</strong>.
 * All collections are defensively copied and wrapped in unmodifiable views.
 * </p>
 *
 * <h2>Null Handling</h2>
 * <ul>
 *   <li><strong>arguments</strong>: Must not be null (use {@code Map.of()} for no arguments)</li>
 *   <li><strong>projection</strong>: May be null (indicates all fields should be returned)</li>
 *   <li><strong>pagination</strong>: May be null (indicates no pagination)</li>
 *   <li><strong>projectionPolicy</strong>: May be null (default policy will be applied)</li>
 * </ul>
 *
 * @param arguments filter argument values (key = filter ID from FilterRequest, value = filter value).
 *                  Must not be null. Use {@code Map.of()} for queries without arguments.
 * @param projection set of field names to include/exclude in query results.
 *                   {@code null} or empty indicates all fields should be returned.
 * @param pagination pagination and sorting configuration.
 *                   {@code null} indicates no pagination (return all results).
 * @param projectionPolicy policy determining how projection fields are interpreted (INCLUDE/EXCLUDE).
 *                         {@code null} uses system default policy.
 */
public record QueryExecutionParams(
        Map<String, Object> arguments,
        Set<String> projection,
        Pagination pagination,
        ProjectionPolicy projectionPolicy
) {
    /**
     * Canonical constructor with defensive copying and immutability enforcement.
     * <p>
     * This constructor ensures that all collections are immutable to maintain thread-safety
     * and prevent external modification after construction.
     * </p>
     *
     * @throws NullPointerException if arguments is null (use {@code Map.of()} for no arguments)
     */
    public QueryExecutionParams {
        // Validate and defensively copy arguments
        Objects.requireNonNull(arguments, "arguments cannot be null (use Map.of() for empty)");
        arguments = Collections.unmodifiableMap(new HashMap<>(arguments));

        // Defensively copy projection if present
        if (projection != null && !projection.isEmpty()) {
            projection = Collections.unmodifiableSet(new HashSet<>(projection));
        }
    }

    /**
     * Determines if specific field projection is requested.
     * <p>
     * When this returns {@code false}, the query should return all available fields.
     * When {@code true}, only fields specified in {@link #projection()} should be returned.
     * </p>
     *
     * @return {@code true} if projection is specified (specific fields requested),
     *         {@code false} if projection is null or empty (all fields requested)
     */
    public boolean hasProjection() {
        return projection != null && !projection.isEmpty();
    }

    /**
     * Checks if pagination is configured for this query.
     * <p>
     * When this returns {@code false}, the query should return all matching results
     * without pagination or sorting constraints.
     * </p>
     *
     * @return {@code true} if pagination is configured, {@code false} otherwise
     */
    public boolean hasPagination() {
        return pagination != null;
    }

    /**
     * Checks if a projection policy is explicitly configured.
     * <p>
     * When this returns {@code false}, the system should apply its default projection policy.
     * </p>
     *
     * @return {@code true} if projectionPolicy is configured, {@code false} otherwise
     */
    public boolean hasProjectionPolicy() {
        return projectionPolicy != null;
    }

    /**
     * Creates a simple QueryExecutionParams with only filter arguments.
     * <p>
     * This is a convenience factory method for the common case where only
     * filter arguments are needed, without projection or pagination.
     * </p>
     *
     * @param arguments filter argument values (must not be null)
     * @return new QueryExecutionParams instance with only arguments set
     * @throws NullPointerException if arguments is null
     */
    public static QueryExecutionParams of(Map<String, Object> arguments) {
        return new QueryExecutionParams(arguments, null, null, null);
    }

    /**
     * Creates a QueryExecutionParams with arguments and projection.
     * <p>
     * Convenience factory method for queries that need both filtering and field projection.
     * </p>
     *
     * @param arguments filter argument values (must not be null)
     * @param projection set of field names to project (may be null for all fields)
     * @return new QueryExecutionParams instance
     * @throws NullPointerException if arguments is null
     */
    public static QueryExecutionParams withProjection(
            Map<String, Object> arguments,
            Set<String> projection
    ) {
        return new QueryExecutionParams(arguments, projection, null, null);
    }

    /**
     * Creates a new builder for constructing QueryExecutionParams.
     * <p>
     * Use this for complex configurations requiring multiple optional parameters.
     * </p>
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing QueryExecutionParams instances with a fluent API.
     * <p>
     * This builder provides a readable way to construct QueryExecutionParams with
     * multiple optional parameters. All parameters have sensible defaults:
     * </p>
     * <ul>
     *   <li>arguments: empty map (no filtering)</li>
     *   <li>projection: null (all fields)</li>
     *   <li>pagination: null (no pagination)</li>
     *   <li>projectionPolicy: null (default policy)</li>
     * </ul>
     *
     * <h3>Example Usage:</h3>
     * <pre>{@code
     * QueryExecutionParams params = QueryExecutionParams.builder()
     *     .arguments(Map.of("status", "ACTIVE"))
     *     .projection(Set.of("id", "name"))
     *     .pagination(new Pagination(0, 20))
     *     .projectionPolicy(ProjectionPolicy.INCLUDE)
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private Map<String, Object> arguments = Map.of();
        private Set<String> projection;
        private Pagination pagination;
        private ProjectionPolicy projectionPolicy;

        /**
         * Sets the filter argument values.
         *
         * @param arguments map of filter ID to filter value (must not be null)
         * @return this builder for method chaining
         * @throws NullPointerException if arguments is null
         */
        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = Objects.requireNonNull(arguments, "arguments cannot be null");
            return this;
        }

        /**
         * Sets the projection fields.
         *
         * @param projection set of field names to project (null for all fields)
         * @return this builder for method chaining
         */
        public Builder projection(Set<String> projection) {
            this.projection = projection;
            return this;
        }

        /**
         * Sets the pagination configuration.
         *
         * @param pagination pagination and sorting config (null for no pagination)
         * @return this builder for method chaining
         */
        public Builder pagination(Pagination pagination) {
            this.pagination = pagination;
            return this;
        }

        /**
         * Sets the projection policy.
         *
         * @param projectionPolicy policy for interpreting projection fields (null for default)
         * @return this builder for method chaining
         */
        public Builder projectionPolicy(ProjectionPolicy projectionPolicy) {
            this.projectionPolicy = projectionPolicy;
            return this;
        }

        /**
         * Constructs the QueryExecutionParams instance.
         * <p>
         * All defensive copying and immutability enforcement is handled by the
         * QueryExecutionParams constructor.
         * </p>
         *
         * @return new immutable QueryExecutionParams instance
         * @throws NullPointerException if arguments was set to null
         */
        public QueryExecutionParams build() {
            return new QueryExecutionParams(arguments, projection, pagination, projectionPolicy);
        }
    }
}
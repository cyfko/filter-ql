package io.github.cyfko.filterql.core;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.DslParser;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.api.FilterTree;
import io.github.cyfko.filterql.core.config.ProjectionPolicy;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.impl.BasicDslParser;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.QueryExecutor;
import io.github.cyfko.filterql.core.spi.FilterQuery;
import io.github.cyfko.filterql.core.validation.PropertyReference;

import java.util.*;
import java.util.logging.Logger;


/**
 * High-level facade for resolving filter requests into executable predicates.
 * <p>
 * The FilterQueryFactory provides a streamlined API that combines DSL parsing,
 * context management, and predicate resolution into a single, easy-to-use interface.
 * It orchestrates the entire filtering pipeline from filter request to executable predicate.
 * </p>
 *
 * <p><strong>Architecture Overview:</strong></p>
 * <ol>
 *   <li><strong>Parse:</strong> Transform DSL expression into FilterTree using {@link DslParser}</li>
 *   <li><strong>Populate:</strong> Register filter definitions in {@link FilterContext}</li>
 *   <li><strong>Generate:</strong> Create condition tree from FilterTree and Context</li>
 *   <li><strong>Resolve:</strong> Convert conditions into executable {@link PredicateResolver}</li>
 * </ol>
 *
 * <p><strong>Complete Usage Example:</strong></p>
 * <pre>{@code
 * // 1. Setup context with mapping strategy
 * FilterContext context = // implementation defined context;
 *
 * // 2. Create filter query
 * FilterQuery<User> query = FilterQueryFactory.of(context);
 *
 * // 3. Build filter request
 * Map<String, FilterDefinition<UserPropertyRef>> filters = Map.of(
 *     "nameFilter", new FilterDefinition<>(UserPropertyRef.NAME, Op.LIKE, "John%"),
 *     "emailFilter", new FilterDefinition<>(UserPropertyRef.EMAIL, Op.LIKE, "%@company.com"),
 *     "cityFilter", new FilterDefinition<>(UserPropertyRef.ADDRESS_CITY, Op.EQUALS, "Paris")
 * );
 *
 * FilterRequest<UserPropertyRef> request = new FilterRequest<>(
 *     filters,
 *     "(nameFilter & emailFilter) | cityFilter"  // DSL expression
 * );
 *
 * // 4. Resolve to executable predicate
 * PredicateResolver<User> predicateResolver = query.resolve(request);
 *
 * // 5. Execute query (implementation-specific, depends on adapter used)
 * // For example, with JPA adapter:
 * // CriteriaBuilder cb = entityManager.getCriteriaBuilder();
 * // CriteriaQuery<User> query = cb.createQuery(User.class);
 * // Root<User> root = query.from(User.class);
 * // query.where(predicateResolver.resolve(root, query, cb));
 * // List<User> results = entityManager.createQuery(query).getResultList();
 * }</pre>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>{@link DSLSyntaxException} - Invalid DSL expression syntax</li>
 *   <li>{@link FilterValidationException} - Filter validation failures</li>
 *   <li>{@link IllegalArgumentException} - Invalid filter definitions or mappings</li>
 * </ul>
 *
 * <p><strong>Best Practices:</strong></p>
 * <ul>
 *   <li>Reuse FilterQueryFactory instances when possible (they are thread-safe)</li>
 *   <li>Keep DSL expressions simple and readable</li>
 *   <li>Validate filter definitions early in the request processing</li>
 *   <li>Use meaningful filter keys that correspond to business concepts</li>
 * </ul>
 *
 * @see DslParser
 * @see FilterTree
 * @see PredicateResolver
 * @see FilterContext
 * @see FilterRequest
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class FilterQueryFactory {

    private FilterQueryFactory() {}

    /**
     * Creates a {@link FilterQuery} with the default {@link BasicDslParser} and the given context.
     * <p>
     * This is the most commonly used factory method. It provides the standard DSL parsing
     * capabilities while allowing customization of the context and mapping strategies.
     * </p>
     *
     * @param context The context to use for condition resolution. Must not be null.
     * @return A new FilterQueryFactory instance with default DSL parser
     * @throws NullPointerException if context is null
     */
    public static <E> FilterQuery<E> of(FilterContext context) {
        return new DefaultFilterQuery<>(context,new BasicDslParser(),ProjectionPolicy.defaults());
    }

    /**
     * Creates a {@link  FilterQuery} with the given parser and context.
     * <p>
     * This factory method allows full customization of both the parsing strategy
     * and the context implementation. Use this when you need custom parsing logic
     * or specialized context behavior.
     * </p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * // Custom parser with extended DSL syntax
     * Parser customParser = new ExtendedDSLParser();
     *
     * // Context with specific mapping strategies
     * Context context = // implementation-defined ! ;
     *
     * // Create manager with custom components
     * FilterRequestManager manager = FilterQueryFactory.of(customParser, context);
     * }</pre>
     *
     * @param context The context to use for condition resolution. Must not be null.
     * @param dslParser The parser to use for DSL expressions. Must not be null.
     * @return A new FilterQueryFactory instance configured with the provided components
     * @throws NullPointerException if dslParser or context is null
     */
    public static <E> FilterQuery<E> of(FilterContext context, DslParser dslParser) {
        return new DefaultFilterQuery<>(context,dslParser,ProjectionPolicy.defaults());
    }

    /**
     * Creates a {@link FilterQuery} with the given parser, context, and projection policy.
     *
     * @param context The context to use for condition resolution. Must not be null.
     * @param dslParser The parser to use for DSL expressions. Must not be null.
     * @param policy The projection policy to use for query execution. Must not be null.
     * @param <E> The entity type for which the filter is resolved.
     * @return A new FilterQuery instance with the provided components.
     * @throws NullPointerException if any parameter is null.
     */
    public static <E> FilterQuery<E> of(FilterContext context, DslParser dslParser, ProjectionPolicy policy) {
        return new DefaultFilterQuery<>(context,dslParser,policy);
    }

    /**
     * Creates a {@link FilterQuery} with the default {@link BasicDslParser}, the given context, and projection policy.
     *
     * @param context The context to use for condition resolution. Must not be null.
     * @param policy The projection policy to use for query execution. Must not be null.
     * @param <E> The entity type for which the filter is resolved.
     * @return A new FilterQuery instance with the provided components.
     * @throws NullPointerException if any parameter is null.
     */
    public static <E> FilterQuery<E> of(FilterContext context, ProjectionPolicy policy) {
        return new DefaultFilterQuery<>(context,new BasicDslParser(),policy);
    }

    /**
     * Default implementation of {@link FilterQuery} for resolving filter requests.
     * <p>
     * This record encapsulates the context, DSL parser, and projection policy required
     * to process filter requests and generate executable predicates.
     * </p>
     *
     * @param <E> The entity type for which the filter is resolved.
     * @param filterContext The context for condition resolution.
     * @param dslParser The parser for DSL expressions.
     * @param projectionPolicy The policy for projection handling.
     */
    private record DefaultFilterQuery<E>(FilterContext filterContext, DslParser dslParser, ProjectionPolicy projectionPolicy) implements FilterQuery<E> {
        private DefaultFilterQuery {
            Objects.requireNonNull(filterContext, "Context cannot be null");
            Objects.requireNonNull(dslParser, "DSL parser cannot be null");
            Objects.requireNonNull(projectionPolicy, "ProjectionPolicy cannot be null");
        }

        @Override
        public <P extends Enum<P> & PropertyReference> PredicateResolver<E> toResolver(FilterRequest<P> request) {
            // Validate input parameters
            Objects.requireNonNull(request, "Filter request cannot be null");

            if (!request.hasFilters()) return (r,b,cb) -> cb.conjunction();

            // Parse DSL into a filter tree
            FilterTree filterTree = dslParser.parse(request.combineWith());

            // Generate a condition tree from filter definitions and context
            Map<String, FilterDefinition<P>> definitionMap = request.filters();
            Condition condition = filterTree.generate(definitionMap, filterContext);

            // Convert condition into a PredicateResolver with deferred filter arguments
            //noinspection unchecked,rawtypes
            QueryExecutionParams params = new QueryExecutionParams(
                    toFilterArgumentRegistry((Map) definitionMap),
                    request.projection(),
                    request.pagination(),
                    projectionPolicy
            );

            //noinspection unchecked
            return (PredicateResolver<E>) filterContext.toResolver(condition, params);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public <P extends Enum<P> & PropertyReference, R> QueryExecutor<R> toExecutor(FilterRequest<P> request) {
            PredicateResolver<E> pr = toResolver(request);
            Map<String, Object> params = toFilterArgumentRegistry((Map) request.filters());
            return new DefaultQueryExecutor<>(
                    pr,
                    new QueryExecutionParams(params, request.projection(), request.pagination(), projectionPolicy)
            );
        }

        /**
         * Converts a map of filter definitions to a registry map of filter arguments.
         * <p>
         * This method extracts the values from each {@link FilterDefinition} in the input map
         * and collects them into a new map keyed by the same keys, effectively forming
         * a registry of current filter parameter values for use during query processing.
         * </p>
         *
         * @param definitionMap the map of filter keys to their corresponding {@link FilterDefinition} objects
         * @return a new map mapping each filter key to the current value from its filter definition
         * @throws NullPointerException if {@code definitionMap} is null or contains null keys or values
         */
        private static Map<String, Object> toFilterArgumentRegistry(Map<String, FilterDefinition<?>> definitionMap) {
            Map<String, Object> result = new HashMap<>();
            definitionMap.forEach((k, definition) -> result.put(k, definition.value()));
            return result;
        }
    }

    /**
     * <h2>DefaultQueryExecutor</h2>
     *
     * <p>
     * Default and generic implementation of {@link QueryExecutor}, responsible
     * for coordinating between:
     * <ul>
     *   <li>a {@link PredicateResolver} (constructing the JPA predicate)</li>
     *   <li>{@link QueryExecutionParams} (projection, pagination, etc.)</li>
     *   <li>and the {@link ExecutionStrategy} (concrete execution logic)</li>
     * </ul>
     * </p>
     *
     * <p>
     * This implementation is agnostic of the backend type (JPA, SQL, etc.):
     * it delegates execution to the provided {@link ExecutionStrategy}.
     * </p>
     *
     * <h3>Usage Example:</h3>
     * <pre>{@code
     * PredicateResolver<User> resolver = filterContext.toResolver(Tuple.class, condition, params);
     * QueryExecutor<User, Tuple> executor = new DefaultQueryExecutor<>(resolver, params);
     *
     * List<Tuple> results = executor.executeWith(em, new SingleQueryExecutionStrategy<>());
     * }</pre>
     *
     * @param <E> Persisted entity type
     * @param <R> Result type (e.g. {@code Tuple}, {@code UserDto}, {@code Map<String,Object>})
     */
    private record DefaultQueryExecutor<E, R>(PredicateResolver<E> resolver, QueryExecutionParams params) implements QueryExecutor<R> {

        private static final Logger log = Logger.getLogger(DefaultQueryExecutor.class.getName());

        /**
         * Executes the FilterQL query using the given {@link ExecutionStrategy}.
         *
         * <p>
         * This method:
         * <ol>
         *   <li>Validates inputs</li>
         *   <li>Logs execution context</li>
         *   <li>Delegates execution to the strategy</li>
         * </ol>
         * </p>
         *
         * @param <Context> the type of execution context (e.g., EntityManager for JPA)
         * @param ctx      Execution context used to execute the query (e.g., EntityManager for JPA)
         * @param strategy Execution strategy (e.g. {@code MultiQueryExecutionStrategy})
         * @return Typed result from the execution strategy
         */
        @Override
        public <Context> R executeWith(Context ctx, ExecutionStrategy<R> strategy) {
            Objects.requireNonNull(ctx, "Execution context cannot be null");
            Objects.requireNonNull(strategy, "ExecutionStrategy cannot be null");

            log.fine(() -> String.format(
                    "Executing FilterQL query: projection=%s, pagination=%s",
                    params.projection(),
                    params.pagination()
            ));

            long start = System.nanoTime();
            R results = strategy.execute(ctx, resolver, params);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            log.info(() -> String.format(
                    "FilterQL query executed successfully in %d ms",
                    durationMs
            ));

            return results;
        }
    }

}

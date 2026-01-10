package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;

/**
 * <h2>ExecutionStrategy</h2>
 *
 * <p>
 * Functional interface describing the execution strategy of a FilterQL query.
 * </p>
 *
 * <p>
 * Each implementation defines <b>both</b>:
 * <ul>
 *   <li><b>HOW</b> to execute the query: single query, multi-query, native SQL, cached, etc.</li>
 *   <li><b>WHAT</b> to return: List, Page, Stream, Count, custom wrapper, etc.</li>
 * </ul>
 * </p>
 *
 * <p>
 * This dual responsibility allows for maximum flexibility: new execution approaches
 * and new return types can be added without modifying the {@link QueryExecutor} interface.
 * </p>
 *
 * <h3>Common Strategy Implementations:</h3>
 * <ul>
 *   <li><b>SingleQueryExecutionStrategy&lt;List&lt;R&gt;&gt;</b>:
 *       Executes a single JPA CriteriaQuery with joins, returns a List</li>
 *   <li><b>MultiQueryExecutionStrategy&lt;List&lt;R&gt;&gt;</b>:
 *       Splits into multiple optimized subqueries for collections, returns a List</li>
 *   <li><b>NativeExecutionStrategy&lt;List&lt;R&gt;&gt;</b>:
 *       Builds and executes native SQL with manual mapping, returns a List</li>
 *   <li><b>PagedExecutionStrategy&lt;Page&lt;R&gt;&gt;</b>:
 *       Executes query with pagination metadata, returns a Page</li>
 *   <li><b>CountExecutionStrategy&lt;Long&gt;</b>:
 *       Executes optimized count query, returns Long</li>
 *   <li><b>StreamExecutionStrategy&lt;Stream&lt;R&gt;&gt;</b>:
 *       Returns results as a Stream for memory-efficient processing</li>
 * </ul>
 *
 * <h3>Typical Usage Pipeline:</h3>
 * <pre>{@code
 * // 1. Create an execution strategy
 * ExecutionStrategy<List<UserDto>> strategy = new MultiQueryExecutionStrategy<>();
 *
 * // 2. Get executor from FilterQuery
 * QueryExecutor<UserDto> executor = filterQuery.resolve(request, UserDto.class);
 *
 * // 3. Execute
 * List<UserDto> results = executor.executeWith(entityManager, strategy);
 * }</pre>
 *
 * <h3>Custom Strategy Example:</h3>
 * <pre>{@code
 * public class CachedExecutionStrategy<R> implements ExecutionStrategy<List<R>> {
 *
 *     private final Cache cache;
 *
 *     {@literal @}Override
 *     public List<R> execute(EntityManager em, PredicateResolver<?> pr, QueryExecutionParams params) {
 *         String cacheKey = generateKey(pr, params);
 *         return cache.get(cacheKey, () -> {
 *             // Fallback to actual query execution
 *             CriteriaQuery<R> query = buildQuery(em, pr, params);
 *             return em.createQuery(query).getResultList();
 *         });
 *     }
 *
 *     {@literal @}Override
 *     public Class<List<R>> getResultType() {
 *         return (Class<List<R>>) (Class<?>) List.class;
 *     }
 * }
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * Strategy implementations should be stateless and thread-safe when possible.
 * If state is required (e.g., caching, configuration), ensure proper synchronization.
 * </p>
 *
 * @param <R> Result type returned by this strategy. Can be:
 *           <ul>
 *             <li>{@code List<T>} for standard list results</li>
 *             <li>{@code Page<T>} for paginated results</li>
 *             <li>{@code Stream<T>} for streamed results</li>
 *             <li>{@code Long} for count queries</li>
 *             <li>Any custom wrapper type (e.g., {@code QueryResult<T>})</li>
 *           </ul>
 *
 * @see QueryExecutor
 * @see PredicateResolver
 * @see QueryExecutionParams
 */
@FunctionalInterface
public interface ExecutionStrategy<R> {

    /**
     * Executes the FilterQL query using this strategy's specific execution logic.
     *
     * <p>
     * This method receives all necessary components to build and execute the query:
     * <ul>
     *   <li>The execution context (e.g., EntityManager for JPA)</li>
     *   <li>The {@link PredicateResolver} for constructing filter predicates</li>
     *   <li>The {@link QueryExecutionParams} with projection, pagination, sorting, etc.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Implementations are responsible for:
     * <ul>
     *   <li>Building the appropriate query using the context</li>
     *   <li>Applying filters from the PredicateResolver</li>
     *   <li>Applying projection, pagination, and sorting from params</li>
     *   <li>Executing the query</li>
     *   <li>Transforming results to the target type R</li>
     * </ul>
     * </p>
     *
     * <h4>Example Implementation Sketch (JPA adapter):</h4>
     * <pre>{@code
     * public List<UserDto> execute(EntityManager em, PredicateResolver<?> pr, QueryExecutionParams params) {
     *     CriteriaBuilder cb = em.getCriteriaBuilder();
     *     CriteriaQuery<UserDto> query = cb.createQuery(UserDto.class);
     *     Root<?> root = query.from(pr.getEntityType());
     *
     *     // Apply filters
     *     query.where(pr.resolve(root, query, cb));
     *
     *     // Apply projection
     *     query.select(buildProjection(params, root));
     *
     *     // Apply sorting
     *     if (params.hasSort()) {
     *         query.orderBy(buildOrderList(params, root, cb));
     *     }
     *
     *     // Execute with pagination
     *     TypedQuery<UserDto> typedQuery = em.createQuery(query);
     *     if (params.hasPagination()) {
     *         typedQuery.setFirstResult(params.getOffset());
     *         typedQuery.setMaxResults(params.getPageSize());
     *     }
     *
     *     return typedQuery.getResultList();
     * }
     * }</pre>
     *
     * @param <Context> the type of execution context (e.g., EntityManager for JPA)
     * @param ctx the execution context used to build and execute the query.
     *           Must not be null.
     * @param pr the {@link PredicateResolver} responsible for building filter predicates
     *           from the FilterQL request. Must not be null.
     * @param params execution parameters including projection, pagination, sorting, etc.
     *              Must not be null.
     * @return Result of type {@code R} as defined by this strategy implementation
     * @throws IllegalArgumentException if any parameter is null
     */
    <Context> R execute(Context ctx, PredicateResolver<?> pr, QueryExecutionParams params);
}
package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.model.QueryExecutionParams;

/**
 * <h2>QueryExecutor</h2>
 *
 * <p>
 * Represents a generic, reusable executor for FilterQL queries,
 * decoupled from the backend type and data model.
 * </p>
 *
 * <p>
 * The {@code QueryExecutor} does not handle query execution internals itself:
 * it delegates this responsibility to an {@link ExecutionStrategy},
 * which encapsulates the execution logic (simple JPA, multi-query, native SQL, etc.).
 * </p>
 *
 * <p>
 * This design provides maximum flexibility: the same executor can work with any strategy
 * that produces results of type {@code R}.
 * </p>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Execute to get a list of DTOs (JPA adapter example)
 * QueryExecutor<List<UserDto>> listExecutor = filterQuery.toExecutor(request);
 * List<UserDto> users = listExecutor.executeWith(entityManager, new MultiQueryFetchStrategy<>());
 *
 * // Execute to get a page of results
 * QueryExecutor<Page<UserDto>> pageExecutor = filterQuery.toExecutor(request);
 * Page<UserDto> page = pageExecutor.executeWith(entityManager, new PagedFetchStrategy<>());
 *
 * // Execute to get a count
 * QueryExecutor<Long> countExecutor = filterQuery.toExecutor(request);
 * Long count = countExecutor.executeWith(entityManager, new CountStrategy<>());
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * Implementations should be thread-safe if they maintain internal state.
 * The context object passed to {@code executeWith} should be managed appropriately
 * for thread safety (e.g., per-thread EntityManager in JPA).
 * </p>
 *
 * @param <Result> Result type produced by this executor (e.g. {@code List<UserDto>}, {@code Page<UserDto>}, {@code Long})
 *
 * @see ExecutionStrategy
 * @see FilterQuery
 */
public interface QueryExecutor<Result> {

    /**
     * Executes the query using the provided strategy.
     *
     * <p>
     * The strategy defines <b>how</b> the query is executed (single query, multi-query,
     * native SQL, cached, etc.) while the executor type {@code Result} defines <b>what</b>
     * is returned.
     * </p>
     *
     * <p>
     * The executor orchestrates the execution by providing the strategy with:
     * <ul>
     *   <li>The execution context (e.g., EntityManager for JPA)</li>
     *   <li>The {@link PredicateResolver} for building filter predicates</li>
     *   <li>The {@link QueryExecutionParams} containing projection, pagination, sorting, etc.</li>
     * </ul>
     * </p>
     *
     * <h4>Examples (JPA adapter):</h4>
     * <pre>{@code
     * // List execution
     * QueryExecutor<List<UserDto>> listExecutor = ...;
     * List<UserDto> users = listExecutor.executeWith(entityManager, new MultiQueryFetchStrategy<>());
     *
     * // Paginated execution
     * QueryExecutor<Page<UserDto>> pageExecutor = ...;
     * Page<UserDto> page = pageExecutor.executeWith(entityManager, new PagedFetchStrategy<>());
     *
     * // Count execution
     * QueryExecutor<Long> countExecutor = ...;
     * Long total = countExecutor.executeWith(entityManager, new CountStrategy<>());
     * }</pre>
     *
     * @param <Context> the type of execution context (e.g., EntityManager for JPA)
     * @param ctx the execution context. Must not be null.
     * @param strategy the execution strategy defining the execution logic.
     *                Must not be null and must produce results of type {@code Result}.
     * @return a result of type {@code Result}
     * @throws IllegalArgumentException if ctx or strategy is null
     */
    <Context> Result executeWith(Context ctx, ExecutionStrategy<Result> strategy);
}
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
 * which encapsulates the execution logic (simple JPA, multi-query, native SQL,
 * etc.).
 * </p>
 *
 * <p>
 * This design provides maximum flexibility: the same executor can work with any
 * strategy. The chosen strategy govern the expected result type.
 * </p>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * Implementations should be thread-safe if they maintain internal state.
 * The context object passed to {@code executeWith} should be managed
 * appropriately
 * for thread safety (e.g., per-thread EntityManager in JPA).
 * </p>
 *
 * @param <Em> The execution management context (e.g EntityManager for JPA).
 *
 * @see ExecutionStrategy
 * @see FilterQuery
 */
public interface QueryExecutor<Em> {

    /**
     * Executes the query using the provided strategy.
     *
     * <p>
     * The strategy defines <b>how</b> the query is executed (single query,
     * multi-query,
     * native SQL, cached, etc.) and <b>what</b> {@link Result} is returned.
     * </p>
     *
     * <p>
     * The executor orchestrates the execution by providing the strategy with:
     * <ul>
     * <li>The specific implementation defined execution management context (e.g., EntityManager for JPA)</li>
     * <li>The {@link ConditionResolver} for building filter predicates</li>
     * <li>The {@link QueryExecutionParams} containing projection, pagination,
     * sorting, etc.</li>
     * </ul>
     * </p>
     *
     * <h4>Implementation Examples:</h4>
     *
     * <pre>{@code
     * var executor = filterQuery.toExecutor(request);
     *
     * // Execute to get a list of DTOs
     * List<?> users = executor.executeWith(em, new MultiQueryFetchStrategy<>());
     *
     * // Execute to get a page of results
     * Page<UserDto> page = executor.executeWith(em, new PagedFetchStrategy<>());
     *
     * // Execute to get a count
     * Long count = executor.executeWith(em, new CountStrategy<>());
     * }</pre>
     *
     * @param em      the execution context. Must not be null.
     * @param strategy the execution strategy defining the execution logic.
     *                 Must not be null and must produce results of type
     *                 {@code Result}.
     * @return a result of type {@code Result}
     * @throws IllegalArgumentException if ctx or strategy is null
     */
    <Result> Result executeWith(Em em, ExecutionStrategy<Result> strategy);
}
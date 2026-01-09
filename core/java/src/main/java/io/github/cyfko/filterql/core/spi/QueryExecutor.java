package io.github.cyfko.filterql.core.spi;

import jakarta.persistence.EntityManager;
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
 * // Execute to get a list of DTOs
 * QueryExecutor<List<UserDto>> listExecutor = filterQuery.resolve(request, List.class);
 * List<UserDto> users = listExecutor.executeWith(em, new MultiQueryExecutionStrategy<>());
 *
 * // Execute to get a page of results
 * QueryExecutor<Page<UserDto>> pageExecutor = filterQuery.resolve(request, Page.class);
 * Page<UserDto> page = pageExecutor.executeWith(em, new PagedExecutionStrategy<>());
 *
 * // Execute to get a count
 * QueryExecutor<Long> countExecutor = filterQuery.resolve(request, Long.class);
 * Long count = countExecutor.executeWith(em, new CountExecutionStrategy<>());
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * Implementations should be thread-safe if they maintain internal state.
 * However, the {@link EntityManager} passed to {@code executeWith} is typically
 * not thread-safe and should be managed per-thread.
 * </p>
 *
 * @param <R> Result type produced by this executor (e.g. {@code List<UserDto>}, {@code Page<UserDto>}, {@code Long})
 *
 * @see ExecutionStrategy
 * @see FilterQuery
 */
public interface QueryExecutor<R> {

    /**
     * Executes the query using the provided strategy.
     *
     * <p>
     * The strategy defines <b>how</b> the query is executed (single query, multi-query,
     * native SQL, cached, etc.) while the executor type {@code R} defines <b>what</b>
     * is returned.
     * </p>
     *
     * <p>
     * The executor orchestrates the execution by providing the strategy with:
     * <ul>
     *   <li>The {@link EntityManager} for database access</li>
     *   <li>The {@link PredicateResolver} for building filter predicates</li>
     *   <li>The {@link QueryExecutionParams} containing projection, pagination, sorting, etc.</li>
     * </ul>
     * </p>
     *
     * <h4>Examples:</h4>
     * <pre>{@code
     * // List execution
     * QueryExecutor<List<UserDto>> listExecutor = ...;
     * List<UserDto> users = listExecutor.executeWith(em, new MultiQueryExecutionStrategy<>());
     *
     * // Paginated execution
     * QueryExecutor<Page<UserDto>> pageExecutor = ...;
     * Page<UserDto> page = pageExecutor.executeWith(em, new PagedExecutionStrategy<>());
     *
     * // Count execution
     * QueryExecutor<Long> countExecutor = ...;
     * Long total = countExecutor.executeWith(em, new CountExecutionStrategy<>());
     * }</pre>
     *
     * @param em the {@link EntityManager} to use for query execution.
     *           Must not be null. Should be an active, transaction-aware EntityManager.
     * @param strategy the execution strategy defining the execution logic.
     *                Must not be null and must produce results of type {@code R}.
     * @return a result of type {@code R}
     * @throws IllegalArgumentException if em or strategy is null
     * @throws jakarta.persistence.PersistenceException if query execution fails
     */
    R executeWith(EntityManager em, ExecutionStrategy<R> strategy);
}
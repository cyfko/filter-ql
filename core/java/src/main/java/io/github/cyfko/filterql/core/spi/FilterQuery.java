package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.PropertyReference;

/**
 * <h2>FilterQuery</h2>
 *
 * <p>
 * Facade interface representing the central entry point for the entire lifecycle
 * of a FilterQL query — from predicate construction to query execution.
 * </p>
 *
 * <p>
 * The {@code FilterQuery} provides a clean, high-level API that abstracts away
 * the complexity of query building and execution, offering multiple levels of control:
 * </p>
 *
 * <ul>
 *   <li><b>Low-level:</b> Obtain a {@link PredicateResolver} for manual query building</li>
 *   <li><b>Mid-level:</b> Obtain a {@link QueryExecutor} and choose an {@link ExecutionStrategy}</li>
 *   <li><b>High-level:</b> Execute directly with a single method call</li>
 * </ul>
 *
 * <h3>Architecture Overview:</h3>
 * <pre>
 * ┌─────────────────┐
 * │  FilterRequest  │  (User input: filters, projection, pagination)
 * └────────┬────────┘
 *          │
 *          ▼
 * ┌─────────────────┐
 * │  FilterQuery    │  (This interface - orchestrates the process)
 * └────────┬────────┘
 *          │
 *          ├──────────────────────────────┐
 *          ▼                              ▼
 * ┌──────────────────┐         ┌──────────────────┐
 * │ PredicateResolver│         │  QueryExecutor   │
 * └──────────────────┘         └────────┬─────────┘
 *                                       │
 *                                       ▼
 *                              ┌──────────────────┐
 *                              │ExecutionStrategy │
 *                              └──────────────────┘
 * </pre>
 *
 * <h3>Usage Examples:</h3>
 *
 * <h4>Example 1: High-level execution (simplest)</h4>
 * <pre>{@code
 * FilterRequest<UserProperty> request = FilterRequest.builder()
 *     .filter(UserProperty.NAME, "LIKE", "John%")
 *     .project(UserProperty.ID, UserProperty.NAME, UserProperty.EMAIL)
 *     .page(0, 20)
 *     .sort(UserProperty.NAME, "ASC")
 *     .build();
 *
 * // For JPA, context would be an EntityManager
 * List<UserDto> users = filterQuery.execute(
 *     request,
 *     context,
 *     new MultiQueryExecutionStrategy<>()
 * );
 * }</pre>
 *
 * <h4>Example 2: Mid-level with executor (more control)</h4>
 * <pre>{@code
 * FilterRequest<UserProperty> request = ...;
 *
 * QueryExecutor<UserDto> executor = filterQuery.toExecutor(request);
 *
 * // Execute with different strategies (context is adapter-specific, e.g., EntityManager for JPA)
 * List<UserDto> list = executor.executeWith(context, new SingleQueryExecutionStrategy<>());
 * Page<UserDto> page = executor.executeWith(context, new PagedExecutionStrategy<>());
 * Long count = executor.executeWith(context, new CountExecutionStrategy<>());
 * }</pre>
 *
 * <h4>Example 3: Low-level with predicate resolver (maximum control)</h4>
 * <pre>{@code
 * FilterRequest<UserProperty> request = ...;
 *
 * PredicateResolver<User> resolver = filterQuery.resolve(request);
 *
 * CriteriaBuilder cb = em.getCriteriaBuilder();
 * CriteriaQuery<User> query = cb.createQuery(User.class);
 * Root<User> root = query.from(User.class);
 *
 * // Manually build custom query
 * query.select(root)
 *      .where(resolver.toPredicate(root, query, cb))
 *      .orderBy(cb.asc(root.get("name")));
 *
 * List<User> users = em.createQuery(query).getResultList();
 * }</pre>
 *
 * <h3>Design Considerations:</h3>
 * <ul>
 *   <li><b>Flexibility:</b> Works with entities, DTOs, Tuples, Maps, or custom types</li>
 *   <li><b>Extensibility:</b> New execution strategies can be added without changing this interface</li>
 *   <li><b>Testability:</b> Each component (resolver, executor, strategy) can be tested independently</li>
 *   <li><b>Separation of Concerns:</b> Filter building, query execution, and result transformation are separated</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * Implementations of {@code FilterQuery} should be thread-safe and reusable across multiple
 * requests. However, the context instances passed to execution methods must be
 * managed per-thread according to the underlying adapter's best practices
 * (e.g., EntityManager for JPA must be managed per-thread).
 * </p>
 *
 * @param <E> Entity type targeted by this FilterQuery (e.g. {@code User}, {@code Order})
 *
 * @see FilterRequest
 * @see PredicateResolver
 * @see QueryExecutor
 * @see ExecutionStrategy
 */
public interface FilterQuery<E> {

    /**
     * Constructs a {@link PredicateResolver} capable of translating a {@link FilterRequest}
     * into JPA predicates.
     *
     * <p>
     * Use this method when you need low-level control over query building,
     * such as combining FilterQL predicates with custom predicates or
     * building complex queries manually.
     * </p>
     *
     * <h4>Example:</h4>
     * <pre>{@code
     * PredicateResolver<User> resolver = filterQuery.resolve(request);
     *
     * CriteriaBuilder cb = em.getCriteriaBuilder();
     * CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
     * Root<User> root = countQuery.from(User.class);
     *
     * countQuery.select(cb.count(root))
     *           .where(
     *               cb.and(
     *                   resolver.toPredicate(root, countQuery, cb),
     *                   cb.equal(root.get("status"), "ACTIVE") // custom predicate
     *               )
     *           );
     *
     * Long count = em.createQuery(countQuery).getSingleResult();
     * }</pre>
     *
     * @param request FilterQL request containing filters and associated parameters
     * @param <P> Enumeration type representing filterable properties (must implement {@link PropertyReference})
     * @return a {@link PredicateResolver} ready to generate JPA predicates
     * @throws IllegalArgumentException if request is null or invalid
     */
    <P extends Enum<P> & PropertyReference> PredicateResolver<E> toResolver(FilterRequest<P> request);

    /**
     * Constructs a {@link QueryExecutor} capable of executing the FilterQL request
     * for a specific result type.
     *
     * <p>
     * Use this method when you want to execute the same query with different strategies,
     * or when you need to prepare an executor that will be used multiple times.
     * </p>
     *
     * <p>
     * The result type parameter allows transforming query results into:
     * <ul>
     *   <li><b>Entity classes:</b> {@code User.class} for JPA entities</li>
     *   <li><b>DTO classes:</b> {@code UserDto.class} for data transfer objects</li>
     *   <li><b>Tuple:</b> {@code Tuple.class} for dynamic multi-column results</li>
     *   <li><b>Map:</b> {@code Map.class} for key-value results</li>
     *   <li><b>Custom types:</b> Any class with appropriate constructor or builder</li>
     * </ul>
     * </p>
     *
     * <h4>Example with Multiple Strategies:</h4>
     * <pre>{@code
     * FilterRequest<ProductProperty> request = ...;
     * QueryExecutor<ProductDto> executor = filterQuery.resolve(request, ProductDto.class);
     *
     * // Try different execution strategies
     * List<ProductDto> all = executor.executeWith(em, new SingleQueryExecutionStrategy<>());
     * Page<ProductDto> paged = executor.executeWith(em, new PagedExecutionStrategy<>());
     * Stream<ProductDto> stream = executor.executeWith(em, new StreamExecutionStrategy<>());
     * }</pre>
     *
     * @param request FilterQL request describing projection, filters, sorting, and pagination
     * @param <P> Enumeration type representing filterable properties
     * @param <R> Type of the query result
     * @return a {@link QueryExecutor} configured for the specified result type
     * @throws IllegalArgumentException if request or resultType is null
     */
    <P extends Enum<P> & PropertyReference, R> QueryExecutor<R> toExecutor(FilterRequest<P> request);

    /**
     * Executes a FilterQL request in a single method call using the specified strategy.
     *
     * <p>
     * This is the highest-level convenience method that handles the entire query lifecycle:
     * resolving the executor, executing the query, and returning results.
     * </p>
     *
     * <p>
     * The return type {@code T} is inferred from the strategy's generic type parameter,
     * ensuring type safety at compile time.
     * </p>
     *
     * <h4>Usage with Different Strategies:</h4>
     * <pre>{@code
     * FilterRequest<UserProperty> request = ...;
     *
     * // Returns List<UserDto> - type inferred from strategy
     * ExecutionStrategy<List<UserDto>> listStrategy = new MultiQueryExecutionStrategy<>();
     * List<UserDto> users = filterQuery.execute(request, context, listStrategy);
     *
     * // Returns Page<UserDto> - type inferred from strategy
     * ExecutionStrategy<Page<UserDto>> pageStrategy = new PagedExecutionStrategy<>();
     * Page<UserDto> page = filterQuery.execute(request, context, pageStrategy);
     *
     * // Returns Long - type inferred from strategy
     * ExecutionStrategy<Long> countStrategy = new CountExecutionStrategy<>();
     * Long count = filterQuery.execute(request, context, countStrategy);
     * }</pre>
     *
     * @param request FilterQL request to execute
     * @param ctx the execution context used for query execution (e.g., EntityManager for JPA).
     *           Must not be null.
     * @param strategy Execution strategy defining how to execute and what to return.
     *                The generic type parameter {@code T} determines the return type.
     * @param <P> Filter property enumeration type
     * @param <Context> the type of execution context (e.g., EntityManager for JPA)
     * @param <T> Return type, inferred from the strategy's type parameter
     * @return Result of type {@code T} as defined by the strategy
     * @throws IllegalArgumentException if any parameter is null
     */
    default <P extends Enum<P> & PropertyReference, Context, T> T execute(
            FilterRequest<P> request,
            Context ctx,
            ExecutionStrategy<T> strategy
    ) {
        return this.<P,T>toExecutor(request).executeWith(ctx, strategy);
    }
}
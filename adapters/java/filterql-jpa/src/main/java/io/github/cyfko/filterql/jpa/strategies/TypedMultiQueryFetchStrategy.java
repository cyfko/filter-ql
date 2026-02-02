package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.filterql.jpa.strategies.helper.RowBuffer;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Typed wrapper for {@link MultiQueryFetchStrategy} that transforms raw result maps into a specific type.
 * <p>
 * This strategy delegates to an underlying {@link MultiQueryFetchStrategy} for batch projection execution,
 * then applies a user-provided transformer to convert each raw result map into the desired output type.
 * It is designed to enable type-safe, fluent projection queries with custom DTOs or domain objects.
 * </p>
 *
 * @param <R> the type of the transformed result objects
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public class TypedMultiQueryFetchStrategy<R> implements ExecutionStrategy<List<R>> {
    private final MultiQueryFetchStrategy multiQueryFetchStrategy;
    private final Function<RowBuffer,R> resultTransformer;

    /**
     * Constructs a typed fetch strategy with the specified projection class and result transformer.
     * <p>
     * The transformer is applied to each result map returned by the underlying batch strategy.
     * </p>
     *
     * @param projectionClass      the DTO or entity class describing the projection structure
     * @param resultTransformer    a function that transforms a raw result map into the desired type {@code R}
     *
     * @throws NullPointerException if {@code resultTransformer} is {@code null}
     */
    public TypedMultiQueryFetchStrategy(Class<R> projectionClass, Function<RowBuffer, R> resultTransformer) {
        multiQueryFetchStrategy = new MultiQueryFetchStrategy(projectionClass);
        this.resultTransformer = Objects.requireNonNull(resultTransformer, "resultTransformer cannot be null");
    }

    /**
     * Constructs a typed fetch strategy with the specified projection class, computer resolver, and result transformer.
     * <p>
     * The transformer is applied to each result map returned by the underlying batch strategy.
     * The computer resolver is used to resolve computed field providers, if needed.
     * </p>
     *
     * @param projectionClass      the DTO or entity class describing the projection structure
     * @param instanceResolver      the resolver for computed field providers, or {@code null} if none are needed
     * @param resultTransformer    a function that transforms a raw result map into the desired type {@code R}
     *
     * @throws NullPointerException if {@code resultTransformer} is {@code null}
     */
    public TypedMultiQueryFetchStrategy(Class<R> projectionClass, InstanceResolver instanceResolver, Function<RowBuffer, R> resultTransformer) {
        multiQueryFetchStrategy = new MultiQueryFetchStrategy(projectionClass, instanceResolver);
        this.resultTransformer = Objects.requireNonNull(resultTransformer, "resultTransformer cannot be null");
    }

    /**
     * Executes the batch projection fetch logic and transforms each result map into the desired type.
     * <p>
     * The raw result maps are obtained from the underlying {@link MultiQueryFetchStrategy}, then transformed
     * using the provided transformer function.
     * </p>
     *
     * @param ctx    the JPA EntityManager for database query execution
     * @param pr     a predicate resolver for filtering logic
     * @param params query execution parameters, including projection selection, sorting, and pagination
     * @return a list of transformed results, each of type {@code R}
     */
    @Override
    public <Context> List<R> execute(Context ctx, PredicateResolver<?> pr, QueryExecutionParams params) {
        return multiQueryFetchStrategy.execute(ctx, pr, params).stream().map(resultTransformer).toList();
    }
}



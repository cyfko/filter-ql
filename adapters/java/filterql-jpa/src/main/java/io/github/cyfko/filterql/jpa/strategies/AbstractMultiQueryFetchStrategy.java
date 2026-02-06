package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.filterql.jpa.strategies.helper.QueryPlan;
import io.github.cyfko.filterql.jpa.strategies.helper.RowBuffer;
import io.github.cyfko.jpametamodel.ProjectionRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.*;
import java.util.logging.Logger;

/**
 * Abstract base class for multi-query JPA projection strategies using Template
 * Method Pattern.
 * <p>
 * Defines a clear 5-step execution pipeline:
 * <ol>
 * <li>{@link #step1_BuildExecutionContext} - Prepare query, root, execution
 * plan</li>
 * <li>{@link #step2_ExecuteRootQuery} - Execute the main entity query</li>
 * <li>{@link #step3_ExecuteCollectionQueries} - Load nested collections</li>
 * <li>{@link #step4_transform} - Calculate @Computed fields and convert to
 * Convert to List&lt;Map&gt;</li>
 * </ol>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public abstract class AbstractMultiQueryFetchStrategy implements ExecutionStrategy<List<RowBuffer>> {

    protected static final Logger logger = Logger.getLogger(AbstractMultiQueryFetchStrategy.class.getName());

    protected final Class<?> dtoClass;
    protected final Class<?> rootEntityClass;
    protected final InstanceResolver instanceResolver;

    protected AbstractMultiQueryFetchStrategy(Class<?> dtoClass, InstanceResolver instanceResolver) {
        this.dtoClass = Objects.requireNonNull(dtoClass, "dtoClass cannot be null");
        this.instanceResolver = instanceResolver;

        var projMeta = ProjectionRegistry.getMetadataFor(dtoClass);
        if (projMeta == null) {
            throw new IllegalArgumentException("No projection metadata found for: " + dtoClass.getName());
        }
        this.rootEntityClass = projMeta.entityClass();
        if (this.rootEntityClass == null) {
            throw new IllegalArgumentException("No entity class for projection: " + dtoClass.getName());
        }
    }

    // ==================== Template Method ====================

    /**
     * Template method defining the execution pipeline.
     * This method is final to ensure the execution order is preserved.
     */
    @Override
    public final <Context> List<RowBuffer> execute(Context ctx, PredicateResolver<?> pr, QueryExecutionParams params) {

        EntityManager em = (EntityManager) ctx;
        long startTime = System.nanoTime();

        // Step 1: Build execution context
        ExecutionContext exeCtx = step1_BuildExecutionContext(em, pr, params, dtoClass);
        logStep("step1_BuildExecutionContext", startTime);

        // Step 2: Execute root query
        long t2 = System.nanoTime();
        Map<Object, RowBuffer> rootResults = step2_ExecuteRootQuery(exeCtx, params);
        logStep("step2_ExecuteRootQuery", t2);

        if (rootResults.isEmpty()) {
            logger.fine("Root query returned no results");
            return List.of();
        }

        // Step 3: Execute collection queries
        long t3 = System.nanoTime();
        step3_ExecuteCollectionQueries(exeCtx, rootResults);
        logStep("step3_ExecuteCollectionQueries", t3);

        // Step 4: Apply computed fields
        long t4 = System.nanoTime();
        List<RowBuffer> output = step4_transform(exeCtx, rootResults);
        logStep("step4_step4_transform", t4);

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        logger.info(() -> String.format("Multi-query completed in %dms: %d results", durationMs, output.size()));

        return output;
    }

    // ==================== Abstract Template Steps ====================

    /**
     * Step 1: Build the execution context with query, root, and plan.
     */
    protected abstract ExecutionContext step1_BuildExecutionContext(EntityManager em,
            PredicateResolver<?> pr,
            QueryExecutionParams params,
            Class<?> dtoClass);

    /**
     * Step 2: Execute the root entity query.
     */
    protected abstract Map<Object, RowBuffer> step2_ExecuteRootQuery(ExecutionContext ctx, QueryExecutionParams params);

    /**
     * Step 3: Execute queries for nested collections.
     */
    protected abstract void step3_ExecuteCollectionQueries(ExecutionContext ctx, Map<Object, RowBuffer> rootResults);

    /**
     * Step 4: Apply computed field calculations to rows and build the final output
     * as List of Maps.
     */
    protected abstract List<RowBuffer> step4_transform(ExecutionContext ctx, Map<Object, RowBuffer> rootResults);

    protected static void logStep(String stepName, long startNanos) {
        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logger.fine(() -> String.format("  %s: %dms", stepName, durationMs));
        }
    }

    // ==================== Execution Context ====================

    /**
     * Execution context holding query state and plan.
     * Uses public fields for easy access in subclasses.
     */
    public record ExecutionContext(
            EntityManager em,
            CriteriaBuilder cb,
            Root<?> root,
            CriteriaQuery<Tuple> query,
            PredicateResolver<?> predicateResolver,
            QueryPlan plan,
            Map<String, Pagination> collectionPagination) {
    }
}

package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Execution strategy that computes the number of rows matching a given filter.
 * <p>
 * This strategy builds a JPA {@link CriteriaQuery} that selects a {@link Long} count
 * for the entity type associated with the provided {@code projectionClass}, applying
 * the same filter logic as any other strategy via a shared {@link PredicateResolver}.
 * </p>
 *
 * <p><b>Accepted classes:</b></p>
 * <ul>
 *   <li>A class annotated with {@link io.github.cyfko.projection.Projection}, describing a DTO projection.</li>
 *   <li>Or directly an entity class annotated with {@link jakarta.persistence.Entity}, when the metamodel
 *       supports auto-projection for entities.</li>
 * </ul>
 *
 * <p>
 * In both cases, the underlying entity type is resolved through
 * {@link io.github.cyfko.projection.metamodel.ProjectionRegistry}, and the count is performed
 * against that entity, independently of how the data will be fetched or projected.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Using a DTO projection class
 * ExecutionStrategy<Long> dtoCount = new CountMatchingStrategy(UserDTO.class);
 *
 * // Using the entity class directly (auto-projection enabled)
 * ExecutionStrategy<Long> entityCount = new CountMatchingStrategy(UserEntity.class);
 *
 * PredicateResolver<?> resolver = filterContext.toResolver(condition, params);
 *
 * Long totalFromDto = dtoCount.execute(entityManager, resolver, params);
 * Long totalFromEntity = entityCount.execute(entityManager, resolver, params);
 * }</pre>
 *
 * @param projectionClass either:
 *                        <ul>
 *                          <li>a class annotated with {@link io.github.cyfko.projection.Projection}, or</li>
 *                          <li>an entity class annotated with {@link jakarta.persistence.Entity}</li>
 *                        </ul>
 *                        for which projection metadata is available in the registry
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public record CountStrategy(Class<?> projectionClass) implements ExecutionStrategy<Long> {
    private static final Logger logger = Logger.getLogger(CountStrategy.class.getName());

    public CountStrategy {
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
    }

    @Override
    public Long execute(EntityManager em, PredicateResolver<?> pr, QueryExecutionParams params) {
        long startTime = System.nanoTime();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<?> root = countQuery.from(ProjectionRegistry.getMetadataFor(projectionClass).entityClass());

        @SuppressWarnings({"rawtypes", "unchecked"})
        Predicate predicate = pr.resolve((Root) root, countQuery, cb);

        countQuery.select(cb.count(root));
        if (predicate != null) {
            countQuery.where(predicate);
        }

        Long count = em.createQuery(countQuery).getSingleResult();

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        logger.info(() -> String.format("Count query completed in %dms: %d matches", durationMs, count));

        return count;
    }
}

package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Objects;

/**
 * Default strategy for fetching full entities from JPA queries.
 * <p>
 * This strategy executes a standard criteria query and returns the complete entity objects,
 * without any projection or transformation. It's the simplest fetch strategy for direct
 * entity retrieval.
 * </p>
 *
 * @param <E> the entity type being queried
 * @param rootEntityClass the JPA entity class to query (must not be null)
 */
public record FullEntityFetchStrategy<E>(Class<E> rootEntityClass) implements ExecutionStrategy<List<E>> {

    @Override
    public List<E> execute(EntityManager em, PredicateResolver<?> predicateResolver, QueryExecutionParams params) {
        Objects.requireNonNull(em, "em cannot be null");
        Objects.requireNonNull(predicateResolver, "predicateResolver cannot be null");
        Objects.requireNonNull(params,  "params cannot be null");

        // 1. Build execution plan with collection options mapping
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> query = cb.createQuery(rootEntityClass);
        Root<E> root = query.from(rootEntityClass);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Predicate filterPredicate = predicateResolver.resolve((Root) root, query, cb);
        query.where(filterPredicate);

        // Execute the query as-is, returning full entities
        return em.createQuery(query).getResultList();
    }
}

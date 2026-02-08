package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.ConditionResolver;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.jpa.spi.ManagerDetail;
import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Objects;

/**
 * Default strategy for fetching full entities from JPA queries.
 * <p>
 * This strategy executes a standard criteria query and returns the complete
 * entity objects,
 * without any projection or transformation. It's the simplest fetch strategy
 * for direct
 * entity retrieval.
 * </p>
 *
 * @param <E>             the entity type being queried
 * @param rootEntityClass the JPA entity class to query (must not be null)
 */
public record FullEntityFetchStrategy<E>(Class<E> rootEntityClass) implements ExecutionStrategy<List<E>> {

    @Override
    public <Em> List<E> execute(Em emc,
                                ConditionResolver<Em, ?> cr,
                                QueryExecutionParams params) {
        if (!(emc instanceof EntityManager em)) {
            throw new IllegalArgumentException("Expected EntityManager but got: " + emc.getClass().getName());
        }


        Objects.requireNonNull(em, "em cannot be null");
        Objects.requireNonNull(cr, "condition resolver cannot be null");
        Objects.requireNonNull(params, "params cannot be null");

        // 1. Build execution plan with collection options mapping
        PredicateResolver<?> pr = PredicateResolver.from(cr);
        ManagerDetail detail = pr.resolve(rootEntityClass, em);

        detail.query().where(detail.predicate());

        // Execute the query as-is, returning full entities
        @SuppressWarnings("unchecked")
        TypedQuery<E> query = (TypedQuery<E>) em.createQuery(detail.query());

        return query.getResultList();
    }
}

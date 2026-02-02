package io.github.cyfko.filterql.jpa.strategies.helper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.Collection;
import java.util.List;

/**
 * Interface for building optimized predicates on entity IDs.
 * <p>
 * Supports different strategies based on ID structure:
 * <ul>
 * <li>Simple IDs: Uses IN clause with batching for large sets</li>
 * <li>Composite IDs: Uses OR of AND predicates</li>
 * </ul>
 * <p>
 * Implementations can be swapped for database-specific optimizations
 * (e.g., Oracle MEMBER OF, PostgreSQL ANY(array), CTEs for large sets).
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public interface IdPredicateBuilder {

    /**
     * Builds an optimized predicate for filtering by IDs.
     *
     * @param cb      JPA CriteriaBuilder
     * @param idPaths Paths to ID fields (size 1 = simple ID, size > 1 = composite)
     * @param ids     Collection of ID values (scalars for simple, List for
     *                composite)
     * @return Optimized predicate
     */
    Predicate buildIdPredicate(
            CriteriaBuilder cb,
            List<Path<?>> idPaths,
            Collection<?> ids);

    /**
     * Builds a predicate for a single ID path (convenience method).
     *
     * @param cb     JPA CriteriaBuilder
     * @param idPath Single path to ID field
     * @param ids    Collection of ID values
     * @return Optimized predicate
     */
    default Predicate buildIdPredicate(
            CriteriaBuilder cb,
            Path<?> idPath,
            Collection<?> ids) {
        return buildIdPredicate(cb, List.of(idPath), ids);
    }

    /**
     * Returns the default implementation (singleton).
     * Uses IN clause for simple IDs with batching, OR predicates for composite.
     *
     * @return default IdPredicateBuilder instance
     */
    static IdPredicateBuilder defaultBuilder() {
        return DefaultIdPredicateBuilder.INSTANCE;
    }
}

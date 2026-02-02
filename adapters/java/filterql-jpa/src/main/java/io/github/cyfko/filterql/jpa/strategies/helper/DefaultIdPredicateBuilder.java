package io.github.cyfko.filterql.jpa.strategies.helper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Default implementation of {@link IdPredicateBuilder}.
 * <p>
 * Strategy:
 * <ul>
 * <li>Simple ID, ≤500 values: WHERE id IN (...)</li>
 * <li>Simple ID, >500 values: (id IN batch1) OR (id IN batch2) ...</li>
 * <li>Composite ID: (id1=a AND id2=b) OR (id1=c AND id2=d) ...</li>
 * </ul>
 * <p>
 * This implementation is database-agnostic and works with all JDBC-compliant
 * databases.
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public final class DefaultIdPredicateBuilder implements IdPredicateBuilder {

    /**
     * Singleton instance.
     */
    public static final DefaultIdPredicateBuilder INSTANCE = new DefaultIdPredicateBuilder();

    /**
     * Maximum number of values in a single IN clause.
     * Oracle has a limit of 1000, most others are higher.
     * 500 is a safe default that works well with query planners.
     */
    public static final int MAX_IN_CLAUSE_SIZE = 500;

    private DefaultIdPredicateBuilder() {
        // Singleton
    }

    @Override
    public Predicate buildIdPredicate(
            CriteriaBuilder cb,
            List<Path<?>> idPaths,
            Collection<?> ids) {

        if (ids == null || ids.isEmpty()) {
            // No IDs = always false
            return cb.disjunction();
        }

        if (idPaths.size() == 1) {
            // Simple ID
            return buildSimpleIdPredicate(cb, idPaths.get(0), ids);
        } else {
            // Composite ID
            return buildCompositeIdPredicate(cb, idPaths, ids);
        }
    }

    /**
     * Builds predicate for simple (single-field) IDs.
     * Uses IN clause with batching for large sets.
     */
    private Predicate buildSimpleIdPredicate(
            CriteriaBuilder cb,
            Path<?> idPath,
            Collection<?> ids) {

        if (ids.size() <= MAX_IN_CLAUSE_SIZE) {
            // Simple IN clause
            return idPath.in(ids);
        } else {
            // Batch into multiple IN clauses combined with OR
            return buildBatchedInPredicate(cb, idPath, ids);
        }
    }

    /**
     * Builds batched IN predicate: (id IN batch1) OR (id IN batch2) ...
     */
    private Predicate buildBatchedInPredicate(
            CriteriaBuilder cb,
            Path<?> idPath,
            Collection<?> ids) {

        List<?> idList = ids instanceof List<?> ? (List<?>) ids : new ArrayList<>(ids);
        List<Predicate> orPredicates = new ArrayList<>();

        for (int i = 0; i < idList.size(); i += MAX_IN_CLAUSE_SIZE) {
            int end = Math.min(i + MAX_IN_CLAUSE_SIZE, idList.size());
            List<?> batch = idList.subList(i, end);
            orPredicates.add(idPath.in(batch));
        }

        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Builds predicate for composite (multi-field) IDs.
     * Uses OR of AND predicates: (id1=a AND id2=b) OR (id1=c AND id2=d)
     * This approach is more portable than tuple IN clauses.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Predicate buildCompositeIdPredicate(
            CriteriaBuilder cb,
            List<Path<?>> idPaths,
            Collection<?> compositeKeys) {

        List<Predicate> orPredicates = new ArrayList<>();

        for (Object keyObj : compositeKeys) {
            List<Object> keyValues;
            if (keyObj instanceof List) {
                keyValues = (List<Object>) keyObj;
            } else {
                // Single value passed to composite handler - wrap it
                keyValues = List.of(keyObj);
            }

            if (keyValues.size() != idPaths.size()) {
                throw new IllegalArgumentException(
                        "Composite key size mismatch: expected " + idPaths.size() +
                                " values but got " + keyValues.size());
            }

            // Build AND predicate: id1 = value1 AND id2 = value2 ...
            List<Predicate> andPredicates = new ArrayList<>(idPaths.size());
            for (int i = 0; i < idPaths.size(); i++) {
                Path path = idPaths.get(i);
                Object value = keyValues.get(i);
                andPredicates.add(cb.equal(path, value));
            }

            orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
        }

        if (orPredicates.isEmpty()) {
            return cb.disjunction(); // Always false
        }

        return cb.or(orPredicates.toArray(new Predicate[0]));
    }
}

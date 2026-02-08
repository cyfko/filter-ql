package io.github.cyfko.filterql.jpa.spi;

import io.github.cyfko.filterql.core.spi.ConditionResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.Objects;

/**
 * Functional interface for deferred JPA predicate generation.
 * <p>
 * This interface provides a callback mechanism for constructing JPA Criteria
 * API predicates
 * in a type-safe, deferred manner. The predicate is not built immediately upon
 * filter parsing,
 * but only when the query is actually executed and the JPA context (Root,
 * CriteriaQuery,
 * CriteriaBuilder) is available.
 * </p>
 *
 * <h2>Why Deferred Resolution?</h2>
 * <p>
 * FilterQL parses filter expressions into an abstract tree before any JPA
 * context exists.
 * This interface bridges that gap by deferring the actual predicate
 * construction to execution time.
 * </p>
 *
 * <h2>Type Safety</h2>
 * <p>
 * The generic type parameter {@code E} ensures compile-time verification that
 * the resolver
 * is used with the correct entity type. This prevents runtime errors from
 * mismatched entity types.
 * </p>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Create a resolver for filtering active users
 * PredicateResolver<User> activeUsers = (root, query, cb) -> cb.equal(root.get("status"), UserStatus.ACTIVE);
 *
 * // Use in a criteria query
 * CriteriaBuilder cb = entityManager.getCriteriaBuilder();
 * CriteriaQuery<User> query = cb.createQuery(User.class);
 * Root<User> root = query.from(User.class);
 * Predicate predicate = activeUsers.resolve(root, query, cb);
 * query.where(predicate);
 * }</pre>
 *
 * @param <E> the JPA entity type this resolver operates on
 * @author Frank KOSSI
 * @since 4.0.0
 * @see Predicate
 * @see CriteriaBuilder
 */
@FunctionalInterface
public interface PredicateResolver<E> extends ConditionResolver<EntityManager, CriteriaBundle> {

    /**
     * Resolves a JPA {@link Predicate} using the provided JPA Criteria API context.
     * <p>
     * This method is called during query execution when the JPA context becomes
     * available.
     * Implementations should use the provided {@link Root}, {@link CriteriaQuery},
     * and
     * {@link CriteriaBuilder} to construct the appropriate predicate.
     * </p>
     *
     * @param root  the query root representing the entity being queried
     * @param query the criteria query being constructed
     * @param cb    the criteria builder for creating predicates
     * @return the constructed predicate, or {@code cb.conjunction()} for an
     *         always-true condition
     */
    Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb);

    /**
     * Resolves this condition into a JPA {@link Predicate}.
     * <p>
     * This method creates a criteria query for the given entity class and uses the
     * underlying {@link PredicateResolver} to generate the predicate.
     * </p>
     *
     * @param subject the entity class to query (must match the resolver's entity
     *                type)
     * @param em      the JPA EntityManager providing the criteria builder
     * @param <T>     the entity type
     * @return the resolved JPA Predicate
     * @throws ClassCastException if subject doesn't match the resolver's entity
     *                            type
     */
    @Override
    default <T> CriteriaBundle resolve(Class<T> subject, EntityManager em) {
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(em, "EntityManager cannot be null");

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(subject);
        Root<T> root = query.from(subject);

        @SuppressWarnings("unchecked")
        Predicate predicate = resolve((Root<E>) root, query, cb);

        return new CriteriaBundle(predicate, query, cb, root);
    }

    /**
     * Extracts a {@link PredicateResolver} from a generic
     * {@link ConditionResolver}.
     * <p>
     * This utility method provides type-safe extraction of JPA-specific resolvers
     * from
     * the backend-agnostic {@link ConditionResolver} interface. It handles three
     * cases:
     * </p>
     * <ul>
     * <li>If the resolver is already a {@link PredicateResolver}, it is returned
     * as-is</li>
     * <li>If the resolver is the {@link ConditionResolver#SENTINEL} marker, a no-op
     * resolver returning {@code cb.conjunction()} (always true) is returned</li>
     * <li>Otherwise, an {@link IllegalArgumentException} is thrown</li>
     * </ul>
     *
     * <p>
     * <strong>Usage example:</strong>
     * </p>
     * 
     * <pre>{@code
     * // In strategy execute() method:
     * PredicateResolver<?> pr = PredicateResolver.from(conditionResolver);
     * 
     * // Resolve to CriteriaBundle (contains predicate, query, cb, root)
     * CriteriaBundle bundle = pr.resolve(User.class, entityManager);
     * 
     * // Use the bundle components
     * bundle.query().where(bundle.predicate());
     * List<User> results = entityManager.createQuery(bundle.query()).getResultList();
     * }</pre>
     *
     * @param cr the condition resolver to extract from (must not be null)
     * @return the extracted {@link PredicateResolver}, never null
     * @throws IllegalArgumentException if cr is not a PredicateResolver and not
     *                                  SENTINEL
     */
    static PredicateResolver<?> from(ConditionResolver<?, ?> cr) {
        if (cr instanceof PredicateResolver<?> pr_) {
            return pr_;
        } else if (cr == ConditionResolver.SENTINEL) {
            return ((root, query, cb) -> cb.conjunction());
        }

        throw new IllegalArgumentException(
                "Expected PredicateResolver which implement ConditionResolver but got: " + cr.getClass().getName());
    }
}

package io.github.cyfko.filterql.core.spi;

/**
 * Backend-agnostic condition resolver that transforms filter conditions
 * into backend-specific query predicates.
 * <p>
 * This interface serves as the bridge between the abstract filter
 * representation
 * and concrete query technologies (JPA, MongoDB, Elasticsearch, etc.).
 * </p>
 *
 * <h2>Design Principles</h2>
 * <ul>
 * <li><strong>Backend Agnostic:</strong> The core module has no dependency on
 * any
 * specific query technology</li>
 * <li><strong>Type-Safe:</strong> Generic parameters ensure compile-time
 * validation</li>
 * <li><strong>Deferred Execution:</strong> Predicates are generated on-demand
 * when
 * {@link #resolve} is called</li>
 * <li><strong>Stateless:</strong> Implementations should be immutable and
 * thread-safe</li>
 * </ul>
 *
 * @param <Em> Execution management context type - implementation-specific
 *             (e.g., DatabaseSession)
 * @param <R>  Result type - backend-specific predicate type
 *             (e.g., javax.persistence.criteria.Predicate,
 *             org.bson.conversions.Bson)
 *
 * @author Frank KOSSI
 * @since 5.0.0
 */
@FunctionalInterface
public interface ConditionResolver<Em, R> {

    /**
     * A resolver that returns null (backend <strong>SHOULD</strong> interprets as
     * no defined filter condition)
     */
    ConditionResolver<Object, Object> SENTINEL = new ConditionResolver<>() {
        @Override
        public <E> Object resolve(Class<E> subject, Object o) {
            return null;
        }
    };

    /**
     * Resolves filter conditions into a backend-specific predicate.
     * <p>
     * This method transforms the abstract filter representation (captured during
     * resolver construction) into a concrete predicate that can be used in query
     * execution.
     * </p>
     *
     * <p>
     * <strong>Implementation Guidelines:</strong>
     * </p>
     * <ul>
     * <li>Method should be stateless and thread-safe</li>
     * <li>Use the provided context for predicate construction</li>
     * <li>Handle null values and edge cases gracefully</li>
     * <li>The subject class provides the entity type for type-safe path access</li>
     * </ul>
     *
     * @param subject the entity class being queried (e.g., User.class)
     * @param em      implementation-specific execution management context
     *                containing
     *                necessary components
     * @param <E>     entity type
     * @return backend-specific predicate ready for query execution
     * @throws IllegalArgumentException if resolver cannot create a predicate
     */
    <E> R resolve(Class<E> subject, Em em);
}

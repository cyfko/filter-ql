package io.github.cyfko.filterql.jpa;


import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.spi.PredicateResolver;


/**
 * JPA Criteria API implementation of the {@link Condition} interface.
 * <p>
 * This record class wraps a {@link PredicateResolver} to provide a composable,
 * immutable interface for building complex filter conditions. It serves as the
 * bridge between FilterQL's abstract condition model and JPA's concrete predicate system.
 * </p>
 *
 * <h2>Core Concepts</h2>
 * <p>
 * {@code JpaCondition} is built on the following principles:
 * </p>
 * <ul>
 *   <li><strong>Immutability:</strong> All operations return new instances, original conditions remain unchanged</li>
 *   <li><strong>Composability:</strong> Conditions can be combined using {@code and()}, {@code or()}, and {@code not()}</li>
 *   <li><strong>Type Safety:</strong> Generic type parameter ensures compile-time entity type checking</li>
 *   <li><strong>Lazy Evaluation:</strong> Predicates are constructed only when the query executes</li>
 * </ul>
 *
 * <h2>Boolean Logic Support</h2>
 * <p>The class supports standard boolean operations:</p>
 * <ul>
 *   <li><strong>AND:</strong> {@link #and(Condition)} - Logical conjunction</li>
 *   <li><strong>OR:</strong> {@link #or(Condition)} - Logical disjunction</li>
 *   <li><strong>NOT:</strong> {@link #not()} - Logical negation</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Condition Creation</h3>
 * <pre>{@code
 * // Create a simple resolver
 * PredicateResolver<User> activeResolver = (root, query, cb) ->
 *     cb.equal(root.get("status"), Status.ACTIVE);
 *
 * // Wrap in JpaCondition
 * JpaCondition<User> activeCondition = new JpaCondition<>(activeResolver);
 * }</pre>
 *
 * <h3>Combining Conditions with AND</h3>
 * <pre>{@code
 * PredicateResolver<User> nameResolver = (root, query, cb) ->
 *     cb.like(root.get("name"), "John%");
 *
 * PredicateResolver<User> ageResolver = (root, query, cb) ->
 *     cb.greaterThan(root.get("age"), 25);
 *
 * JpaCondition<User> nameCondition = new JpaCondition<>(nameResolver);
 * JpaCondition<User> ageCondition = new JpaCondition<>(ageResolver);
 *
 * // Combine: name LIKE 'John%' AND age > 25
 * Condition combined = nameCondition.and(ageCondition);
 * }</pre>
 *
 * <h3>Complex Boolean Logic</h3>
 * <pre>{@code
 * JpaCondition<User> activeCondition = new JpaCondition<>(activeResolver);
 * JpaCondition<User> premiumCondition = new JpaCondition<>(premiumResolver);
 * JpaCondition<User> deletedCondition = new JpaCondition<>(deletedResolver);
 *
 * // Build: (active OR premium) AND NOT deleted
 * Condition result = activeCondition
 *     .or(premiumCondition)
 *     .and(deletedCondition.not());
 *
 * // Equivalent SQL: (status = 'ACTIVE' OR membership = 'PREMIUM') AND deleted = false
 * }</pre>
 *
 * <h3>Integration with FilterContext</h3>
 * <pre>{@code
 * // Typical usage with FilterContext
 * JpaFilterContext<User, UserProperty> context = ...;
 *
 * context.addCondition("name", UserProperty.NAME, "eq");
 * context.addCondition("minAge", UserProperty.AGE, "gte");
 *
 * Condition nameCondition = context.getCondition("name");
 * Condition ageCondition = context.getCondition("minAge");
 *
 * // Combine and resolve
 * Condition finalCondition = nameCondition.and(ageCondition);
 * PredicateResolver<User> resolver = context.toResolver(argumentMap);
 * }</pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Memory:</strong> Lightweight - only stores resolver reference</li>
 *   <li><strong>Composition:</strong> O(1) for all boolean operations (no deep copying)</li>
 *   <li><strong>Evaluation:</strong> Deferred until JPA query execution</li>
 *   <li><strong>Thread Safety:</strong> Fully thread-safe due to immutability</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * {@code JpaCondition} is immutable and thread-safe. All combination operations
 * ({@code and()}, {@code or()}, {@code not()}) return new instances without
 * modifying the original condition. This makes it safe to share condition instances
 * across multiple threads and requests.
 * </p>
 *
 * <h2>Implementation Notes</h2>
 * <p>
 * This class is implemented as a Java record, providing:
 * </p>
 * <ul>
 *   <li>Automatic implementation of {@code equals()}, {@code hashCode()}, and {@code toString()}</li>
 *   <li>Immutability by design</li>
 *   <li>Compact syntax and reduced boilerplate</li>
 * </ul>
 *
 * @param <T> The JPA entity type this condition filters (e.g., User, Product, Order)
 * @param resolver The underlying {@link PredicateResolver} that generates JPA predicates
 * 
 * @since 2.0.0
 * @see Condition
 * @see PredicateResolver
 * @see JpaFilterContext
 */
public record JpaCondition<T>(PredicateResolver<T> resolver) implements Condition {
    /**
     * Constructs a new FilterCondition wrapping a PredicateResolver.
     * <p>
     * The provided resolver will be used to generate JPA predicates
     * when this condition is evaluated in a query context.
     * </p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * // Create a PredicateResolver for active users
     * PredicateResolver<User> activeSpec = (root, query, cb) ->
     *     cb.equal(root.get("status"), UserStatus.ACTIVE);
     *
     * // Wrap in FilterCondition
     * FilterCondition<User> activeCondition = new FilterCondition<>(activeSpec);
     * }</pre>
     *
     * @param resolver The PredicateResolver to wrap. Must not be null.
     * @throws NullPointerException if resolver is {@code null}
     */
    public JpaCondition {}

    /**
     * {@inheritDoc}
     *
     * @param other The other condition to combine with
     * @return A new condition representing the AND combination
     * @throws IllegalArgumentException if the other condition is not a JPA condition
     */
    @Override
    public Condition and(Condition other) {
        if (!(other instanceof JpaCondition<?>)) {
            throw new IllegalArgumentException("Cannot combine with non-JPA condition");
        }

        @SuppressWarnings("unchecked")
        JpaCondition<T> otherCond = (JpaCondition<T>) other;

        PredicateResolver<T> andSpec = (r, q, cb) -> cb.and(
                this.resolver.resolve(r, q, cb),
                otherCond.resolver.resolve(r, q, cb)
        );

        return new JpaCondition<>(andSpec);
    }

    /**
     * {@inheritDoc}
     *
     * @param other The other condition to combine with
     * @return A new condition representing the OR combination
     * @throws IllegalArgumentException if the other condition is not a JPA condition
     */
    @Override
    public Condition or(Condition other) {
        if (!(other instanceof JpaCondition<?>)) {
            throw new IllegalArgumentException("Cannot combine with non-JPA condition");
        }

        @SuppressWarnings("unchecked")
        JpaCondition<T> otherCond = (JpaCondition<T>) other;
        PredicateResolver<T> orSpec = (r, q, cb) -> cb.or(
                this.resolver.resolve(r, q, cb),
                otherCond.resolver.resolve(r, q, cb)
        );

        return new JpaCondition<>(orSpec);
    }

    /**
     * {@inheritDoc}
     *
     * @return A new condition representing the negation of this condition
     */
    @Override
    public Condition not() {
        PredicateResolver<T> notSpec = (r, q, cb) -> cb.not(this.resolver.resolve(r, q, cb));
        return new JpaCondition<>(notSpec);
    }

    /**
     * Gets the underlying PredicateResolver.
     * <p>
     * Returns the wrapped PredicateResolver that can be used directly with criteria queries.
     * </p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * // use with criteria API
     * CriteriaBuilder cb = entityManager.getCriteriaBuilder();
     * CriteriaQuery<User> query = cb.createQuery(User.class);
     * Root<User> root = query.from(User.class);
     * query.where(resolver.resolve(root, query, cb));
     * }</pre>
     *
     * @return The PredicateResolver wrapped by this condition
     */
    public PredicateResolver<T> getResolver() {
        return resolver;
    }
}





package io.github.cyfko.filterql.jpa.projection;

import io.github.cyfko.filterql.jpa.exception.InstanceResolutionException;

/**
 * Strategy interface for resolving provider instances from an IoC (Inversion of Control) container.
 * <p>
 * This interface enables the projection system to remain agnostic of the underlying IoC framework
 * (such as Spring, Quarkus, or CDI) while still supporting dependency injection for various providers
 * including computed field calculators, virtual field resolvers, validators, and transformers.
 * Implementations can be provided by specific container adapters to bridge the projection layer
 * with the chosen DI framework.
 * </p>
 *
 * <p>
 * This is a functional interface whose functional method is {@link #resolve(Class, String)}, making it
 * compatible with lambda expressions and method references.
 * </p>
 *
 * <h3>Resolution Rules</h3>
 * <ul>
 *   <li>If {@code beanName} is empty or null, the resolver should attempt to find a bean by type only.</li>
 *   <li>If {@code beanName} is provided, the resolver should attempt to find a bean by both type and name.</li>
 *   <li>If no matching bean is found, the resolver <b>should return {@code null}</b> to indicate that
 *       static method resolution should be attempted.</li>
 *   <li>If bean resolution fails critically (e.g., circular dependency, initialization error),
 *       throw {@link InstanceResolutionException}.</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>{@code
 * // Direct usage
 * InstanceResolver resolver = ...;
 * DateFormatter formatter = resolver.resolve(DateFormatter.class, "");
 * DateFormatter isoFormatter = resolver.resolve(DateFormatter.class, "isoFormatter");
 *
 * // With ProjectionUtils
 * Object result = ProjectionUtils.computeField(
 *     resolver,
 *     UserDTO.class,
 *     "fullName",
 *     firstName, lastName
 * );
 *
 * // Static resolution only (no IoC)
 * InstanceResolver staticOnly = InstanceResolver.noBean();
 * Object result = ProjectionUtils.invoke(
 *     staticOnly,
 *     UserComputations.class,
 *     "",
 *     "getFullName",
 *     firstName, lastName
 * );
 * }</pre>
 *
 * <h3>Implementation Guidelines</h3>
 * <p>When implementing this interface for a specific IoC framework:</p>
 * <ul>
 *   <li><b>Return {@code null}</b> when the bean is not found (to allow static fallback)</li>
 *   <li><b>Throw {@link InstanceResolutionException}</b> for critical errors (circular deps, initialization failures)</li>
 *   <li><b>Log warnings</b> (optional) when bean lookup fails to help debugging</li>
 *   <li><b>Cache resolutions</b> (optional) for performance in high-throughput scenarios</li>
 * </ul>
 *
 * @since 2.0.0
 * @see io.github.cyfko.filterql.jpa.utils.ProjectionUtils#computeField(InstanceResolver, Class, String, Object...)
 * @see io.github.cyfko.filterql.jpa.utils.ProjectionUtils#invoke(InstanceResolver, Class, String, String, Object...)
 */
@FunctionalInterface
public interface InstanceResolver {

    /**
     * Resolves a provider instance from the IoC container.
     * <p>
     * The resolution process is:
     * </p>
     * <ol>
     *   <li>If {@code beanName} is empty or null, attempt to find a bean by type only.</li>
     *   <li>If {@code beanName} is provided, attempt to find a bean by both type and name.</li>
     *   <li>If no matching bean is found, return {@code null} to signal static method fallback.</li>
     *   <li>If bean resolution fails critically (e.g., circular dependency), throw {@link InstanceResolutionException}.</li>
     * </ol>
     *
     * <h4>Return Value Semantics</h4>
     * <ul>
     *   <li><b>{@code null}:</b> Bean not found, caller should try static method resolution</li>
     *   <li><b>Non-null:</b> Bean instance successfully resolved, use for instance method invocation</li>
     *   <li><b>Exception:</b> Critical failure during resolution (circular deps, initialization errors)</li>
     * </ul>
     *
     * @param clazz    the class of the instance to resolve
     * @param beanName the bean name for disambiguation (empty string or null for type-only lookup)
     * @param <T>      the type of the provider instance to resolve
     * @return the resolved provider instance, or {@code null} if not found (to allow static fallback)
     * @throws InstanceResolutionException if bean resolution fails critically (not for "bean not found" cases)
     */
    <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException;

    /**
     * Returns a resolver that always returns {@code null}, indicating no beans are available.
     * <p>
     * This signals that all providers should use static methods exclusively.
     * Useful when:
     * </p>
     * <ul>
     *   <li>No IoC container is available in the runtime environment</li>
     *   <li>Static methods are explicitly preferred for all computations</li>
     *   <li>Testing scenarios where bean injection is not needed</li>
     * </ul>
     *
     * @return an {@code InstanceResolver} that always returns {@code null}
     */
    static InstanceResolver noBean() {
        return new InstanceResolver() {
            @Override
            public <T> T resolve(Class<T> computerClass, String beanName) { return null; }
        };
    }
}
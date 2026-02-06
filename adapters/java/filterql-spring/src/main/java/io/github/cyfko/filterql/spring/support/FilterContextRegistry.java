package io.github.cyfko.filterql.spring.support;

import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for all automatically generated {@link JpaFilterContext} instances in the FilterQL Spring Boot starter.
 * <p>
 * Enables retrieval of the appropriate filter context for a given {@link io.github.cyfko.filterql.core.validation.PropertyReference},
 * supporting type-safe query construction and validation. Populated at startup via dependency injection.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Spring component: auto-wired into services/controllers needing dynamic filtering</li>
 *   <li>Provides fast, thread-safe lookup of filter contexts for {@code PropertyReference} enum classes</li>
 *   <li>Ensures only entities annotated with {@link io.github.cyfko.projection.Projection} are registered</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * The registry is safe for concurrent reads after initialization. Registration occurs only at construction.
 * </p>
 *
 * <h2>Extension Points</h2>
 * <ul>
 *   <li>Custom registration logic via constructor injection</li>
 *   <li>Override for advanced multi-tenancy or dynamic context scenarios</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
@Component
public class FilterContextRegistry {

    private final Map<Class<?>, JpaFilterContext<?>> contextByEnum;

    /**
     * Constructs the registry from a list of {@link JpaFilterContext} instances.
     * <p>
     * Each context is mapped by its entity class for fast lookup.
     * </p>
     *
     * @param contexts list of filter contexts to register
     */
    public FilterContextRegistry(List<JpaFilterContext<?>> contexts) {
        this.contextByEnum = new HashMap<>();
        for (JpaFilterContext<?> context : contexts) {
            contextByEnum.put(context.getPropertyRefClass(), context);
        }
    }

    /**
     * Retrieves the {@link JpaFilterContext} for the specified enum class.
     *
     * @param enumClass enum class to look up
     * @param <P> enum type
     * @return filter context for the entity
     * @throws IllegalArgumentException if no context is registered for the entity
     */
    public <P extends Enum<P> & PropertyReference> JpaFilterContext<?> getContext(Class<P> enumClass) {
        JpaFilterContext<?> context = contextByEnum.get(enumClass);
        if (context == null) {
            throw new IllegalArgumentException(
                    "No JpaFilterContext found for reference " + enumClass.getName() + ". " +
                            "Ensure @Projection is used."
            );
        }
        return context;
    }

    /**
     * Checks if a {@link JpaFilterContext} is registered for the given enum class.
     *
     * @param enumClass enum class to check
     * @return {@code true} if a context is registered, {@code false} otherwise
     */
    public boolean hasContext(Class<?> enumClass) {
        return contextByEnum.containsKey(enumClass);
    }
}

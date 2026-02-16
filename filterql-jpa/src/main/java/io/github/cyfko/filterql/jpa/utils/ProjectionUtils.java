package io.github.cyfko.filterql.jpa.utils;

import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.jpametamodel.PersistenceRegistry;
import io.github.cyfko.jpametamodel.ProjectionRegistry;
import io.github.cyfko.jpametamodel.api.ComputationProvider;
import io.github.cyfko.jpametamodel.api.ComputedField;
import io.github.cyfko.jpametamodel.api.ProjectionMetadata;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Utility methods for working with projections and computed fields at runtime.
 * <p>
 * This class provides helper operations that complement the generated projection metadata,
 * such as delegating the execution of computed fields to their configured computation providers
 * and generic string utilities used across the projection layer.
 * </p>
 *
 * <p>
 * All methods are static and side effect free, making this class safe to use from any
 * projection execution context.
 * </p>
 *
 * @author  Frank KOSSI
 * @since   2.0.0
 */
public abstract class ProjectionUtils {

    private ProjectionUtils() {}

    /**
     * General-purpose method to invoke a method on either a resolved instance or statically
     * on the target class.
     * <p>
     * The invocation strategy follows these rules:
     * </p>
     * <ul>
     *   <li>Locates a public method matching the given name and argument types.</li>
     *   <li>Attempts to resolve an instance using the provided {@code instanceResolver}.</li>
     *   <li>If the resolver returns {@code null}, invokes the method statically on the class.</li>
     *   <li>Otherwise, invokes the method on the resolved instance.</li>
     * </ul>
     *
     * @param instanceResolver the resolver used to obtain computation provider instances (must not be null)
     * @param clazz            the class containing the target method (must not be null)
     * @param beanName         the bean name for instance resolution (may be empty or null)
     * @param methodName       the name of the method to invoke (must not be null)
     * @param args             the method arguments (may be empty)
     * @return the result of the method invocation
     * @throws NullPointerException if {@code instanceResolver}, {@code clazz}, or {@code methodName} is null
     * @throws UnsupportedOperationException if no matching public method with compatible parameter types is found
     * @throws RuntimeException wrapping {@link IllegalAccessException}, {@link java.lang.reflect.InvocationTargetException},
     *                          or other reflection-related failures, including exceptions thrown by the invoked method
     */
    public static Object invoke(InstanceResolver instanceResolver,
                                Class<?> clazz,
                                String beanName,
                                String methodName,
                                Object... args) {

        Objects.requireNonNull(instanceResolver,  "providerResolver is null");
        Objects.requireNonNull(clazz,  "clazz is null");
        Objects.requireNonNull(methodName,  "methodName is null");

        Method method = ReflectionUtils.findMethod(clazz, methodName, args);
        if (method == null) {
            throw new UnsupportedOperationException(
                    String.format("No matching method found: %s.%s(%s)",
                            clazz.getName(),
                            methodName,
                            formatArgumentTypes(args))
            );
        }

        Object computerInstance = instanceResolver.resolve(clazz, beanName);
        try {
            if (computerInstance == null) {
                // Static invocation
                return method.invoke(null, args);
            } else {
                // Instance invocation
                return method.invoke(computerInstance, args);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes the value of a projection field declared as <em>computed</em> using the registered
     * computation providers associated with the given projection class.
     * <p>
     * This method implements the full resolution strategy for computed fields, including:
     * </p>
     * <ul>
     *   <li>Loading projection metadata and validating the field</li>
     *   <li>Iterating through registered providers in declaration order</li>
     *   <li>First-match-wins resolution across multiple providers</li>
     *   <li>Automatic fallback from instance to static methods when beans are unavailable</li>
     * </ul>
     *
     * <h3>Resolution Process</h3>
     * <ol>
     *   <li>Load {@link ProjectionMetadata} for the given projection class.</li>
     *   <li>Verify that the specified field is declared as a computed field.</li>
     *   <li>Derive the expected method name using the {@code get[FieldName]} convention.</li>
     *   <li>Iterate over all registered computation providers for this projection.</li>
     *   <li>For each provider, attempt to locate and invoke a compatible method:
     *     <ul>
     *       <li>If {@code instanceResolver} returns {@code null}, the method is invoked statically.</li>
     *       <li>If {@code instanceResolver} returns an instance, the method is invoked on that instance.</li>
     *     </ul>
     *   </li>
     *   <li>Return the first successfully computed result (first-match-wins).</li>
     * </ol>
     *
     * <h3>Provider Resolution Strategy</h3>
     * <p>Providers are evaluated in the order they are declared in {@code @Projection(providers = {...})}.
     * The first provider that has a matching method signature will be used. This allows for:</p>
     * <ul>
     *   <li>Provider precedence (specific → general)</li>
     *   <li>Graceful fallback when methods are not found</li>
     *   <li>Testing overrides by declaring test providers first</li>
     * </ul>
     *
     * <h3>Example Usage</h3>
     * <pre>{@code
     * // With IoC resolver (Spring, Quarkus, etc.)
     * InstanceResolver resolver = ... // Spring/Quarkus implementation
     * Object fullName = ProjectionUtils.computeField(
     *     resolver,
     *     UserDTO.class,
     *     "fullName",
     *     "John", "Doe"
     * );
     *
     * // With static-only resolution
     * InstanceResolver staticOnly = InstanceResolver.noBean();
     * Object age = ProjectionUtils.computeField(
     *     staticOnly,
     *     UserDTO.class,
     *     "age",
     *     LocalDate.of(1990, 1, 1)
     * );
     * }</pre>
     *
     * @param instanceResolver a resolver that, given a provider class and bean name, returns a
     *                         concrete provider instance or {@code null} to indicate static method invocation
     * @param projectionClazz  the projection class declaring the computed field
     * @param field            the logical name of the computed field, without the {@code get} prefix
     * @param dependencies     ordered dependency values that will be passed as arguments to the
     *                         computation method; the runtime types must match the method signature
     * @return the computed field value as returned by the resolved provider method
     * @throws NullPointerException     if {@code instanceResolver} or {@code projectionClazz} is {@code null}
     * @throws IllegalArgumentException if no projection metadata is found for the class,
     *                                  or if the field is not declared as a computed field
     * @throws IllegalStateException    if no suitable provider method (static or instance) can be found
     *                                  across all registered providers
     * @throws Exception                if the reflective invocation fails or execution throws an exception
     */
    public static Object computeField(InstanceResolver instanceResolver,
                                      Class<?> projectionClazz,
                                      String field,
                                      Object... dependencies) throws Exception {
        return resolveComputeMethod(instanceResolver, projectionClazz, field)
                .apply(dependencies);
    }

    public static Function<Object[], Object> resolveComputeMethod(
            InstanceResolver instanceResolver,
            Class<?> projectionClazz,
            String field) throws Exception {

        Objects.requireNonNull(instanceResolver,  "providerResolver is null");
        final ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(projectionClazz);

        if (metadata == null) {
            throw new IllegalArgumentException("The supposed projection class is not a projection nor an entity: " + projectionClazz.getSimpleName());
        }

        final ComputedField computedField = metadata.getComputedField(field, true)
                .orElseThrow(() -> new IllegalArgumentException("No computed field found with the given name: " + field));

        final ComputedField.MethodReference computedBy = computedField.computedBy();

        // Lookup method immediately
        final Class<?>[] parameterTypes = getParameterTypes(computedField.dependencies(), metadata.entityClass());
        final Method method = computedBy.owner().getMethod(computedBy.methodName(), parameterTypes);

        // resolve instance if method is not static
        Object computerInstance;
        if (method.getModifiers() != Modifier.STATIC) {
            String beanName = Arrays.stream(metadata.computers())
                    .filter(p -> computedBy.owner().equals(p.clazz()))
                    .findFirst()
                    .map(ComputationProvider::bean)
                    .orElse(null);

            computerInstance = instanceResolver.resolve(computedBy.owner(), beanName);
        } else {
            computerInstance = null;
        }

        // If transformation is not needed return this handler
        ComputedField.MethodReference transformer = computedField.transformer();
        if (transformer == null){
            return (args) -> {
                try {
                    return method.invoke(computerInstance, args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }

        // Obtain transformer and apply transformation
        final Method transformM = transformer.owner().getMethod(transformer.methodName(), method.getReturnType());
        return (args) -> {
            try {
                Object result = method.invoke(computerInstance, args);
                return transformM.invoke(null, result);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static Class<?>[] getParameterTypes(String[] paramNames, Class<?> targetEntity) {
        final Class<?>[] parameterTypes = new Class<?>[paramNames.length];
        for (int i=0; i < paramNames.length; i++) {
            parameterTypes[i] = PersistenceRegistry.getFieldType(targetEntity, paramNames[i]);
        }
        return parameterTypes;
    }

    /**
     * Capitalizes the first character of the given string, leaving the remainder unchanged.
     * <p>
     * This method is null-safe and returns the input as-is when the string is {@code null}
     * or empty. It is typically used to build JavaBean-style accessor names from field
     * identifiers, for example when resolving {@code getXxx} methods via reflection.
     * </p>
     *
     * <h4>Examples:</h4>
     * <ul>
     *   <li>{@code capitalize("fullName")} → {@code "FullName"}</li>
     *   <li>{@code capitalize("age")} → {@code "Age"}</li>
     *   <li>{@code capitalize("")} → {@code ""}</li>
     *   <li>{@code capitalize(null)} → {@code null}</li>
     * </ul>
     *
     * @param str the input string, possibly {@code null} or empty
     * @return the capitalized string, or the original value if {@code null} or empty
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Formats argument types for error messages.
     *
     * @param args the arguments
     * @return a comma-separated string of type names
     */
    private static String formatArgumentTypes(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i] != null ? args[i].getClass().getSimpleName() : "null");
        }
        return sb.toString();
    }

    /**
     * Formats provider list for error messages.
     *
     * @param providers the computation providers
     * @return a comma-separated string of provider class names
     */
    private static String formatProviderList(Object[] providers) {
        if (providers == null || providers.length == 0) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < providers.length; i++) {
            if (i > 0) sb.append(", ");
            // Assuming providers have a clazz() or similar method
            sb.append(providers[i].toString());
        }
        return sb.toString();
    }

    private static String pascalToCamel(String pascal) {
        return pascal.substring(0, 1).toLowerCase() + pascal.substring(1);
    }


}
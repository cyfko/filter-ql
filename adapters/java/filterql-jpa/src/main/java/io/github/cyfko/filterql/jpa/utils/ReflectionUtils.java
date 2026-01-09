package io.github.cyfko.filterql.jpa.utils;

import java.lang.reflect.Method;

/**
 * Reflection utility methods for resolving methods by name and argument types.
 *
 * <p>
 * Provides a type-compatible lookup facility that supports primitive–wrapper
 * matching and {@code null} arguments, making reflective invocations more robust
 * than simple {@link Class#getMethod(String, Class[])} lookups. [web:22][web:23][web:7]
 * </p>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public class ReflectionUtils {

    /**
     * Finds the first public method on the given class that matches the specified
     * name and is compatible with the provided argument values.
     *
     * <p>
     * Compatibility rules:
     * </p>
     * <ul>
     *   <li>Method name must exactly match {@code methodName}.</li>
     *   <li>Parameter count must equal {@code args.length}.</li>
     *   <li>{@code null} arguments are allowed only for non-primitive parameters.</li>
     *   <li>Wrapper types are accepted for primitive parameters
     *       (e.g. {@code Integer} for {@code int}).</li>
     *   <li>Non-primitive parameters use {@link Class#isAssignableFrom(Class)}.</li>
     * </ul>
     *
     * <p>
     * If multiple methods are compatible, the first one encountered in
     * {@link Class#getMethods()} order is returned.
     * </p>
     *
     * @param clazz      target class to inspect; must not be {@code null}
     * @param methodName name of the method to resolve; must not be {@code null}
     * @param args       argument values used for compatibility checks;
     *                   may contain {@code null} elements but must not be {@code null} as an array
     * @return the first compatible {@link Method}, or {@code null} if none matches
     *
     * @throws NullPointerException if {@code clazz}, {@code methodName}, or {@code args} is {@code null}
     *
     * @example
     * <pre>{@code
     * Method m = ReflectionUtils.findMethod(
     *     MyService.class,
     *     "process",
     *     new Object[]{ 42, "payload" }
     * );
     * if (m != null) {
     *     m.invoke(serviceInstance, 42, "payload");
     * }
     * }</pre>
     */
    public static Method findMethod(Class<?> clazz, String methodName, Object[] args) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                Class<?>[] paramTypes = m.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < args.length; i++) {
                    if (!isCompatible(paramTypes[i], args[i])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    return m; // first compatible method found
                }
            }
        }
        return null;
    }

    /**
     * Determines whether the given runtime argument value is compatible with
     * the specified parameter type.
     *
     * <p>
     * Handles:
     * </p>
     * <ul>
     *   <li>{@code null} arguments: only allowed for non-primitive parameters.</li>
     *   <li>Primitive–wrapper matching (e.g. {@code int} ↔ {@code Integer}).</li>
     *   <li>Standard assignability checks for reference types via
     *       {@link Class#isAssignableFrom(Class)}.</li>
     * </ul>
     *
     * @param paramType target parameter type from method signature
     * @param arg       runtime argument value (may be {@code null})
     * @return {@code true} if the argument can be passed to the parameter,
     *         {@code false} otherwise
     */
    private static boolean isCompatible(Class<?> paramType, Object arg) {
        if (arg == null) {
            // null cannot be assigned to primitive parameters
            return !paramType.isPrimitive();
        }
        Class<?> argClass = arg.getClass();

        // Primitive ↔ wrapper compatibility
        if (paramType.isPrimitive()) {
            if (paramType == int.class && argClass == Integer.class) return true;
            if (paramType == long.class && argClass == Long.class) return true;
            if (paramType == boolean.class && argClass == Boolean.class) return true;
            if (paramType == double.class && argClass == Double.class) return true;
            if (paramType == float.class && argClass == Float.class) return true;
            if (paramType == short.class && argClass == Short.class) return true;
            if (paramType == byte.class && argClass == Byte.class) return true;
            if (paramType == char.class && argClass == Character.class) return true;
            return false;
        }

        // Standard assignability for reference types
        return paramType.isAssignableFrom(argClass);
    }
}

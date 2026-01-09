package io.github.cyfko.filterql.spring.util;

import java.util.Locale;

/**
 * Utility class for common string transformations used in the FilterQL Spring Boot starter.
 * <p>
 * Provides methods for class name extraction and case conversions (PascalCase, camelCase, kebab-case)
 * to facilitate consistent naming conventions across generated code, metadata, and API endpoints.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Code generation (annotation processors, metadata registries)</li>
 *   <li>API schema exposure (naming of fields and entities)</li>
 *   <li>Internal normalization for property references</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All methods are stateless and thread-safe.
 * </p>
 *
 * @author cyfko
 * @since 1.0
 */
public class StringUtils {
    /**
     * Extracts the simple class name from a fully qualified class name.
     * <p>
     * For example, {@code "com.example.MyEntity"} yields {@code "MyEntity"}.
     * </p>
     *
     * @param fullClassName fully qualified class name (e.g., {@code "com.example.MyEntity"})
     * @return simple class name (e.g., {@code "MyEntity"})
     * @throws NullPointerException if {@code fullClassName} is {@code null}
     */
    public static String getSimpleClassName(String fullClassName) {
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
    }

    /**
     * Converts a PascalCase string to camelCase.
     * <p>
     * For example, {@code "MyEntity"} yields {@code "myEntity"}.
     * </p>
     *
     * @param pascalCase input string in PascalCase (first letter uppercase)
     * @return the string in camelCase (first letter lowercase)
     * @throws NullPointerException if {@code pascalCase} is {@code null}
     * @see #camelToKebabCase(String)
     */
    public static String toCamelCase(String pascalCase) {
        return Character.toLowerCase(pascalCase.charAt(0)) + pascalCase.substring(1);
    }

    /**
     * Converts a camelCase string to kebab-case.
     * <p>
     * For example, {@code "myEntityName"} yields {@code "my-entity-name"}.
     * </p>
     *
     * @param camelCaseString input string in camelCase
     * @return kebab-case equivalent, or {@code null} if input is {@code null}
     * @see #toCamelCase(String)
     */
    public static String camelToKebabCase(String camelCaseString) {
        if (camelCaseString == null || camelCaseString.isEmpty()) {
            return camelCaseString;
        }
        // Use a regex to find uppercase letters that are not at the beginning of the string,
        // and replace them with a hyphen followed by the lowercase version of the letter.
        // The regex ([a-z0-9])([A-Z]) captures a lowercase letter/digit followed by an uppercase letter.
        // $1 refers to the first captured group (the lowercase letter/digit),
        // $2 refers to the second captured group (the uppercase letter).
        return camelCaseString
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
    }
}

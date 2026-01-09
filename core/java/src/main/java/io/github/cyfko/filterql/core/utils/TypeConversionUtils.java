package io.github.cyfko.filterql.core.utils;

import io.github.cyfko.filterql.core.config.EnumMatchMode;
import io.github.cyfko.filterql.core.config.StringCaseStrategy;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive type conversion utility for FilterQL framework.
 * <p>
 * This utility class provides robust, adapter-agnostic type conversion capabilities
 * for transforming filter values from their source format (typically strings or mixed types)
 * to the target types required by persistence layers or business logic.
 * </p>
 *
 * <h2>Design Philosophy</h2>
 * <p>
 * {@code TypeConversionUtils} is designed as a shared, reusable component across all
 * FilterQL adapters (JPA, MongoDB, JDBC, etc.). It centralizes conversion logic to ensure:
 * </p>
 * <ul>
 *   <li><strong>Consistency:</strong> All adapters use the same conversion rules</li>
 *   <li><strong>Maintainability:</strong> Single source of truth for type handling</li>
 *   <li><strong>Testability:</strong> Conversion logic isolated and thoroughly tested</li>
 *   <li><strong>Extensibility:</strong> Easy to add support for new types</li>
 * </ul>
 *
 * <h2>Supported Type Categories</h2>
 * <dl>
 *   <dt><strong>Numeric Types</strong></dt>
 *   <dd>
 *     Primitives (int, long, double, float, short, byte), Wrappers (Integer, Long, etc.),
 *     BigDecimal, BigInteger. Handles string representations and numeric conversions.
 *   </dd>
 *
 *   <dt><strong>Date/Time Types</strong></dt>
 *   <dd>
 *     LocalDate, LocalDateTime, LocalTime, Instant, ZonedDateTime, OffsetDateTime,
 *     java.util.Date. Supports ISO-8601 strings and epoch timestamps.
 *   </dd>
 *
 *   <dt><strong>Enum Types</strong></dt>
 *   <dd>
 *     Case-sensitive and case-insensitive matching via {@link EnumMatchMode}.
 *     Robust error messages for invalid enum values.
 *   </dd>
 *
 *   <dt><strong>Collection Types</strong></dt>
 *   <dd>
 *     List, Set, arrays with element-wise type conversion. Supports CSV strings,
 *     existing collections, and arrays as input.
 *   </dd>
 *
 *   <dt><strong>Other Types</strong></dt>
 *   <dd>
 *     Boolean (flexible parsing: "true", "1", "yes", "y", "oui"),
 *     UUID (from string), String, and any type with a String constructor.
 *   </dd>
 * </dl>
 *
 * <h2>Key Methods</h2>
 * <ul>
 *   <li>{@link #convertValue(Class, Object, EnumMatchMode)} - Main conversion entry point</li>
 *   <li>{@link #convertToTypedCollection(Object, Class)} - Collection conversion with element typing</li>
 *   <li>{@link #applyStringCaseStrategy(Object, StringCaseStrategy)} - String case normalization</li>
 *   <li>{@link #convertToEnum(Class, Object, EnumMatchMode)} - Enum conversion with mode control</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Basic type conversion
 * Integer age = (Integer) TypeConversionUtils.convertValue(
 *     Integer.class, "25", EnumMatchMode.CASE_INSENSITIVE
 * );
 *
 * // Collection conversion from CSV
 * List<Long> ids = TypeConversionUtils.convertToTypedCollection(
 *     "1,2,3,4,5",  // CSV string
 *     Long.class
 * );
 *
 * // Enum conversion (case-insensitive)
 * Priority priority = (Priority) TypeConversionUtils.convertToEnum(
 *     Priority.class,
 *     "high",  // Matches HIGH enum constant
 *     EnumMatchMode.CASE_INSENSITIVE
 * );
 *
 * // Date conversion from ISO-8601
 * LocalDate date = (LocalDate) TypeConversionUtils.convertValue(
 *     LocalDate.class,
 *     "2024-01-15",
 *     EnumMatchMode.CASE_INSENSITIVE
 * );
 *
 * // String case strategy application
 * Object normalized = TypeConversionUtils.applyStringCaseStrategy(
 *     "MixedCase",
 *     StringCaseStrategy.LOWER
 * ); // Returns "mixedcase"
 *
 * // Complex collection with type conversion
 * List<BigDecimal> prices = TypeConversionUtils.convertToTypedCollection(
 *     Arrays.asList("19.99", "29.99", "39.99"),
 *     BigDecimal.class
 * );
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>
 * The class throws {@link IllegalArgumentException} with descriptive messages for:
 * </p>
 * <ul>
 *   <li>Unsupported target types</li>
 *   <li>Invalid enum values (with mode-specific error messages)</li>
 *   <li>Unparseable date/time strings</li>
 *   <li>Numeric conversion failures</li>
 *   <li>Null target types (null values are passed through)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All methods are stateless and thread-safe. This utility class can be used
 * safely in concurrent environments without synchronization.
 * </p>
 *
 * <h2>Integration with FilterQL</h2>
 * <p>
 * This utility is used internally by all FilterQL adapters to ensure consistent
 * type conversion behavior. When implementing custom adapters, leverage these
 * methods to maintain compatibility with the FilterQL ecosystem.
 * </p>
 *
 * @since 4.0.0
 * @see EnumMatchMode
 * @see StringCaseStrategy
 * @see io.github.cyfko.filterql.core.config.FilterConfig
 */
public final class TypeConversionUtils {

    private TypeConversionUtils() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    // ========================================
    // PUBLIC API: Main Conversion Methods
    // ========================================

    /**
     * Converts a value to the specified target type with full support for complex types.
     * <p>
     * This is the main entry point for type conversion. It handles:
     * <ul>
     *   <li>Numeric types (primitives and wrappers)</li>
     *   <li>Enums (case-insensitive with configurable mode)</li>
     *   <li>Boolean (supports "true", "1", "yes", "oui", "y")</li>
     *   <li>Date/Time types (LocalDate, LocalDateTime, Instant, etc.)</li>
     *   <li>UUID</li>
     *   <li>BigDecimal, BigInteger</li>
     *   <li>String</li>
     *   <li>Any type with a String constructor</li>
     * </ul>
     * </p>
     *
     * @param targetType The target class to convert to
     * @param value The value to convert
     * @param enumMatchMode The enum matching strategy (CASE_INSENSITIVE, EXACT, etc.)
     * @return The converted value
     * @throws IllegalArgumentException if conversion is not possible
     */
    public static Object convertValue(Class<?> targetType, Object value, EnumMatchMode enumMatchMode) {
        if (value == null) {
            return null;
        }

        // If type matches, return as is
        if (targetType.isInstance(value)) {
            return value;
        }

        try {
            // BigDecimal/BigInteger first (before numeric check)
            if (targetType == BigDecimal.class) return convertToBigDecimal(value);
            if (targetType == BigInteger.class) return convertToBigInteger(value);

            // Numeric types
            if (Number.class.isAssignableFrom(targetType) || isNumericPrimitive(targetType)) {
                return convertToNumeric(targetType, value);
            }

            // Enum types
            if (targetType.isEnum()) {
                return convertToEnum(targetType, value, enumMatchMode);
            }

            // Boolean
            if (targetType == Boolean.class || targetType == boolean.class) {
                return convertToBoolean(value);
            }

            // Date/Time types
            if (targetType == LocalDate.class) return convertToLocalDate(value);
            if (targetType == LocalDateTime.class) return convertToLocalDateTime(value);
            if (targetType == LocalTime.class) return convertToLocalTime(value);
            if (targetType == Instant.class) return convertToInstant(value);
            if (targetType == ZonedDateTime.class) return convertToZonedDateTime(value);
            if (targetType == Date.class) return convertToDate(value);

            // UUID
            if (targetType == UUID.class) {
                return value instanceof UUID ? value : UUID.fromString(value.toString());
            }

            // String
            if (targetType == String.class) {
                return value.toString();
            }

            // Try constructor with String parameter
            try {
                Constructor<?> constructor = targetType.getConstructor(String.class);
                return constructor.newInstance(value.toString());
            } catch (NoSuchMethodException e) {
                // No string constructor
            }

            throw new IllegalArgumentException(
                    String.format("Cannot convert value '%s' (type: %s) to target type %s",
                            value, value.getClass().getName(), targetType.getName())
            );

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Error converting value '%s' to type %s: %s",
                            value, targetType.getName(), e.getMessage()), e
            );
        }
    }

    /**
     * Converts a value (single, array, or collection) to a typed collection.
     * <p>
     * This method handles multiple input formats:
     * <ul>
     *   <li>Collection: converts each element to target type</li>
     *   <li>Array: converts each element to target type</li>
     *   <li>CSV String: splits by comma and converts each element</li>
     *   <li>Single value: wraps in a singleton list</li>
     * </ul>
     * </p>
     *
     * <p><strong>Examples:</strong></p>
     * <pre>{@code
     * // From List
     * List<Integer> ids = convertToTypedCollection(Arrays.asList("1", "2"), Integer.class, EnumMatchMode.EXACT);
     * // Result: [1, 2]
     *
     * // From CSV string
     * List<String> tags = convertToTypedCollection("tag1,tag2,tag3", String.class, EnumMatchMode.EXACT);
     * // Result: ["tag1", "tag2", "tag3"]
     *
     * // From array
     * List<Long> timestamps = convertToTypedCollection(new Object[]{1L, 2L}, Long.class, EnumMatchMode.EXACT);
     * // Result: [1, 2]
     * }</pre>
     *
     * @param value The value to convert (can be single, array, collection, or CSV string)
     * @param elementType The target type for collection elements
     * @param enumMatchMode The enum matching strategy
     * @return A list of converted values
     * @throws IllegalArgumentException if conversion fails
     */
    public static List<?> convertToTypedCollection(Object value, Class<?> elementType, EnumMatchMode enumMatchMode) {
        if (value == null) {
            return null;
        }

        List<Object> result = new ArrayList<>();

        // If value is already a collection, convert each element
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                result.add(convertValue(elementType, item, enumMatchMode));
            }
            return result;
        }

        // If value is an array, convert each element
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(convertValue(elementType, Array.get(value, i), enumMatchMode));
            }
            return result;
        }

        // If it's a comma-separated string, split and convert
        if (value instanceof String str) {
            if (str.contains(",")) {
                return Arrays.stream(str.split(","))
                        .map(String::trim)
                        .map(item -> convertValue(elementType, item, enumMatchMode))
                        .collect(Collectors.toList());
            }
        }

        // Single value wrapped in a collection
        return Collections.singletonList(convertValue(elementType, value, enumMatchMode));
    }

    /**
     * Applies a string case strategy to a value or collection of values.
     * <p>
     * This method transforms string values according to the specified strategy:
     * <ul>
     *   <li>LOWER: converts to lowercase</li>
     *   <li>UPPER: converts to uppercase</li>
     *   <li>NONE: no transformation</li>
     * </ul>
     * </p>
     *
     * <p>For collections, the strategy is applied to each string element.</p>
     *
     * @param value The value to transform (String or Collection)
     * @param strategy The case strategy to apply
     * @return The transformed value
     */
    public static Object applyStringCaseStrategy(Object value, StringCaseStrategy strategy) {
        if (value == null || strategy == null) {
            return value;
        }

        return switch (strategy) {
            case LOWER -> {
                if (value instanceof String s) yield s.toLowerCase();
                if (value instanceof Collection<?> col) {
                    yield col.stream()
                            .map(v -> v instanceof String sv ? sv.toLowerCase() : v)
                            .collect(Collectors.toList());
                }
                yield value;
            }
            case UPPER -> {
                if (value instanceof String s) yield s.toUpperCase();
                if (value instanceof Collection<?> col) {
                    yield col.stream()
                            .map(v -> v instanceof String sv ? sv.toUpperCase() : v)
                            .collect(Collectors.toList());
                }
                yield value;
            }
            case NONE -> value;
        };
    }

    // ========================================
    // INTERNAL: Type Checking
    // ========================================

    /**
     * Checks if a class represents a numeric primitive type.
     */
    private static boolean isNumericPrimitive(Class<?> type) {
        return type == int.class || type == long.class || type == double.class
                || type == float.class || type == short.class || type == byte.class;
    }

    // ========================================
    // INTERNAL: Numeric Conversions
    // ========================================

    /**
     * Converts a value to a numeric type (primitive or wrapper).
     * Supports conversion from Number objects and String representations.
     */
    private static Object convertToNumeric(Class<?> targetType, Object value) {
        if (value instanceof Number num) {
            if (targetType == Integer.class || targetType == int.class) return num.intValue();
            if (targetType == Long.class || targetType == long.class) return num.longValue();
            if (targetType == Double.class || targetType == double.class) return num.doubleValue();
            if (targetType == Float.class || targetType == float.class) return num.floatValue();
            if (targetType == Short.class || targetType == short.class) return num.shortValue();
            if (targetType == Byte.class || targetType == byte.class) return num.byteValue();
        }

        String str = value.toString();
        if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(str);
        if (targetType == Long.class || targetType == long.class) return Long.valueOf(str);
        if (targetType == Double.class || targetType == double.class) return Double.valueOf(str);
        if (targetType == Float.class || targetType == float.class) return Float.valueOf(str);
        if (targetType == Short.class || targetType == short.class) return Short.valueOf(str);
        if (targetType == Byte.class || targetType == byte.class) return Byte.valueOf(str);

        throw new IllegalArgumentException("Unsupported numeric type: " + targetType);
    }

    // ========================================
    // INTERNAL: Enum Conversion
    // ========================================

    /**
     * Converts a value to an enum constant using the specified match mode.
     * <p>
     * Supports multiple matching strategies:
     * <ul>
     *   <li>EXACT: exact name match (case-sensitive)</li>
     *   <li>CASE_INSENSITIVE: case-insensitive name match</li>
     *   <li>Others: delegated to EnumMatchMode behavior</li>
     * </ul>
     * </p>
     */
    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E convertToEnum(Class<?> targetType, Object value, EnumMatchMode enumMatchMode) {
        if (!targetType.isEnum()) {
            throw new IllegalArgumentException("Target type is not an enum: " + targetType);
        }

        Class<E> enumClass = (Class<E>) targetType;
        String stringValue = value.toString();

        // Try exact match first
        try {
            return Enum.valueOf(enumClass, stringValue);
        } catch (IllegalArgumentException ignored) {
            // If CASE_SENSITIVE mode and exact match failed, throw immediately
            if (enumMatchMode == EnumMatchMode.CASE_SENSITIVE) {
                throw new IllegalArgumentException(
                        String.format("Invalid value '%s' for enum %s (mode: %s)",
                                stringValue, enumClass.getSimpleName(), enumMatchMode)
                );
            }
        }

        // Case-insensitive matching (only if mode is CASE_INSENSITIVE)
        if (enumMatchMode == EnumMatchMode.CASE_INSENSITIVE) {
            for (E enumConstant : enumClass.getEnumConstants()) {
                if (enumConstant.name().equalsIgnoreCase(stringValue)) {
                    return enumConstant;
                }
            }
        }

        throw new IllegalArgumentException(
                String.format("Invalid value '%s' for enum %s (mode: %s)",
                        stringValue, enumClass.getSimpleName(), enumMatchMode)
        );
    }

    // ========================================
    // INTERNAL: Boolean Conversion
    // ========================================

    /**
     * Converts a value to Boolean.
     * <p>
     * Recognizes the following as true (case-insensitive):
     * <ul>
     *   <li>"true"</li>
     *   <li>"1"</li>
     *   <li>"yes"</li>
     *   <li>"oui"</li>
     *   <li>"y"</li>
     *   <li>Numbers != 0</li>
     * </ul>
     * </p>
     */
    private static Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number num) return num.intValue() != 0;

        String normalized = value.toString().toLowerCase().trim();
        return "true".equals(normalized) || "1".equals(normalized)
                || "yes".equals(normalized) || "oui".equals(normalized) || "y".equals(normalized);
    }

    // ========================================
    // INTERNAL: Date/Time Conversions
    // ========================================

    private static LocalDate convertToLocalDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (value instanceof Date d) return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if (value instanceof Long l) return Instant.ofEpochMilli(l).atZone(ZoneId.systemDefault()).toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static LocalDateTime convertToLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Date d) return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        if (value instanceof Long l) return Instant.ofEpochMilli(l).atZone(ZoneId.systemDefault()).toLocalDateTime();
        return LocalDateTime.parse(value.toString());
    }

    private static LocalTime convertToLocalTime(Object value) {
        if (value instanceof LocalTime lt) return lt;
        if (value instanceof LocalDateTime ldt) return ldt.toLocalTime();
        return LocalTime.parse(value.toString());
    }

    private static Instant convertToInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof Date d) return d.toInstant();
        if (value instanceof Long l) return Instant.ofEpochMilli(l);
        if (value instanceof LocalDateTime ldt) return ldt.atZone(ZoneId.systemDefault()).toInstant();
        return Instant.parse(value.toString());
    }

    private static ZonedDateTime convertToZonedDateTime(Object value) {
        if (value instanceof ZonedDateTime zdt) return zdt;
        if (value instanceof Date d) return d.toInstant().atZone(ZoneId.systemDefault());
        if (value instanceof Long l) return Instant.ofEpochMilli(l).atZone(ZoneId.systemDefault());
        if (value instanceof LocalDateTime ldt) return ldt.atZone(ZoneId.systemDefault());
        return ZonedDateTime.parse(value.toString());
    }

    private static Date convertToDate(Object value) {
        if (value instanceof Date d) return d;
        if (value instanceof Long l) return new Date(l);
        if (value instanceof LocalDateTime ldt) return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        if (value instanceof LocalDate ld) return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
        if (value instanceof Instant i) return Date.from(i);

        // Try parsing as ISO date string
        try {
            return Date.from(Instant.parse(value.toString()));
        } catch (Exception e) {
            // Fallback to timestamp
            return new Date(Long.parseLong(value.toString()));
        }
    }

    private static BigDecimal convertToBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private static BigInteger convertToBigInteger(Object value) {
        if (value instanceof BigInteger bi) return bi;
        if (value instanceof Number n) return BigInteger.valueOf(n.longValue());
        return new BigInteger(value.toString());
    }
}

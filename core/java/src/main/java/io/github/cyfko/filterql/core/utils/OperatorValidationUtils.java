package io.github.cyfko.filterql.core.utils;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * Utility class providing static methods for operator and value validation in the FilterQL framework.
 * <p>
 * This class centralizes all validation logic for operators and their values, ensuring consistency
 * across the framework and enabling reusability in custom implementations.
 * </p>
 *
 * <p><b>Key responsibilities:</b></p>
 * <ul>
 *     <li>Validate values against operators (EQ, IN, RANGE, etc.)</li>
 *     <li>Check type compatibility between values and expected types</li>
 *     <li>Handle collections, arrays, and scalar values appropriately</li>
 *     <li>Provide detailed error messages for validation failures</li>
 * </ul>
 *
 * <p><b>Usage example:</b></p>
 * <pre>{@code
 * // Validate a single value for EQ operator
 * ValidationResult result = OperatorValidationUtils.validateValueForOperator(
 *     Op.EQ, "John", String.class
 * );
 * 
 * if (!result.isValid()) {
 *     throw new FilterValidationException(result.getErrorMessage());
 * }
 * 
 * // Check type compatibility
 * boolean compatible = OperatorValidationUtils.isCompatibleType(Integer.class, Number.class);
 * }</pre>
 *
 * <p>This class is stateless and thread-safe. All methods are static and do not maintain any state.</p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 * @see ValidationResult
 * @see PropertyReference
 */
public final class OperatorValidationUtils {

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class
    );

    /**
     * Private constructor to prevent instantiation.
     */
    private OperatorValidationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates the compatibility between an operator and a value.
     * <p>
     * This method performs comprehensive validation based on the operator type:
     * </p>
     * <ul>
     *     <li>Comparison operators (EQ, GT, etc.): Single value or collection of compatible type</li>
     *     <li>Null-check operators (IS_NULL, IS_NOT_NULL): Always valid</li>
     *     <li>IN operators: Collection or single value of compatible type</li>
     *     <li>Range operators (RANGE, NOT_RANGE): Collection of exactly 2 compatible values</li>
     *     <li>Custom operators: Validation delegated to operator implementation</li>
     * </ul>
     *
     * @param op           the operator to validate
     * @param value        the value to validate
     * @param expectedType the expected type for the property
     * @return the validation result indicating success or failure with error message
     * @throws NullPointerException if op or expectedType is null
     */
    public static ValidationResult validateValueForOperator(Op op, Object value, Class<?> expectedType) {
        if (op == null) {
            throw new NullPointerException("Operator cannot be null");
        }
        if (expectedType == null) {
            throw new NullPointerException("Expected type cannot be null");
        }

        return switch (op) {
            case EQ, NE, GT, GTE, LT, LTE, MATCHES, NOT_MATCHES ->
                    validateSingleOrMultiValue(op, value, expectedType);
            case IS_NULL, NOT_NULL -> validateNullCheck(op, value);
            case IN, NOT_IN -> validateInOperatorValue(op, value, expectedType);
            case RANGE, NOT_RANGE -> validateCollectionValue(op, value, expectedType, true);
            case CUSTOM -> ValidationResult.success(); // Custom operators validate at execution time
        };
    }

    /**
     * Validates a single value for comparison operators.
     * <p>
     * The value must be non-null and type-compatible with the expected type.
     * </p>
     *
     * @param operator     the operator being validated
     * @param value        the value to validate
     * @param expectedType the expected type for the property
     * @return the validation result
     */
    public static ValidationResult validateSingleValue(Op operator, Object value, Class<?> expectedType) {
        if (value == null) {
            return ValidationResult.failure(
                    String.format("Operator %s requires a non-null value", operator)
            );
        }

        if (!isCompatibleType(value.getClass(), expectedType)) {
            return ValidationResult.failure(
                    String.format("Value of type %s is not compatible with property type %s for operator %s",
                            value.getClass().getSimpleName(),
                            expectedType.getSimpleName(),
                            operator)
            );
        }

        return ValidationResult.success();
    }

    /**
     * Validates a value that may be either a single element or a collection/array.
     * <p>
     * This method is used for "singleton" operators like EQ, GT, MATCHES, etc.
     * that typically work with single values but can also accept collections for batch processing.
     * </p>
     * <p>
     * For collections/arrays:
     * </p>
     * <ul>
     *     <li>Must be non-empty</li>
     *     <li>All elements must be type-compatible with the expected type</li>
     * </ul>
     *
     * @param operator     the operator being validated
     * @param value        the value to validate (single, collection, or array)
     * @param expectedType the expected type for the property
     * @return the validation result
     */
    public static ValidationResult validateSingleOrMultiValue(Op operator, Object value, Class<?> expectedType) {
        if (value == null) {
            return ValidationResult.failure(
                    String.format("Operator %s requires a non-null value", operator)
            );
        }

        // Handle collection
        if (value instanceof Collection<?> col) {
            if (col.isEmpty()) {
                return ValidationResult.failure(
                        String.format("Operator %s requires a non-empty collection", operator)
                );
            }
            if (!ClassUtils.allCompatible(expectedType, col)) {
                return ValidationResult.failure(
                        String.format("Collection elements are not compatible with property type %s for operator %s",
                                expectedType.getSimpleName(), operator)
                );
            }
            return ValidationResult.success();
        }

        // Handle array
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length == 0) {
                return ValidationResult.failure(
                        String.format("Operator %s requires a non-empty array", operator)
                );
            }
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                if (element == null || !isCompatibleType(element.getClass(), expectedType)) {
                    return ValidationResult.failure(
                            String.format("Array elements are not compatible with property type %s for operator %s",
                                    expectedType.getSimpleName(), operator)
                    );
                }
            }
            return ValidationResult.success();
        }

        // Fallback to single value validation
        return validateSingleValue(operator, value, expectedType);
    }

    /**
     * Validates values for IN and NOT_IN operators.
     * <p>
     * These operators accept either:
     * </p>
     * <ul>
     *     <li>A collection or array of compatible values</li>
     *     <li>A single compatible scalar value (which will be wrapped in a collection)</li>
     * </ul>
     *
     * @param operator     the IN or NOT_IN operator
     * @param value        the value to validate
     * @param expectedType the expected type for the property
     * @return the validation result
     */
    public static ValidationResult validateInOperatorValue(Op operator, Object value, Class<?> expectedType) {
        if (value == null) {
            return ValidationResult.failure(
                    String.format("Operator %s requires a non-null value (collection or single)", operator)
            );
        }

        if (value instanceof Collection<?> || value.getClass().isArray()) {
            return validateCollectionValue(operator, value, expectedType, false);
        }

        // Single scalar – must be compatible with property type
        if (!isCompatibleType(value.getClass(), expectedType)) {
            return ValidationResult.failure(
                    String.format("Value of type %s is not compatible with property type %s for operator %s",
                            value.getClass().getSimpleName(), expectedType.getSimpleName(), operator)
            );
        }
        return ValidationResult.success();
    }

    /**
     * Validates null-check operators (IS_NULL, IS_NOT_NULL).
     * <p>
     * These operators don't require specific values, so validation always succeeds.
     * The framework is tolerant and accepts any value provided.
     * </p>
     *
     * @param operator the null-check operator
     * @param value    the value (ignored)
     * @return always returns a successful validation result
     */
    public static ValidationResult validateNullCheck(Op operator, Object value) {
        // For IS_NULL and IS_NOT_NULL, the value should be null or absent
        // but we can be tolerant and accept any value
        return ValidationResult.success();
    }

    /**
     * Validates a collection value for operators that require collections.
     * <p>
     * Used primarily for IN, NOT_IN, RANGE, and NOT_RANGE operators.
     * </p>
     *
     * @param operator          the operator being validated
     * @param value             the collection value to validate
     * @param expectedType      the expected type for collection elements
     * @param requiresExactlyTwo if true, the collection must contain exactly 2 elements (for RANGE operators)
     * @return the validation result
     */
    public static ValidationResult validateCollectionValue(Op operator, Object value, Class<?> expectedType,
                                                            boolean requiresExactlyTwo) {
        if (value == null) {
            return ValidationResult.failure(
                    String.format("Operator %s requires a non-null collection value", operator)
            );
        }

        if (!(value instanceof Collection<?>)) {
            return ValidationResult.failure(
                    String.format("Operator %s requires a Collection value, got %s",
                            operator, value.getClass().getSimpleName())
            );
        }

        Collection<?> collection = (Collection<?>) value;

        if (collection.isEmpty()) {
            return ValidationResult.failure(
                    String.format("Operator %s requires a non-empty collection", operator)
            );
        }

        if (requiresExactlyTwo && collection.size() != 2) {
            return ValidationResult.failure(
                    String.format("Operator %s requires exactly 2 values for range, got %d",
                            operator, collection.size())
            );
        }

        // Check type compatibility of elements
        if (!ClassUtils.allCompatible(expectedType, collection)) {
            return ValidationResult.failure(
                    String.format("Collection elements are not compatible with property type %s for operator %s",
                            expectedType.getSimpleName(), operator)
            );
        }

        return ValidationResult.success();
    }

    /**
     * Checks compatibility between a value type and an expected type.
     * <p>
     * This method handles:
     * </p>
     * <ul>
     *     <li>Direct assignability (e.g., Integer to Number)</li>
     *     <li>Primitive types and their wrappers (e.g., int to Integer)</li>
     *     <li>Wrapper types and their primitives (e.g., Integer to int)</li>
     * </ul>
     *
     * @param valueType    the actual type of the value
     * @param expectedType the expected type
     * @return true if valueType is compatible with expectedType
     * @throws NullPointerException if either argument is null
     */
    public static boolean isCompatibleType(Class<?> valueType, Class<?> expectedType) {
        if (valueType == null || expectedType == null) {
            throw new NullPointerException("Types cannot be null");
        }

        // Direct assignability
        if (expectedType.isAssignableFrom(valueType)) {
            return true;
        }

        // Handle primitives and their wrappers
        return isPrimitiveCompatible(valueType, expectedType);
    }

    /**
     * Checks compatibility between primitive types and their wrapper classes.
     * <p>
     * Examples:
     * </p>
     * <ul>
     *     <li>int and Integer are compatible</li>
     *     <li>boolean and Boolean are compatible</li>
     *     <li>Integer and int are compatible (bidirectional)</li>
     * </ul>
     *
     * @param valueType    the actual type of the value
     * @param expectedType the expected type
     * @return true if the types are primitive-wrapper compatible
     */
    public static boolean isPrimitiveCompatible(Class<?> valueType, Class<?> expectedType) {
        // Primitive -> Wrapper
        if (PRIMITIVE_TO_WRAPPER.get(expectedType) == valueType) {
            return true;
        }

        // Wrapper -> Primitive
        return PRIMITIVE_TO_WRAPPER.get(valueType) == expectedType;
    }

    /**
     * Checks if a type is numeric (Number or numeric primitive).
     * <p>
     * Useful for determining which comparison operators can be applied.
     * </p>
     *
     * @param type the type to check
     * @return true if the type represents a number
     * @throws NullPointerException if type is null
     */
    public static boolean isNumeric(Class<?> type) {
        if (type == null) {
            throw new NullPointerException("Type cannot be null");
        }
        return Number.class.isAssignableFrom(type) || isPrimitiveNumber(type);
    }

    /**
     * Checks if a type is textual (String or CharSequence).
     * <p>
     * Useful for determining if MATCHES/NOT_MATCHES operators are applicable.
     * </p>
     *
     * @param type the type to check
     * @return true if the type is textual
     * @throws NullPointerException if type is null
     */
    public static boolean isTextual(Class<?> type) {
        if (type == null) {
            throw new NullPointerException("Type cannot be null");
        }
        return CharSequence.class.isAssignableFrom(type);
    }

    /**
     * Checks if a type is a numeric primitive.
     *
     * @param type the type to check
     * @return true if the type is a numeric primitive (byte, short, int, long, float, double)
     */
    private static boolean isPrimitiveNumber(Class<?> type) {
        return type == byte.class || type == short.class ||
                type == int.class || type == long.class ||
                type == float.class || type == double.class;
    }
}

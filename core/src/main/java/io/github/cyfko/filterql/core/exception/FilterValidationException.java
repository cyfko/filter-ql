package io.github.cyfko.filterql.core.exception;

import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;

/**
 * Exception thrown when a filter condition cannot be validated or constructed.
 * <p>
 * This exception specifically handles validation errors that occur during filter 
 * processing, including type mismatches, unsupported operators, invalid values,
 * and constraint violations. It provides detailed information to help developers
 * understand and fix filter definition issues.
 * </p>
 * 
 * <p><strong>Common Validation Scenarios:</strong></p>
 * <ul>
 *   <li><strong>Type Mismatches:</strong> Value type incompatible with property type</li>
 *   <li><strong>Unsupported Operators:</strong> Operator not allowed for property</li>
 *   <li><strong>Invalid Values:</strong> Null values for non-nullable operators</li>
 *   <li><strong>Collection Constraints:</strong> Empty collections for IN/RANGE operators</li>
 *   <li><strong>Range Validation:</strong> Invalid range bounds or ordering</li>
 * </ul>
 * 
 * <p><strong>Validation Error Examples:</strong></p>
 * <pre>{@code
 * // Type mismatch errors
 * FilterDefinition<UserPropertyRef> ageFilter = new FilterDefinition<>(
 *     UserPropertyRef.AGE,  // expects Integer
 *     Op.GT, 
 *     "25"  // String provided instead of Integer
 * );
 * // → "Value of type String is not compatible with property type Integer for operator GT"
 * 
 * // Unsupported operator errors  
 * FilterDefinition<UserPropertyRef> nameFilter = new FilterDefinition<>(
 *     UserPropertyRef.NAME,  // only supports EQ, MATCHES, IN
 *     Op.GT,  // not supported for String properties
 *     "John"
 * );
 * // → "Operator GT is not supported for property NAME. Supported operators: [EQ, MATCHES, IN]"
 * 
 * // Invalid collection errors
 * FilterDefinition<UserPropertyRef> statusFilter = new FilterDefinition<>(
 *     UserPropertyRef.STATUS,
 *     Op.IN,
 *     List.of()  // Empty collection
 * );
 * // → "Operator IN requires a non-empty collection"
 * 
 * // Range validation errors
 * FilterDefinition<UserPropertyRef> ageRangeFilter = new FilterDefinition<>(
 *     UserPropertyRef.AGE,
 *     Op.RANGE,
 *     List.of(25, 30, 35)  // Range needs exactly 2 values
 * );
 * // → "Operator RANGE requires exactly 2 values for range, got 3"
 * }</pre>
 * 
 * <p><strong>Handling Best Practices:</strong></p>
 * <pre>{@code
 * try {
 *     // Validate property reference and operator
 *     propertyRef.validateOperatorForValue(operator, value);
 *     
 *     // Create filter definition
 *     FilterDefinition<PropertyRef> filter = new FilterDefinition<>(propertyRef, operator, value);
 *     
 * } catch (FilterValidationException e) {
 *     // Log detailed validation error
 *     logger.warn("Filter validation failed: {}", e.getMessage());
 *     
 *     // Return structured error response
 *     return ValidationResult.failure(e.getMessage());
 * }
 * }</pre>
 * 
 * <p><strong>Integration with Property References:</strong></p>
 * <p>This exception is commonly thrown by {@link PropertyReference}
 * implementations during validation of operator-value combinations. The error messages
 * are designed to be informative enough for both developers and end users.</p>
 * 
 * <p><strong>Error Recovery Strategies:</strong></p>
 * <ul>
 *   <li>Pre-validate filter inputs in UI components</li>
 *   <li>Provide type-aware input controls (number inputs for numeric properties)</li>
 *   <li>Show available operators per property type</li>
 *   <li>Validate collections before submission</li>
 *   <li>Implement graceful fallbacks for invalid filters</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see PropertyReference
 * @see io.github.cyfko.filterql.core.model.FilterDefinition
 * @see Op
 */
public class FilterValidationException extends RuntimeException {

    /**
     * Creates an exception with an explanatory message.
     * <p>
     * This constructor is used for straightforward validation errors where
     * the message provides sufficient context about what went wrong.
     * The message should be descriptive and actionable.
     * </p>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <pre>{@code
     * // Type compatibility error
     * throw new FilterValidationException(
     *     "Value of type String is not compatible with property type Integer for operator GT");
     * 
     * // Operator support error
     * throw new FilterValidationException(
     *     "Operator RANGE is not supported for property NAME. Supported operators: [EQ, MATCHES, IN]");
     * 
     * // Collection validation error  
     * throw new FilterValidationException(
     *     "Operator IN requires a non-empty collection");
     * }</pre>
     *
     * @param message the description of the cause of the exception, should be specific and actionable
     */
    public FilterValidationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with an explanatory message and an underlying cause.
     * <p>
     * This constructor is used when validation fails due to an underlying system
     * issue, such as reflection errors, type conversion failures, or database
     * constraint violations. It preserves the original exception for debugging.
     * </p>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <pre>{@code
     * try {
     *     // Attempt type conversion for validation
     *     convertValueToExpectedType(value, expectedType);
     * } catch (ClassCastException e) {
     *     throw new FilterValidationException(
     *         "Failed to convert value '" + value + "' to expected type " + expectedType.getSimpleName(), e);
     * }
     * 
     * try {
     *     // Validate against database constraints
     *     validateAgainstDatabaseSchema(propertyRef, value);
     * } catch (SQLException e) {
     *     throw new FilterValidationException(
     *         "Database validation failed for property " + propertyRef + " with value " + value, e);
     * }
     * }</pre>
     *
     * @param message the description of the cause of the exception
     * @param cause   the original cause of the exception (e.g., ClassCastException, SQLException)
     */
    public FilterValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}


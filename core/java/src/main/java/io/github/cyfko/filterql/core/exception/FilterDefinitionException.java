package io.github.cyfko.filterql.core.exception;

/**
 * Exception thrown when a filter definition is invalid or cannot be constructed.
 * <p>
 * This runtime exception signals validation failures or construction errors
 * encountered while defining or processing filters within FilterQL.
 * It is designed to provide clear diagnostic information about invalid filter conditions,
 * such as type incompatibilities, unsupported operators, nullability violations, or range errors.
 * </p>
 *
 * <p><strong>Common Validation Failure Scenarios:</strong></p>
 * <ul>
 *   <li>Type mismatches between filter value and expected property type</li>
 *   <li>Unsupported or unknown operators specified for a given property</li>
 *   <li>Null values provided for operators that require non-null operands</li>
 *   <li>Empty or incorrectly sized collections for multi-value operators (e.g., IN, RANGE)</li>
 *   <li>Invalid range bounds or ordering for range operators</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Throwing an exception for a type mismatch
 * throw new FilterDefinitionException("Value 'abc' is not compatible with Integer property for operator GT.");
 *
 * // Throwing an exception for unsupported operator
 * throw new FilterDefinitionException("Operator IN is not supported for property STATUS.");
 *
 * // Throwing an exception for invalid collection size
 * throw new FilterDefinitionException("Operator RANGE requires exactly 2 values, got 3.");
 * }</pre>
 *
 * <p><strong>Best Practice for Handling:</strong></p>
 * <pre>{@code
 * try {
 *     // Validate and create filter definition
 *     FilterDefinition<PropertyRef> filter = new FilterDefinition<>(propertyRef, operator, value);
 * } catch (FilterDefinitionException e) {
 *     logger.warn("Filter definition failed validation: " + e.getMessage());
 *     // Handle error gracefully, e.g., return meaningful response to client
 * }
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class FilterDefinitionException extends RuntimeException {

    /**
     * Creates a new FilterDefinitionException with detailed message.
     *
     * @param message explanation of the validation failure or construction error
     */
    public FilterDefinitionException(String message) {
        super(message);
    }

    /**
     * Creates a new FilterDefinitionException with detailed message and cause.
     *
     * @param message explanation of the failure
     * @param cause underlying exception causing this failure (e.g., type conversion issues)
     */
    public FilterDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
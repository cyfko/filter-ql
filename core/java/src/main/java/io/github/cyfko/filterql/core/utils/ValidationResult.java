package io.github.cyfko.filterql.core.utils;

/**
 * Class representing the result of a validation operation.
 * <p>
 * The result can indicate either a successful validation or a failure with an associated error message.
 * This class is used to simply and clearly convey the validation state.
 * </p>
 *
 * <p>Instances are immutable and created via the static methods
 * {@link #success()} and {@link #failure(String)}.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * ValidationResult result = OperatorValidationUtils.validateValue(operator, value, type);
 * if (!result.isValid()) {
 *     System.out.println("Validation error: " + result.getErrorMessage());
 * }
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public final class ValidationResult {

    private final boolean valid;
    private final String errorMessage;

    /**
     * Private constructor - use via the static creation methods.
     *
     * @param valid        true if validation succeeded, false otherwise
     * @param errorMessage error message in case of failure, or null if valid
     */
    private ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates an instance indicating a successful validation.
     *
     * @return a valid result with no error message
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    /**
     * Creates an instance indicating a failed validation with an error message.
     *
     * @param errorMessage message explaining the reason for failure
     * @return an invalid result containing the provided error message
     */
    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }

    /**
     * Indicates whether the validation succeeded.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns the error message associated with a failed validation.
     *
     * @return error message if invalid, or null if valid
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return valid ? "ValidationResult[valid=true]"
                : "ValidationResult[valid=false, error=" + errorMessage + "]";
    }
}

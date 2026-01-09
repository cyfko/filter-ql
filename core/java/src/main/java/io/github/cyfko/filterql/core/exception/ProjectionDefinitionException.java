package io.github.cyfko.filterql.core.exception;

public class ProjectionDefinitionException extends RuntimeException {
    public ProjectionDefinitionException(String message) {
        super(message);
    }
    /**
     * Creates a new FilterDefinitionException with detailed message and cause.
     *
     * @param message explanation of the failure
     * @param cause underlying exception causing this failure (e.g., type conversion issues)
     */
    public ProjectionDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

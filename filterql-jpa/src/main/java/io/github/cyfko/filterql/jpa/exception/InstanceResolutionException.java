package io.github.cyfko.filterql.jpa.exception;

/**
 * Exception thrown when provider resolution fails.
 * 
 * <p>
 * This typically occurs when:
 * </p>
 * <ul>
 * <li>Multiple beans of the same type exist and no name was provided</li>
 * <li>The IoC container is not properly initialized</li>
 * <li>Circular dependencies are detected</li>
 * </ul>
 * 
 * @since 2.0.0
 */
public class InstanceResolutionException extends RuntimeException {

    /**
     * Constructs a new instance resolution exception with the specified detail
     * message.
     *
     * @param message the detail message explaining why resolution failed
     */
    public InstanceResolutionException(String message) {
        super(message);
    }

    /**
     * Constructs a new instance resolution exception with the specified detail
     * message and cause.
     *
     * @param message the detail message explaining why resolution failed
     * @param cause   the underlying cause of the resolution failure
     */
    public InstanceResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

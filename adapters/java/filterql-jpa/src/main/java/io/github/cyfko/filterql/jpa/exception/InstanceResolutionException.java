package io.github.cyfko.filterql.jpa.exception;

/**
 * Exception thrown when provider resolution fails.
 * 
 * <p>This typically occurs when:</p>
 * <ul>
 *   <li>Multiple beans of the same type exist and no name was provided</li>
 *   <li>The IoC container is not properly initialized</li>
 *   <li>Circular dependencies are detected</li>
 * </ul>
 * 
 * @since 2.0.0
 */
public class InstanceResolutionException extends RuntimeException {
    
    public InstanceResolutionException(String message) {
        super(message);
    }
    
    public InstanceResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

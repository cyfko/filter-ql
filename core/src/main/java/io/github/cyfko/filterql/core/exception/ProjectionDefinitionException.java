package io.github.cyfko.filterql.core.exception;

/**
 * Thrown when a projection definition is structurally invalid or references
 * fields that do not exist in the source entity.
 * <p>
 * Common causes include:
 * </p>
 * <ul>
 * <li>A {@code @Projected(from = "...")} path that does not resolve to a valid
 * entity field</li>
 * <li>A type mismatch between the DTO field and the resolved entity field</li>
 * <li>A {@code @Computed} dependency path that traverses a collection without a
 * terminal simple field</li>
 * <li>Conflicting or duplicate field projection definitions</li>
 * </ul>
 *
 * <p>
 * This is a <strong>runtime exception</strong> because projection validation
 * may occur both at compile-time (via annotation processors) and at runtime
 * (when projections are built dynamically from client requests).
 * </p>
 *
 * @see io.github.cyfko.filterql.core.exception.FilterDefinitionException
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class ProjectionDefinitionException extends RuntimeException {
    public ProjectionDefinitionException(String message) {
        super(message);
    }

    /**
     * Creates a new FilterDefinitionException with detailed message and cause.
     *
     * @param message explanation of the failure
     * @param cause   underlying exception causing this failure (e.g., type
     *                conversion issues)
     */
    public ProjectionDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

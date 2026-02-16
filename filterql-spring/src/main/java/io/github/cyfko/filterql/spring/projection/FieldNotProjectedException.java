package io.github.cyfko.filterql.spring.projection;

/**
 * Exception thrown when a getter is invoked on a projection proxy for a field
 * that was not included in the query projection.
 *
 * <p>
 * This distinguishes between a field that was projected with a {@code null}
 * value
 * (legitimate) and a field that was never projected at all (programming error
 * or
 * incomplete query).
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class FieldNotProjectedException extends RuntimeException {

    private final String fieldName;
    private final String projectionType;

    /**
     * Constructs a new exception for a non-projected field access.
     *
     * @param fieldName      the name of the field that was not projected
     * @param projectionType the simple name of the projection interface
     */
    public FieldNotProjectedException(String fieldName, String projectionType) {
        super("Field '" + fieldName + "' was not projected in the query for " + projectionType
                + ". Add this field to your projection or remove the accessor call.");
        this.fieldName = fieldName;
        this.projectionType = projectionType;
    }

    /**
     * @return the field name that triggered this exception
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * @return the projection interface type name
     */
    public String getProjectionType() {
        return projectionType;
    }
}

package io.github.cyfko.filterql.core.config;

import java.util.Objects;

/**
 * Configuration policy controlling how projection field names are matched
 * against entity field names during query construction.
 * <p>
 * The primary control is {@link FieldCase}, which determines whether projection
 * field matching is case-sensitive or case-insensitive. This is relevant when
 * the projection request specifies field names that differ in casing from the
 * actual entity fields (e.g., {@code "Name"} vs {@code "name"}).
 * </p>
 *
 * <p>
 * <strong>Default behavior:</strong> {@link FieldCase#CASE_INSENSITIVE},
 * obtainable via {@link #defaults()}.
 * </p>
 *
 * <p>
 * <strong>Edge case:</strong> When using {@link FieldCase#CASE_SENSITIVE},
 * a projection requesting {@code "Name"} will <em>not</em> match an entity
 * field
 * named {@code "name"}, resulting in the field being silently excluded from
 * results.
 * </p>
 *
 * @param fieldCase the case sensitivity strategy for field name matching; must
 *                  not be null
 * @see FilterConfig
 * @author Frank KOSSI
 * @since 4.0.0
 */
public record ProjectionPolicy(
        FieldCase fieldCase) {

    public ProjectionPolicy {
        Objects.requireNonNull(fieldCase, "fieldCase is required");
    }

    public static ProjectionPolicy defaults() {
        return new ProjectionPolicy(FieldCase.CASE_INSENSITIVE);
    }

    public enum FieldCase {
        CASE_INSENSITIVE,
        CASE_SENSITIVE,
    }
}

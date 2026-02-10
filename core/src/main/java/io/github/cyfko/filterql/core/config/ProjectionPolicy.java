package io.github.cyfko.filterql.core.config;

import java.util.Objects;

public record ProjectionPolicy(
        FieldCase fieldCase
) {

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

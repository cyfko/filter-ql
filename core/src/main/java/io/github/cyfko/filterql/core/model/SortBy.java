package io.github.cyfko.filterql.core.model;

import java.util.Objects;

/**
 * Sort specification with field name and direction.
 *
 * @param field field name to sort by
 * @param direction sort direction ("asc" or "desc", case-insensitive)
 */
public record SortBy(String field, String direction) {
    /**
     * Canonical constructor with validation.
     */
    public SortBy {
        Objects.requireNonNull(field, "Sorting field is required");
        Objects.requireNonNull(direction, "Sorting direction is required. Either 'asc' (ascending) or 'desc' (descending)");

        if (field.isBlank()) {
            throw new IllegalArgumentException("field cannot be blank");
        }

        // Normalize direction to lowercase
        direction = direction.toLowerCase();
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new IllegalArgumentException("direction must be 'asc' or 'desc', got: " + direction);
        }
    }

    /**
     * Creates ascending sort specification.
     *
     * @param field field name
     * @return sort specification with ascending direction
     */
    public static SortBy asc(String field) {
        return new SortBy(field, "asc");
    }

    /**
     * Creates descending sort specification.
     *
     * @param field field name
     * @return sort specification with descending direction
     */
    public static SortBy desc(String field) {
        return new SortBy(field, "desc");
    }
}

package io.github.cyfko.filterql.core.config;

/**
 * Policies for handling null values when adding filter definitions.
 */
public enum NullValuePolicy {
    /** Throw a FilterValidationException when a null value is provided for unsupported operators. */
    STRICT_EXCEPTION,
    /** Coerce EQ/NE to IS_NULL/NOT_NULL where possible. */
    COERCE_TO_IS_NULL,
    /** Ignore the filter by storing a neutral predicate. */
    IGNORE_FILTER;
}

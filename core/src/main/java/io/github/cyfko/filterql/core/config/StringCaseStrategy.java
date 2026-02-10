package io.github.cyfko.filterql.core.config;

/**
 * Strategy for handling string case when performing string comparisons/LIKE operations.
 */
public enum StringCaseStrategy {
    /** Do not change case (use value as provided). */
    NONE,
    /** Convert both property and value to lower case before comparing. */
    LOWER,
    /** Convert both property and value to upper case before comparing. */
    UPPER
}

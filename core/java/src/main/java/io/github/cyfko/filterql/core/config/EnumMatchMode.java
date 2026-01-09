package io.github.cyfko.filterql.core.config;

/**
 * Mode used to compare string input values to enum constants.
 */
public enum EnumMatchMode {
    /** Match enum name exactly (case-sensitive). */
    CASE_SENSITIVE,
    /** Match enum ignoring case. */
    CASE_INSENSITIVE
}

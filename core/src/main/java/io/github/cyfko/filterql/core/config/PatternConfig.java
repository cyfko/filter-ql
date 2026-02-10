package io.github.cyfko.filterql.core.config;

import java.util.regex.Pattern;

import static io.github.cyfko.filterql.core.config.DslReservedSymbol.FALSE;
import static io.github.cyfko.filterql.core.config.DslReservedSymbol.TRUE;

/**
 * Configuration for DSL identifier validation patterns.
 * <p>
 * Provides pre-compiled regular expression patterns for validating filter identifiers
 * in DSL expressions. Identifiers must start with a letter or underscore, followed by
 * letters, digits, or underscores, with a maximum length of 30 characters.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public abstract class PatternConfig {
    private PatternConfig () {}

    private static final String SIMPLE_FORM = "[a-zA-Z_][a-zA-Z0-9_]{0,29}";

    /**
     * Pattern for simple identifiers without reserved symbols.
     * <p>
     * Matches identifiers: start with letter or underscore, followed by alphanumerics/underscores, max 30 chars.
     * Example valid: "filter1", "user_name", "_temp"
     * Example invalid: "123abc", "filter-name", "a" (too short if minimum enforced elsewhere)
     * </p>
     */
    public static final Pattern SIMPLE_IDENTIFIER_PATTERN = Pattern.compile("^" +  SIMPLE_FORM + "$");

    /**
     * Pattern for identifiers including reserved boolean symbols.
     * <p>
     * Matches simple identifiers OR the reserved symbols for TRUE and FALSE.
     * Used in contexts where boolean constants are allowed.
     * </p>
     */
    public static final Pattern SIMPLE_PATTERN_WITH_RESERVED = Pattern.compile("^("+SIMPLE_FORM+"|"+FALSE+"|"+TRUE+")$");
}

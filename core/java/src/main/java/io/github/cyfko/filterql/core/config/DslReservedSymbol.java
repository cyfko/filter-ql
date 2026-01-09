package io.github.cyfko.filterql.core.config;

/**
 * Reserved symbolic constants used in DSL processing and boolean simplification.
 * <p>
 * These symbols represent boolean literals in a canonical form, allowing
 * the parser to recognize and optimize boolean expressions containing
 * constant true/false values.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class DslReservedSymbol {
    /**
     * Symbol representing the boolean constant TRUE (top element in boolean algebra).
     */
    public static final String TRUE = "⊤";

    /**
     * Symbol representing the boolean constant FALSE (bottom element in boolean algebra).
     */
    public static final String FALSE = "⊥";
}

package io.github.cyfko.filterql.core.api;

/**
 * Enumeration of supported filter operators.
 * <p>
 * Each operator defines its own symbol, code, and the types of operations it supports.
 * This enum follows naming conventions similar to those used in web standards,
 * HTML, and DOM APIs, emphasizing short, descriptive codes.
 * </p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>{@code
 * // Parse operator from symbol or code string
 * Op op = Op.fromString("=");
 * if (op != null && op.requiresValue()) {
 *     // handle operators that require a value
 * }
 *
 * // Check if operator supports multiple values (like IN or RANGE)
 * if (op.supportsMultipleValues()) {
 *     // handle collection or range values
 * }
 *
 * // Example with filter definitions
 * FilterDefinition<UserPropertyRef> equalFilter =
 *     new FilterDefinition<>(UserPropertyRef.NAME, Op.EQ, "John");
 * FilterDefinition<UserPropertyRef> likeFilter =
 *     new FilterDefinition<>(UserPropertyRef.NAME, Op.MATCHES, "John%");
 * FilterDefinition<UserPropertyRef> rangeFilter =
 *     new FilterDefinition<>(UserPropertyRef.AGE, Op.RANGE, List.of(18, 65));
 * FilterDefinition<UserPropertyRef> nullCheckFilter =
 *     new FilterDefinition<>(UserPropertyRef.EMAIL, Op.IS_NULL, null);
 * }</pre>
 *
 * <p><strong>Operator Categories and Examples:</strong></p>
 *
 * <p><em>Comparison Operators:</em></p>
 * <pre>{@code
 * // Equality and inequality
 * user.age == 25          -> Op.EQ
 * user.status != ACTIVE   -> Op.NE
 *
 * // Numerical comparisons
 * user.age > 18           -> Op.GT
 * user.age >= 21          -> Op.GTE
 * user.salary < 50000     -> Op.LT
 * user.experience <= 5    -> Op.LTE
 * }</pre>
 *
 * <p><em>Text Matching:</em></p>
 * <pre>{@code
 * // Pattern matching (SQL LIKE)
 * user.name LIKE 'John%'     -> Op.MATCHES
 * user.email NOT LIKE '%test%' -> Op.NOT_MATCHES
 * }</pre>
 *
 * <p><em>Collection Operations:</em></p>
 * <pre>{@code
 * // Set membership
 * user.status IN ('ACTIVE', 'PENDING')     -> Op.IN
 * user.role NOT IN ('ADMIN', 'MODERATOR')  -> Op.NOT_IN
 * }</pre>
 *
 * <p><em>Null Checks:</em></p>
 * <pre>{@code
 * // Null value checks (no value parameter needed)
 * user.deletedAt IS NULL      -> Op.IS_NULL
 * user.email IS NOT NULL      -> Op.NOT_NULL
 * }</pre>
 *
 * <p><em>Range Operations:</em></p>
 * <pre>{@code
 * // Range checks (requires exactly 2 values)
 * user.age BETWEEN 18 AND 65     -> Op.RANGE
 * user.salary NOT BETWEEN 30000 AND 50000 -> Op.NOT_RANGE
 * }</pre>
 *
 * <p><strong>HTML-style operator mappings:</strong></p>
 * <ul>
 *     <li>EQ / =</li>
 *     <li>NE / !=</li>
 *     <li>GT / &gt;</li>
 *     <li>GTE / &gt;=</li>
 *     <li>LT / &lt;</li>
 *     <li>LTE / &lt;=</li>
 *     <li>MATCHES / LIKE</li>
 *     <li>NOT_MATCHES / NOT LIKE</li>
 *     <li>IN / IN</li>
 *     <li>NOT_IN / NOT IN</li>
 *     <li>IS_NULL / IS NULL</li>
 *     <li>NOT_NULL / IS NOT NULL</li>
 *     <li>RANGE / BETWEEN</li>
 *     <li>NOT_RANGE / NOT BETWEEN</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public enum Op {

    /** Equality operator: "=" */
    EQ("=", "EQ"),

    /** Not equal operator: "!=" */
    NE("!=", "NE"),

    /** Greater than operator: ">" */
    GT(">", "GT"),

    /** Greater than or equal operator: ">=" */
    GTE(">=", "GTE"),

    /** Less than operator: "&lt;" */
    LT("<", "LT"),

    /** Less than or equal operator: "&lt;=" */
    LTE("<=", "LTE"),

    /** Pattern matching operator: "LIKE" */
    MATCHES("LIKE", "MATCHES"),

    /** Negated pattern matching operator: "NOT LIKE" */
    NOT_MATCHES("NOT LIKE", "NOT_MATCHES"),

    /** Inclusion operator for collections: "IN" */
    IN("IN", "IN"),

    /** Negated inclusion operator: "NOT IN" */
    NOT_IN("NOT IN", "NOT_IN"),

    /** Operator for checking NULL: "IS NULL" */
    IS_NULL("IS NULL", "IS_NULL"),

    /** Operator for checking NOT NULL: "IS NOT NULL" */
    NOT_NULL("IS NOT NULL", "NOT_NULL"),

    /** Range operator: "BETWEEN" */
    RANGE("BETWEEN", "RANGE"),

    /** Negated range operator: "NOT BETWEEN" */
    NOT_RANGE("NOT BETWEEN", "NOT_RANGE"),

    /**
     * Marker operator for custom or external definitions.
     * Should not be used directly.
     */
    CUSTOM(null, null);

    private final String symbol;
    private final String code;

    Op(String symbol, String code) {
        this.symbol = symbol;
        this.code = code;
    }

    /**
     * Returns the display symbol of the operator.
     * <p>
     * Examples include "=", "LIKE", "IN", etc.
     * </p>
     *
     * @return the symbol representing the operator
     */
    public String getSymbol() {
        if (this == CUSTOM)
            throw new UnsupportedOperationException("CUSTOM operator has no fixed symbol.");
        return symbol;
    }

    /**
     * Returns the short code identifier for the operator.
     * <p>
     * Examples include codes such as "EQ" for equal, "MATCHES" for pattern matching,
     * or other custom codes.
     * </p>
     *
     * @return the short code string identifying the operator
     */
    public String getCode() {
        if (this == CUSTOM)
            throw new UnsupportedOperationException("CUSTOM operator has no fixed code.");
        return code;
    }

    /**
     * Finds an {@code Op} by its symbol or code, ignoring case.
     *
     * @param value symbol or code string to search for
     * @return matching {@code Op}, never {@code null}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static Op fromString(String value) {
        String trimmed = value.trim();

        if (Op.CUSTOM.name().equals(trimmed)) return Op.CUSTOM;

        // Standard operators
        for (Op op : values()) {
            if (op.symbol != null && op.symbol.equalsIgnoreCase(trimmed)) return op;
            if (op.code != null && op.code.equalsIgnoreCase(trimmed)) return op;
        }

        return Op.CUSTOM;
    }

    /**
     * Indicates whether this operator requires a filter value.
     * <p>
     * For example, operators like "IS NULL" may not require a value,
     * whereas "EQ" or "LIKE" require values.
     * </p>
     *
     * @return {@code true} if this operator requires an operand value, {@code false} otherwise
     */
    public boolean requiresValue() {
        if (this == CUSTOM)
            throw new UnsupportedOperationException("CUSTOM operator behavior undefined.");
        return this != IS_NULL && this != NOT_NULL;
    }

    /**
     * Checks whether the operator supports multiple values, e.g. for collections or ranges.
     * Supported operators include {@link #IN}, {@link #NOT_IN}, {@link #RANGE}, and {@link #NOT_RANGE}.
     *
     * @return {@code true} if operator supports multiple values, otherwise {@code false}
     */
    public boolean supportsMultipleValues() {
        if (this == CUSTOM)
            throw new UnsupportedOperationException("CUSTOM operator behavior undefined.");
        return this == IN || this == NOT_IN || this == RANGE || this == NOT_RANGE;
    }
}


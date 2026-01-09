package io.github.cyfko.filterql.core.model;

import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;
import io.github.cyfko.filterql.core.validation.PropertyReference;

import java.util.Objects;


/**
 * Immutable representation of a single atomic filter definition.
 * <p>
 * A {@code FilterDefinition} encapsulates the three essential components of a filter:
 * a property reference, an operator code, and an optional value. This record provides
 * type-safe, validated filter specifications that can be composed into complex filter
 * expressions.
 * </p>
 *
 * <h2>Core Components</h2>
 * <dl>
 *   <dt><strong>Property Reference ({@code ref})</strong></dt>
 *   <dd>
 *     An enum value implementing {@link PropertyReference} that identifies the property
 *     to filter. This enum-based approach ensures compile-time safety and prevents typos.
 *   </dd>
 *
 *   <dt><strong>Operator Code ({@code op})</strong></dt>
 *   <dd>
 *     A string identifier for the filtering operation. Can be either:
 *     <ul>
 *       <li><strong>Standard Operator:</strong> One of the 14 operators defined in {@link Op}
 *           (EQ, NE, GT, LT, GTE, LTE, MATCHES, NOT_MATCHES, IN, NOT_IN, IS_NULL, NOT_NULL, RANGE, NOT_RANGE)</li>
 *       <li><strong>Custom Operator:</strong> A custom operator code registered in
 *           {@link OperatorProviderRegistry} for domain-specific filtering logic</li>
 *     </ul>
 *     Operator codes are case-insensitive and automatically normalized to uppercase.
 *   </dd>
 *
 *   <dt><strong>Value ({@code value})</strong></dt>
 *   <dd>
 *     The filter operand. Can be {@code null} for unary operators like {@link Op#IS_NULL}.
 *     Type compatibility with the property and operator is validated during filter resolution.
 *   </dd>
 * </dl>
 *
 * <h2>Validation Strategy</h2>
 * <p>
 * The record's canonical constructor performs <strong>eager validation</strong> of
 * structural correctness:
 * </p>
 * <ul>
 *   <li>✅ Property reference must not be null</li>
 *   <li>✅ Operator code must not be null or blank</li>
 *   <li>✅ Operator code is normalized to uppercase</li>
 *   <li>✅ {@code IS_NULL} operator must have null value</li>
 *   <li>✅ {@code CUSTOM} keyword is reserved and cannot be used</li>
 * </ul>
 * <p>
 * <strong>Deferred Validations</strong> (for performance):
 * </p>
 * <ul>
 *   <li>⏱️ Custom operator existence check → Deferred to {@code FilterContext.toCondition()}</li>
 *   <li>⏱️ Operator-value type compatibility → Deferred to filter resolution</li>
 *   <li>⏱️ Property-operator compatibility → Deferred to {@code PropertyReference} validation</li>
 * </ul>
 *
 * <h2>Immutability & Thread Safety</h2>
 * <p>
 * As a Java {@code record}, {@code FilterDefinition} is inherently immutable and thread-safe.
 * All fields are {@code final} and cannot be modified after construction.
 * </p>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Standard Operators</h3>
 * <pre>{@code
 * // Define property enum
 * enum UserProperty implements PropertyReference {
 *     NAME(String.class, Set.of(Op.EQ, Op.MATCHES)),
 *     EMAIL(String.class, Set.of(Op.EQ, Op.MATCHES)),
 *     AGE(Integer.class, Set.of(Op.GT, Op.LT, Op.RANGE)),
 *     ACTIVE(Boolean.class, Set.of(Op.EQ));
 *
 *     // PropertyReference implementation...
 * }
 *
 * // Using Op enum (recommended)
 * var nameFilter = new FilterDefinition<>(UserProperty.NAME, Op.MATCHES, "%John%");
 * var emailFilter = new FilterDefinition<>(UserProperty.EMAIL, Op.EQ, "alice@example.com");
 * var ageFilter = new FilterDefinition<>(UserProperty.AGE, Op.GTE, 18);
 *
 * // Using operator code string (case-insensitive)
 * var activeFilter = new FilterDefinition<>(UserProperty.ACTIVE, "eq", true);  // Normalized to "EQ"
 *
 * // Unary operator (null value)
 * var nullCheckFilter = new FilterDefinition<>(UserProperty.EMAIL, Op.IS_NULL, null);
 * }</pre>
 *
 * <h3>Collection Values</h3>
 * <pre>{@code
 * // IN operator with collection
 * var statusFilter = new FilterDefinition<>(
 *     UserProperty.STATUS,
 *     Op.IN,
 *     List.of("ACTIVE", "PENDING", "APPROVED")
 * );
 *
 * // RANGE operator with two-element collection
 * var ageRangeFilter = new FilterDefinition<>(
 *     UserProperty.AGE,
 *     Op.RANGE,
 *     List.of(18, 65)
 * );
 * }</pre>
 *
 * <h3>Custom Operators</h3>
 * <pre>{@code
 * // Register custom operator first
 * OperatorProviderRegistry.register("SOUNDEX", new SoundexOperatorProvider());
 *
 * // Use custom operator by code
 * var soundexFilter = new FilterDefinition<>(
 *     UserProperty.NAME,
 *     "SOUNDEX",  // Custom operator code
 *     "Smith"
 * );
 * }</pre>
 *
 * @param <P> the property reference enum type (must extend {@code Enum} and implement {@link PropertyReference})
 * @param ref the property reference identifying which field to filter
 * @param op the operator code (case-insensitive, normalized to uppercase)
 * @param value the filter value (nullable for unary operators like IS_NULL)
 *
 * @throws FilterDefinitionException if validation fails
 *
 * @see PropertyReference
 * @see Op
 * @see OperatorProviderRegistry
 * @see FilterRequest
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public record FilterDefinition<P extends Enum<P> & PropertyReference>(
        P ref,
        String op,
        Object value
) {
    /**
     * Validates the provided filter definition parameters.
     *
     * <p>Throws {@link FilterDefinitionException} if any validation rule is violated, such as:</p>
     * <ul>
     *   <li>{@code ref} is null</li>
     *   <li>{@code op} is null or blank</li>
     *   <li>{@code op} equals {@link Op#IS_NULL} but {@code value} is non-null</li>
     *   <li>{@code op} equals {@link Op#CUSTOM} which is reserved</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> Custom operator registry validation is deferred to actual usage
     * (during {@code FilterContext.toCondition()}) for better performance. This allows
     * creating FilterDefinitions even if the custom operator is not yet registered.</p>
     */
    public FilterDefinition {
        if (ref == null)
            throw new FilterDefinitionException("ref cannot be null");
        if (op == null || op.isBlank())
            throw new FilterDefinitionException("operator cannot be null nor blank");

        op = op.trim().toUpperCase(); // normalize before validations

        if (Op.IS_NULL.name().equals(op) && value != null)
            throw new FilterDefinitionException("value must be null for operator IS_NULL");
        if (Op.CUSTOM.name().equals(op))
            throw new FilterDefinitionException("operator code cannot have value CUSTOM! It is has a special meaning");
        
        // Custom operator registry check deferred to FilterContext.toCondition() for lazy validation
        // This improves performance and allows flexible operator registration timing
    }

    /**
     * Convenience constructor for standard operators (non-CUSTOM).
     *
     * @param ref the property reference enum value
     * @param operator the standard operator enum
     * @param value the filter value
     */
    public FilterDefinition(P ref, Op operator, Object value) {
        this(ref, required(operator).name(), value);
    }

    /**
     * Returns the {@link Op} instance corresponding to the operator code.
     * <p>
     * If this returns {@link Op#CUSTOM}, the caller should use {@link OperatorProviderRegistry}
     * with {@link #op()} for resolving the custom operator behavior.
     * </p>
     *
     * @return the corresponding {@link Op} enum instance
     */
    public Op operator() {
        return Op.fromString(op);
    }

    /**
     * Checks whether this filter definition uses a custom operator.
     * <p>
     * Returns {@code true} if the operator code is not one of the standard operators
     * defined in {@link Op} enum (excluding {@link Op#CUSTOM} itself, which is reserved).
     * Custom operators are resolved through {@link OperatorProviderRegistry}.
     * </p>
     *
     * <p><strong>Example usage:</strong></p>
     * <pre>{@code
     * FilterDefinition<UserRef> standardFilter = new FilterDefinition<>(UserRef.NAME, Op.EQ, "John");
     * FilterDefinition<UserRef> customFilter = new FilterDefinition<>(UserRef.NAME, "SOUNDEX", "John");
     *
     * standardFilter.isCustomOperator(); // false
     * customFilter.isCustomOperator();   // true
     * }</pre>
     *
     * @return {@code true} if this is a custom operator, {@code false} if it's a standard operator
     * @since 4.0.0
     */
    public boolean isCustomOperator() {
        return isCustom(op);
    }

    /**
     * Determines if the given operator code indicates a custom operator.
     *
     * @param key the operator code string
     * @return {@code true} if the code is considered a custom operator code, {@code false} otherwise
     */
    private static boolean isCustom(String key) {
        return Op.fromString(key) == Op.CUSTOM;
    }

    private static Op required(Op op) {
        if (op == null) throw new FilterDefinitionException("operator is required");
        return op;
    }
}


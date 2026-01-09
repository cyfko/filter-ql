package io.github.cyfko.filterql.core.utils;

import io.github.cyfko.filterql.core.validation.Op;

import java.util.Set;

/**
 * Utilities for filter operators, providing predefined sets of operators optimized for different data types.
 * <p>
 * This utility class offers carefully curated collections of operators that are logically
 * appropriate for specific data types. These sets help ensure that only meaningful operations
 * are performed on properties, improving both user experience and query efficiency.
 * </p>
 * 
 * <p><strong>Primary Use Cases:</strong></p>
 * <ul>
 *   <li><strong>Property Reference Definition:</strong> Define supported operators in enums</li>
 *   <li><strong>UI Component Configuration:</strong> Show only relevant operators in filter builders</li>
 *   <li><strong>Validation:</strong> Ensure operators are appropriate for property types</li>
 *   <li><strong>Code Generation:</strong> Generate type-safe filter APIs</li>
 * </ul>
 * 
 * <p><strong>Operator Categories:</strong></p>
 * <table border="1">
 * <caption>Operator Categories by Data Type</caption>
 * <tr><th>Category</th><th>Suitable For</th><th>Key Operators</th></tr>
 * <tr><td>Text Operations</td><td>String, char, text fields</td><td>MATCHES, NOT_MATCHES</td></tr>
 * <tr><td>Numeric Operations</td><td>Numbers, dates, comparable types</td><td>GT, GTE, LT, LTE, RANGE</td></tr>
 * <tr><td>Equality Operations</td><td>All types</td><td>EQ, NE, IN, NOT_IN</td></tr>
 * <tr><td>Null Operations</td><td>All nullable types</td><td>IS_NULL, NOT_NULL</td></tr>
 * </table>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * 
 * <p><em>Property Reference Definition:</em></p>
 * <pre>{@code
 * public enum UserPropertyRef implements PropertyReference {
 *     // Text field - only text-appropriate operations
 *     NAME,
 *     EMAIL,
 *     
 *     // Numeric field - full numeric operations
 *     AGE,
 *     SALARY,
 *     
 *     // Enum field - limited to equality operations
 *     STATUS;
 *     
 *     @Override
 *     public Class<?> getType() {
 *         return switch (this) {
 *             case NAME, EMAIL -> String.class;
 *             case AGE -> Integer.class;
 *             case SALARY -> BigDecimal.class;
 *             case STATUS -> UserStatus.class;
 *         };
 *     }
 *     
 *     @Override
 *     public Set<Op> getSupportedOperators() {
 *         return switch (this) {
 *             case NAME, EMAIL -> OperatorUtils.FOR_TEXT;
 *             case AGE, SALARY -> OperatorUtils.FOR_NUMBER;
 *             case STATUS -> Set.of(Op.EQ, Op.NE, Op.IN, Op.NOT_IN, Op.IS_NULL, Op.NOT_NULL);
 *         };
 *     }
 *     
 *     @Override
 *     public Class<?> getEntityType() {
 *         return User.class;
 *     }
 * }
 * }</pre>
 * 
 * <p><em>Dynamic UI Generation:</em></p>
 * <pre>{@code
 * // Generate filter UI based on property type
 * public List<Op> getAvailableOperators(PropertyReference property) {
 *     if (property.isTextual()) {
 *         return new ArrayList<>(OperatorUtils.FOR_TEXT);
 *     } else if (property.isNumeric()) {
 *         return new ArrayList<>(OperatorUtils.FOR_NUMBER);
 *     } else {
 *         // Fallback to basic operations
 *         return List.of(Op.EQ, Op.NE, Op.IS_NULL, Op.NOT_NULL);
 *     }
 * }
 * }</pre>
 * 
 * <p><em>Validation and Safety:</em></p>
 * <pre>{@code
 * // Validate operator compatibility
 * public boolean isValidOperatorForProperty(PropertyReference property, Op operator) {
 *     if (property.isTextual()) {
 *         return OperatorUtils.FOR_TEXT.contains(operator);
 *     } else if (property.isNumeric()) {
 *         return OperatorUtils.FOR_NUMBER.contains(operator);
 *     }
 *     return false;
 * }
 * }</pre>
 * 
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li><strong>Type Safety:</strong> Only operators that make logical sense for the data type</li>
 *   <li><strong>Immutability:</strong> All sets are immutable to prevent accidental modification</li>
 *   <li><strong>Performance:</strong> Pre-computed sets avoid repeated allocation</li>
 *   <li><strong>Extensibility:</strong> Easy to add new operator sets for custom types</li>
 * </ul>
 * 
 * <p><strong>Customization Example:</strong></p>
 * <pre>{@code
 * // Create custom operator sets for specific business needs
 * public static final Set<Op> FOR_ENUM = Set.of(
 *     Op.EQ, Op.NE, Op.IN, Op.NOT_IN, Op.IS_NULL, Op.NOT_NULL
 * );
 * 
 * public static final Set<Op> FOR_DATE = Set.of(
 *     Op.EQ, Op.NE, Op.GT, Op.GTE, Op.LT, Op.LTE, 
 *     Op.RANGE, Op.NOT_RANGE, Op.IS_NULL, Op.NOT_NULL
 * );
 * 
 * public static final Set<Op> FOR_BOOLEAN = Set.of(
 *     Op.EQ, Op.NE, Op.IS_NULL, Op.NOT_NULL
 * );
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see Op
 * @see io.github.cyfko.filterql.core.validation.PropertyReference
 */
public final class OperatorUtils {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private OperatorUtils() {
        // Utility class - no instantiation allowed
    }

    /**
     * Immutable set of operators applicable to text properties (String, char, CharSequence).
     * <p>
     * This set includes operators that are logically meaningful for textual data:
     * </p>
     * <ul>
     *   <li><strong>Equality:</strong> {@link Op#EQ}, {@link Op#NE} - exact matching</li>
     *   <li><strong>Pattern Matching:</strong> {@link Op#MATCHES}, {@link Op#NOT_MATCHES} - SQL LIKE operations</li>
     *   <li><strong>Set Operations:</strong> {@link Op#IN}, {@link Op#NOT_IN} - membership tests</li>
     *   <li><strong>Null Checks:</strong> {@link Op#IS_NULL}, {@link Op#NOT_NULL} - nullable handling</li>
     * </ul>
     * 
     * <p><strong>Notable Exclusions:</strong></p>
     * <ul>
     *   <li>Comparison operators (GT, LT, etc.) - not meaningful for text</li>
     *   <li>Range operators - text ranges are rarely useful</li>
     * </ul>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * public enum UserPropertyRef implements PropertyReference {
     *     NAME(String.class, OperatorUtils.FOR_TEXT),
     *     EMAIL(String.class, OperatorUtils.FOR_TEXT),
     *     DESCRIPTION(String.class, OperatorUtils.FOR_TEXT);
     *     
     *     // Implementation...
     * }
     * }</pre>
     * 
     * @see Op#MATCHES
     * @see Op#NOT_MATCHES
     */
    public static final Set<Op> FOR_TEXT = Set.of(
            Op.EQ, Op.NE,
            Op.MATCHES, Op.NOT_MATCHES,
            Op.IN, Op.NOT_IN,
            Op.IS_NULL, Op.NOT_NULL
    );

    /**
     * Immutable set of operators applicable to numeric properties (Number, int, long, double, BigDecimal, etc.).
     * <p>
     * This comprehensive set includes all operators that are meaningful for numeric data:
     * </p>
     * <ul>
     *   <li><strong>Equality:</strong> {@link Op#EQ}, {@link Op#NE} - exact value matching</li>
     *   <li><strong>Comparisons:</strong> {@link Op#GT}, {@link Op#GTE}, {@link Op#LT}, {@link Op#LTE} - magnitude comparisons</li>
     *   <li><strong>Ranges:</strong> {@link Op#RANGE}, {@link Op#NOT_RANGE} - between operations</li>
     *   <li><strong>Set Operations:</strong> {@link Op#IN}, {@link Op#NOT_IN} - membership in numeric collections</li>
     *   <li><strong>Null Checks:</strong> {@link Op#IS_NULL}, {@link Op#NOT_NULL} - handle nullable numbers</li>
     * </ul>
     * 
     * <p><strong>Suitable For:</strong></p>
     * <ul>
     *   <li>Primitive numbers: int, long, float, double</li>
     *   <li>Wrapper classes: Integer, Long, Float, Double</li>
     *   <li>Big numbers: BigInteger, BigDecimal</li>
     *   <li>Date/time types: LocalDate, LocalDateTime, Instant</li>
     *   <li>Any Comparable numeric type</li>
     * </ul>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <pre>{@code
     * public enum ProductPropertyRef implements PropertyReference {
     *     PRICE(BigDecimal.class, OperatorUtils.FOR_NUMBER),
     *     QUANTITY(Integer.class, OperatorUtils.FOR_NUMBER),
     *     WEIGHT(Double.class, OperatorUtils.FOR_NUMBER),
     *     CREATED_DATE(LocalDateTime.class, OperatorUtils.FOR_NUMBER); // Dates are comparable
     *     
     *     // Implementation...
     * }
     * 
     * // Example filter usage:
     * // price > 100.00
     * FilterDefinition<ProductPropertyRef> priceFilter = new FilterDefinition<>(
     *     ProductPropertyRef.PRICE, Op.GT, new BigDecimal("100.00"));
     * 
     * // quantity between 10 and 50
     * FilterDefinition<ProductPropertyRef> quantityRange = new FilterDefinition<>(
     *     ProductPropertyRef.QUANTITY, Op.RANGE, List.of(10, 50));
     * }</pre>
     * 
     * @see Op#GT
     * @see Op#RANGE
     * @see Comparable
     */
    public static final Set<Op> FOR_NUMBER = Set.of(
            Op.EQ, Op.NE,
            Op.GT, Op.GTE,
            Op.LT, Op.LTE,
            Op.RANGE, Op.NOT_RANGE,
            Op.IN, Op.NOT_IN,
            Op.IS_NULL, Op.NOT_NULL
    );
}


package io.github.cyfko.filterql.core.api;

import io.github.cyfko.filterql.core.utils.OperatorValidationUtils;
import io.github.cyfko.filterql.core.utils.ValidationResult;

import java.util.Set;

/**
 * Interface representing a property reference in a dynamic filtering context.
 * <p>
 * Developers must create their own enums implementing this interface to define
 * the accessible properties and their characteristics on their entities.
 * </p>
 * <p>
 * {@code PropertyReference} only contains the logical definition of the property (type, supported operators).
 * Each adapter is responsible for interpreting this interface and building the appropriate conditions.
 * This distinction offers great flexibility, allowing for example a single property reference
 * to correspond to multiple fields or complex conditions.
 * </p>
 * <p>
 * For validation of operators and values, use the {@link OperatorValidationUtils} utility class.
 * </p>
 * <p><b>Usage example:</b></p>
 * <pre>{@code
 * public enum UserPropertyRef implements PropertyReference {
 *     USER_NAME,
 *     USER_AGE,
 *     USER_STATUS;
 *
 *     @Override
 *     public Class<?> getType() {
 *         return switch (this) {
 *             case USER_NAME -> String.class;
 *             case USER_AGE -> Integer.class;
 *             case USER_STATUS -> UserStatus.class;
 *         };
 *     }
 *
 *     @Override
 *     public Set<Op> getSupportedOperators() {
 *         return switch (this) {
 *             case USER_NAME -> Set.of(Op.MATCHES, Op.EQ, Op.IN);
 *             case USER_AGE -> Set.of(Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
 *             case USER_STATUS -> Set.of(Op.EQ, Op.NE, Op.IN);
 *         };
 *     }
 *
 *     @Override
 *     public Class<?> getEntityType() {
 *         return User.class;
 *     }
 * }
 * 
 * // Validation example
 * PropertyReference prop = UserPropertyRef.USER_AGE;
 * ValidationResult result = OperatorValidationUtils.validateValueForOperator(
 *     Op.EQ, 25, prop.getType()
 * );
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see OperatorValidationUtils
 * @see ValidationResult
 */
public interface PropertyReference {

    /**
     * Returns the Java type of the represented property.
     *
     * @return the Java class representing the property type (e.g., String.class, Integer.class)
     */
    Class<?> getType();

    /**
     * Returns the unmodifiable collection of default operators supported by this property.
     *
     * @return an immutable {@link Set} of default operators supported by the property
     */
    Set<Op> getSupportedOperators();

    /**
     * Returns the Java type of the target {@link jakarta.persistence.Entity}.
     */
    Class<?> getEntityType();
}
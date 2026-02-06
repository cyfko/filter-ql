package io.github.cyfko.filterql.spring.support;

import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.spring.processor.model.SupportedType;

/**
 * Provides the default mapping between supported field types and allowed FilterQL operators.
 * <p>
 * Used by the FilterQL Spring Boot starter to determine which operators are valid for a given
 * property type (e.g., String, Number, Enum) during filter validation and query construction.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Automatic operator assignment for generated property references</li>
 *   <li>Validation of filter definitions at construction and runtime</li>
 *   <li>Extension point for custom operator strategies</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Stateless utility; safe for concurrent use.
 * </p>
 *
 * <h2>Extension Points</h2>
 * <ul>
 *   <li>Override or extend for domain-specific operator mappings</li>
 *   <li>Inject custom strategies via Spring configuration</li>
 * </ul>
 *
 * @author cyfko
 * @since 1.0
 */
public class DefaultOperatorStrategy {

    /**
     * Returns the default set of allowed operators for the given field type.
     * <p>
     * Uses {@link SupportedType#fromClass(Class)} to determine the supported type.
     * </p>
     *
     * @param fieldType Java class of the field
     * @return an array of allowed {@link Op} operators
     */
    public Op[] getDefaultOperators(Class<?> fieldType) {
        SupportedType supportedType = SupportedType.fromClass(fieldType);
        return getOperatorsForSupportedType(supportedType);
    }

    /**
     * Returns the default set of allowed operators for the given supported type.
     *
     * @param supportedType supported type of the field
     * @return an array of allowed {@link Op} operators
     */
    public Op[] getDefaultOperators(SupportedType supportedType) {
        return getOperatorsForSupportedType(supportedType);
    }

    /**
     * Returns the set of allowed operators for the specified {@link SupportedType}.
     * <p>
     * The mapping is as follows:
     * <ul>
     *   <li>STRING: EQ, NE, MATCHES, NOT_MATCHES, IN</li>
     *   <li>INTEGER, LONG, DOUBLE, FLOAT, BIG_INTEGER, BIG_DECIMAL: EQ, NE, GT, GTE, LT, LTE, IN, RANGE</li>
     *   <li>BOOLEAN: EQ, NE</li>
     *   <li>LOCAL_DATE, LOCAL_DATE_TIME: EQ, NE, GT, GTE, LT, LTE, RANGE</li>
     *   <li>UUID: EQ, IN</li>
     *   <li>ENUM: EQ, NE, IN</li>
     *   <li>Default: EQ, NE</li>
     * </ul>
     * </p>
     *
     * @param supportedType supported type of the field
     * @return set of allowed {@link Op} operators
     */
    public Op[] getOperatorsForSupportedType(SupportedType supportedType) {
        return switch (supportedType) {
            case STRING -> new Op[]{ Op.EQ, Op.NE, Op.MATCHES, Op.NOT_MATCHES, Op.IN };
            case INTEGER, LONG, DOUBLE, FLOAT, BIG_INTEGER, BIG_DECIMAL -> new Op[]{ Op.EQ, Op.NE, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.IN, Op.RANGE };
            case BOOLEAN -> new Op[]{ Op.EQ, Op.NE };
            case LOCAL_DATE, LOCAL_DATE_TIME -> new Op[]{ Op.EQ, Op.NE, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE };
            case UUID -> new Op[]{ Op.EQ, Op.IN };
            case ENUM -> new Op[]{ Op.EQ, Op.NE, Op.IN };
            default -> new Op[]{ Op.EQ, Op.NE};
        };
    }
}

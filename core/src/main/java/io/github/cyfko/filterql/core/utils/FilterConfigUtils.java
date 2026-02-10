package io.github.cyfko.filterql.core.utils;

import io.github.cyfko.filterql.core.config.EnumMatchMode;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.config.NullValuePolicy;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FilterConfigUtils {
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private FilterConfigUtils() {
        // Utility class - no instantiation allowed
    }

    /**
     * Ensures null value handling according to configured policy.
     */
    public static <P extends Enum<P> & PropertyReference> FilterDefinition<P> ensureNullValuePolicy(FilterConfig config, FilterDefinition<P> definition) {
        Op operator = definition.operator();
        Object originalValue = definition.value();

        if (originalValue == null && operator != Op.IS_NULL && operator != Op.NOT_NULL) {
            NullValuePolicy nullValuePolicy = config.getNullValuePolicy();
            switch (nullValuePolicy) {
                case STRICT_EXCEPTION -> throw new FilterValidationException(
                        String.format("Null value not allowed for operator %s (policy STRICT_EXCEPTION)", operator)
                );
                case COERCE_TO_IS_NULL -> {
                    if (operator == Op.EQ) {
                        P castRef = definition.ref();
                        return new FilterDefinition<>(castRef, Op.IS_NULL, null);
                    } else if (operator == Op.NE) {
                        P castRef = definition.ref();
                        return new FilterDefinition<>(castRef, Op.NOT_NULL, null);
                    } else {
                        throw new FilterValidationException(
                                String.format("Cannot coerce null value for operator %s under COERCE_TO_IS_NULL policy", operator)
                        );
                    }
                }
                case IGNORE_FILTER -> {
                    // Create a neutral definition (always true predicate)
                    return null;
                }
            }
        }

        return definition;
    }

    /**
     * If the property is an enum and the provided value is a String or collection of Strings, convert them
     * to enum constants according to the configured EnumMatchMode. Returns the (possibly new) definition.
     */
    public static <P extends Enum<P> & PropertyReference> FilterDefinition<P> coerceEnumValueIfNeeded(FilterConfig config, FilterDefinition<P> def) {
        if (def.value() == null) return def;

        Class<?> type = def.ref().getType();
        if (!type.isEnum()) return def;

        Object raw = def.value();
        EnumMatchMode mode = config.getEnumMatchMode();

        if (raw instanceof String s) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) type;
            Object enumVal = convertToEnumRaw(enumType, s, mode);
            return new FilterDefinition<>(def.ref(), def.operator(), enumVal);
        }

        if (raw instanceof Collection<?> col) {
            List<Object> converted = new ArrayList<>(col.size());
            for (Object item : col) {
                if (item instanceof String es) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) type;
                    converted.add(convertToEnumRaw(enumType, es, mode));
                } else if (type.isInstance(item)) {
                    converted.add(item);
                } else {
                    throw new IllegalArgumentException("Incompatible collection element for enum property: " + item);
                }
            }
            return new FilterDefinition<>(def.ref(), def.operator(), converted);
        }

        return def;
    }

    /** Internal helper to centralize an unavoidable generic cast with documentation. */
    @SuppressWarnings("unchecked")
    private static <P extends Enum<P> & PropertyReference>  FilterDefinition<P> castUnchecked(FilterDefinition<?> def) {
        return (FilterDefinition<P>) def; // Safe: validated enum class compatibility earlier
    }

    /** Raw enum coercion helper where generic E cannot be expressed at call site. */
    public static Enum<?> convertToEnumRaw(Class<? extends Enum<?>> enumType, String text, EnumMatchMode mode) {
        // Use raw loop to avoid generic mismatch; delegate to generic helper via capture
        @SuppressWarnings({"unchecked","rawtypes"})
        Enum<?> val = convertToEnumGeneric((Class) enumType, text, mode);
        return val;
    }

    private static <E extends Enum<E>> E convertToEnumGeneric(Class<E> enumClass, Object value, EnumMatchMode matchMode) {
        String strValue = value.toString();
        try {
            return Enum.valueOf(enumClass, strValue);
        } catch (IllegalArgumentException e) {
            if (matchMode == EnumMatchMode.CASE_INSENSITIVE) {
                for (E c : enumClass.getEnumConstants()) {
                    if (c.name().equalsIgnoreCase(strValue)) return c;
                }
            }
            throw new IllegalArgumentException("Invalid value '" + strValue + "' for enum " + enumClass.getName());
        }
    }
}

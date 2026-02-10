package io.github.cyfko.filterql.spring.processor.model;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import io.github.cyfko.filterql.core.api.Op;

/**
 * Métadonnées d'un champ filtrable (régulier ou virtuel).
 */
public record FieldMetadata(
        String referredAs,
        String name,
        Op[] operators,
        VirtualFieldDetails virtualFieldDetails
) {
    public static FieldMetadata regularField(
            String refName,
            String name,
            Op[] operators
    ) {
        return new FieldMetadata(refName, name, operators, null);
    }

    public static FieldMetadata virtualField(
            String refName,
            String methodName,
            Op[] operators,
            String resolverClassName,
            String beanName,
            boolean isStatic
    ) {
        return new FieldMetadata(
                refName,
                methodName,
                operators,
                new VirtualFieldDetails(resolverClassName, beanName, isStatic)
        );
    }

    public boolean isVirtual() { return virtualFieldDetails != null; }

    /**
     * Génère une représentation exécutable Java du FieldMetadata.
     * Exemple : FieldMetadata.regularField("id", SupportedType.NUMBER, Set.of(Op.EQ, Op.GT), "id", ...)
     */
    public String toSourceCode() {
        String ops = operators == null || operators.length == 0
                ? "new Op[]{}"
                : "new Op[]{" + String.join(", ", Arrays.stream(operators).map(op -> "Op." + op.name()).toList()) + "}";

        Function<String,String> safe = s -> s == null ? "null" : "\"" + s.replace("\"", "\\\"") + "\"";

        if (isVirtual()) {
            var v = virtualFieldDetails;
            return String.format(
                    "FieldMetadata.virtualField(%s, SupportedType.%s, %s, %s, %s)",
                    safe.apply(referredAs),
                    name,
                    ops,
                    safe.apply(v.resolverClassName()),
                    v.isStatic()
            );
        } else {
            return String.format(
                    "FieldMetadata.regularField(%s, SupportedType.%s, %s)",
                    safe.apply(referredAs),
                    name,
                    ops
            );
        }
    }

    /**
     * Details for virtual fields.
     * @param resolverClassName Fully qualified class name containing the resolver method
     * @param beanName Spring bean name (null for static methods)
     * @param isStatic Whether the method is static
     */
    public record VirtualFieldDetails(String resolverClassName, String beanName, boolean isStatic){ }
}
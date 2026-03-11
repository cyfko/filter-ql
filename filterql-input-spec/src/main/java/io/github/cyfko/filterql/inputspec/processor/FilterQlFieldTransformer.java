package io.github.cyfko.filterql.inputspec.processor;

import io.github.cyfko.inputspec.spi.FieldContext;
import io.github.cyfko.inputspec.spi.FieldTransformer;

import javax.lang.model.element.AnnotationMirror;
import java.util.List;
import java.util.Optional;

/**
 * {@link FieldTransformer} implementation for FilterQL.
 *
 * <p>Activates when a @FieldMeta element also carries {@code @ExposedAs}.
 * Transforms it into a DIFSP {@code OBJECT} field with two sub-fields:
 * <ul>
 *   <li>{@code op}    — CLOSED domain of allowed operators</li>
 *   <li>{@code value} — typed value, CLOSED domain for enums or RANGE</li>
 * </ul>
 *
 * <p>No import of FilterQL classes — all annotation access goes through
 * {@link FieldContext#findAnnotation(String)} using qualified name strings.
 */
public final class FilterQlFieldTransformer implements FieldTransformer {

    private static final String EXPOSED_AS =
            "io.github.cyfko.filterql.annotation.ExposedAs";

    // ─── FieldTransformer ─────────────────────────────────────────────────────

    @Override
    public boolean supports(FieldContext ctx) {
        return ctx.hasAnnotation(EXPOSED_AS);
    }

    @Override
    public String fieldRefName(FieldContext ctx) {
        return ctx.findAnnotation(EXPOSED_AS)
                .flatMap(m -> ctx.annotationStringValue(m, "value"))
                .filter(s -> !s.isBlank())
                .orElse(ctx.fieldName().toUpperCase());
    }

    @Override
    public String transform(FieldContext ctx) {
        AnnotationMirror exposedAs = ctx.findAnnotation(EXPOSED_AS).orElseThrow();
        String ref       = fieldRefName(ctx);
        List<String> ops = ctx.annotationEnumList(exposedAs, "operators");

        String displayName = resolveDisplayName(ctx, ref);
        String description = resolveDescription(ctx);

        String opSubField    = buildOpSubField(ctx, ops);
        String valueSubField = buildValueSubField(ctx, ops);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": ").append(quoted(ref)).append(",\n");
        sb.append("  \"displayName\": ").append(displayName).append(",\n");
        if (description != null) {
            sb.append("  \"description\": ").append(description).append(",\n");
        }
        sb.append("  \"dataType\": \"OBJECT\",\n");
        sb.append("  \"expectMultipleValues\": false,\n");
        sb.append("  \"required\": false,\n");
        sb.append("  \"subFields\": [\n");
        sb.append("    ").append(indent(opSubField, 4)).append(",\n");
        sb.append("    ").append(indent(valueSubField, 4)).append("\n");
        sb.append("  ],\n");
        sb.append("  \"constraints\": []\n");
        sb.append("}");
        return sb.toString();
    }

    // ─── "op" sub-field ───────────────────────────────────────────────────────

    private String buildOpSubField(FieldContext ctx, List<String> ops) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < ops.size(); i++) {
            if (i > 0) items.append(",\n          ");
            String op = ops.get(i);
            items.append("{ \"value\": ").append(quoted(op))
                 .append(", \"label\": ").append(opLabel(op, ctx.locales()))
                 .append(" }");
        }
        return "{\n" +
               "  \"name\": \"op\",\n" +
               "  \"displayName\": " + localizedString("Opérateur", "Operator", ctx.locales()) + ",\n" +
               "  \"dataType\": \"STRING\",\n" +
               "  \"expectMultipleValues\": false,\n" +
               "  \"required\": true,\n" +
               "  \"valuesEndpoint\": {\n" +
               "    \"protocol\": \"INLINE\",\n" +
               "    \"mode\": \"CLOSED\",\n" +
               "    \"items\": [\n" +
               "      " + items + "\n" +
               "    ]\n" +
               "  },\n" +
               "  \"constraints\": []\n" +
               "}";
    }

    // ─── "value" sub-field ────────────────────────────────────────────────────

    private String buildValueSubField(FieldContext ctx, List<String> ops) {
        boolean hasRange    = ops.contains("RANGE");
        boolean hasIn       = ops.contains("IN");
        boolean multiValues = ctx.isMultiValued() || hasIn || hasRange;

        String dataType    = ctx.difspDataType();
        String formatHint  = ctx.formatHint().orElse(null);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"value\",\n");
        sb.append("  \"displayName\": ").append(localizedString("Valeur", "Value", ctx.locales())).append(",\n");
        if (hasRange) {
            sb.append("  \"description\": ").append(rangeDescription(ctx.locales())).append(",\n");
        }
        sb.append("  \"dataType\": \"").append(dataType).append("\",\n");
        sb.append("  \"expectMultipleValues\": ").append(multiValues).append(",\n");
        sb.append("  \"required\": true,\n");

        if (formatHint != null) {
            sb.append("  \"formatHint\": ").append(quoted(formatHint)).append(",\n");
        }

        // Enum → closed domain from @FieldMeta.valuesSource or auto-resolved
        if (ctx.isEnum()) {
            sb.append("  \"valuesEndpoint\": ").append(buildEnumValuesEndpoint(ctx)).append(",\n");
        } else {
            // Check if @FieldMeta carries an explicit INLINE valuesSource
            Optional<String> inlineEndpoint = extractInlineFromFieldMeta(ctx);
            if (inlineEndpoint.isPresent()) {
                sb.append("  \"valuesEndpoint\": ").append(inlineEndpoint.get()).append(",\n");
            }
        }

        sb.append("  \"constraints\": ").append(buildValueConstraints(ctx, ops)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    // ─── Enum domain (auto-resolved or from @FieldMeta) ──────────────────────

    private String buildEnumValuesEndpoint(FieldContext ctx) {
        // Prefer explicit @FieldMeta.valuesSource items if declared
        Optional<String> explicit = extractInlineFromFieldMeta(ctx);
        if (explicit.isPresent()) return explicit.get();

        // Auto-resolve from enum constants
        List<String> constants = ctx.enumConstants();
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < constants.size(); i++) {
            if (i > 0) items.append(",\n      ");
            String c = constants.get(i);
            items.append("{ \"value\": ").append(quoted(c))
                 .append(", \"label\": { \"default\": ").append(quoted(c)).append(" } }");
        }
        return "{\n" +
               "  \"protocol\": \"INLINE\",\n" +
               "  \"mode\": \"CLOSED\",\n" +
               "  \"items\": [\n" +
               "    " + items + "\n" +
               "  ]\n" +
               "}";
    }

    /**
     * Extracts the INLINE valuesEndpoint from @FieldMeta.valuesSource if present.
     * The items are passed through verbatim from what InputSpec already declared —
     * this is the key integration point: we reuse what the developer declared.
     */
    private Optional<String> extractInlineFromFieldMeta(FieldContext ctx) {
        Optional<AnnotationMirror> fieldMeta = ctx.fieldMeta();
        if (fieldMeta.isEmpty()) return Optional.empty();

        List<AnnotationMirror> valuesSources =
                ctx.annotationMirrorList(fieldMeta.get(), "valuesSource");
        if (valuesSources.isEmpty()) return Optional.empty();

        AnnotationMirror vs = valuesSources.get(0);
        String protocol = ctx.annotationStringValue(vs, "protocol").orElse("");
        if (!protocol.equals("INLINE")) return Optional.empty();

        String mode = ctx.annotationStringValue(vs, "mode").orElse("CLOSED");
        List<AnnotationMirror> inlineItems = ctx.annotationMirrorList(vs, "items");
        if (inlineItems.isEmpty()) return Optional.empty();

        StringBuilder items = new StringBuilder();
        for (int i = 0; i < inlineItems.size(); i++) {
            if (i > 0) items.append(",\n      ");
            AnnotationMirror item = inlineItems.get(i);
            String value = ctx.annotationStringValue(item, "value").orElse("");
            String label = ctx.annotationStringValue(item, "label").orElse(value);
            items.append("{ \"value\": ").append(quoted(value))
                 .append(", \"label\": { \"default\": ").append(quoted(label)).append(" } }");
        }

        return Optional.of("{\n" +
                "  \"protocol\": \"INLINE\",\n" +
                "  \"mode\": \"" + mode + "\",\n" +
                "  \"items\": [\n" +
                "    " + items + "\n" +
                "  ]\n" +
                "}");
    }

    // ─── Value constraints ────────────────────────────────────────────────────

    private String buildValueConstraints(FieldContext ctx, List<String> ops) {
        boolean hasRange = ops.contains("RANGE");
        if (!hasRange) return "[]";

        String desc = rangeCardinalityDescription(ctx.locales());
        return "[\n" +
               "  {\n" +
               "    \"name\": \"rangeCardinality\",\n" +
               "    \"type\": \"custom\",\n" +
               "    \"params\": { \"key\": \"filterql-range-cardinality\" },\n" +
               "    \"description\": " + desc + "\n" +
               "  }\n" +
               "]";
    }

    // ─── Display name / description ───────────────────────────────────────────

    private String resolveDisplayName(FieldContext ctx, String ref) {
        return ctx.fieldMeta()
                .flatMap(m -> ctx.annotationStringValue(m, "displayName"))
                .filter(s -> !s.isBlank())
                .map(s -> "{ \"default\": " + quoted(s) + " }")
                .orElse("{ \"default\": " + quoted(ref) + " }");
    }

    private String resolveDescription(FieldContext ctx) {
        return ctx.fieldMeta()
                .flatMap(m -> ctx.annotationStringValue(m, "description"))
                .filter(s -> !s.isBlank())
                .map(s -> "{ \"default\": " + quoted(s) + " }")
                .orElse(null);
    }

    // ─── Localized strings ────────────────────────────────────────────────────

    private String localizedString(String fr, String en, List<String> locales) {
        StringBuilder sb = new StringBuilder("{ \"default\": ").append(quoted(fr));
        for (String loc : locales) {
            if (loc.startsWith("fr")) sb.append(", \"fr\": ").append(quoted(fr));
            if (loc.startsWith("en")) sb.append(", \"en\": ").append(quoted(en));
        }
        sb.append(" }");
        return sb.toString();
    }

    private String opLabel(String op, List<String> locales) {
        String fr = OP_FR.getOrDefault(op, op);
        String en = OP_EN.getOrDefault(op, op);
        return localizedString(fr, en, locales);
    }

    private String rangeDescription(List<String> locales) {
        return localizedString(
                "Pour GTE/LTE: une valeur. Pour RANGE: deux valeurs [début, fin].",
                "For GTE/LTE: one value. For RANGE: two values [from, to].",
                locales);
    }

    private String rangeCardinalityDescription(List<String> locales) {
        return localizedString(
                "Si op=RANGE, exactement 2 valeurs requises [début, fin]. Sinon exactement 1.",
                "If op=RANGE, exactly 2 values required [from, to]. Otherwise exactly 1.",
                locales);
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private static String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String indent(String json, int spaces) {
        String pad = " ".repeat(spaces);
        return json.replace("\n", "\n" + pad);
    }

    // ─── Operator label maps ──────────────────────────────────────────────────

    private static final Map<String, String> OP_FR = new LinkedHashMap<>();
    private static final Map<String, String> OP_EN = new LinkedHashMap<>();

    static {
        OP_FR.put("EQ",         "égal à");
        OP_FR.put("GT",         "supérieur à");
        OP_FR.put("GTE",        "supérieur ou égal à");
        OP_FR.put("LT",         "inférieur à");
        OP_FR.put("LTE",        "inférieur ou égal à");
        OP_FR.put("MATCHES",    "contient");
        OP_FR.put("STARTS_WITH","commence par");
        OP_FR.put("ENDS_WITH",  "se termine par");
        OP_FR.put("IN",         "est parmi");
        OP_FR.put("NOT_IN",     "n'est pas parmi");
        OP_FR.put("RANGE",      "est compris entre");

        OP_EN.put("EQ",         "equal to");
        OP_EN.put("GT",         "greater than");
        OP_EN.put("GTE",        "at least");
        OP_EN.put("LT",         "less than");
        OP_EN.put("LTE",        "at most");
        OP_EN.put("MATCHES",    "contains");
        OP_EN.put("STARTS_WITH","starts with");
        OP_EN.put("ENDS_WITH",  "ends with");
        OP_EN.put("IN",         "is one of");
        OP_EN.put("NOT_IN",     "is not one of");
        OP_EN.put("RANGE",      "is between");
    }
}

package io.github.cyfko.filterql.inputspec.processor;

import io.github.cyfko.inputspec.spi.FormContributor;
import io.github.cyfko.inputspec.spi.FormContext;

import java.util.*;

/**
 * {@link FormContributor} implementation for FilterQL.
 *
 * <p>Activates when at least one field was claimed by {@link FilterQlFieldTransformer}.
 * Appends three synthetic fields to the FormSpec:
 * <ul>
 *   <li>{@code combineWith}  — boolean DSL combining filter refs</li>
 *   <li>{@code projection}   — optional field selection</li>
 *   <li>{@code pagination}   — page, size, sort[]</li>
 * </ul>
 * and one cross-constraint ensuring combineWith refs match provided filters.
 */
public final class FilterQlFormContributor implements FormContributor {

    @Override
    public boolean supports(FormContext ctx) {
        return !ctx.transformedFieldRefs().isEmpty();
    }

    @Override
    public List<String> additionalFields(FormContext ctx) {
        return List.of(
                buildCombineWithField(ctx),
                buildProjectionField(ctx),
                buildPaginationField(ctx)
        );
    }

    @Override
    public List<String> additionalCrossConstraints(FormContext ctx) {
        return List.of(buildRefsExistConstraint(ctx));
    }

    // ─── combineWith ──────────────────────────────────────────────────────────

    private String buildCombineWithField(FormContext ctx) {
        List<String> refs = ctx.transformedFieldRefs();
        String refsStr    = String.join(", ", refs);
        String example    = refs.size() >= 2
                ? refs.get(0) + " & " + refs.get(1)
                : refs.isEmpty() ? "AND" : refs.get(0);

        String descFr = "Combinez les filtres avec & (ET), | (OU), ! (NON), et des parenthèses. " +
                        "Identifiants: " + refsStr + ". Exemple: " + example + ". " +
                        "Raccourcis: AND (tous), OR (au moins un).";
        String descEn = "Combine filters with & (AND), | (OR), ! (NOT) and parentheses. " +
                        "Identifiers: " + refsStr + ". Example: " + example + ". " +
                        "Shortcuts: AND (all), OR (any).";

        return "{\n" +
               "  \"name\": \"combineWith\",\n" +
               "  \"displayName\": " + ls("Combinaison logique", "Logical combination", ctx) + ",\n" +
               "  \"description\": " + ls(descFr, descEn, ctx) + ",\n" +
               "  \"dataType\": \"STRING\",\n" +
               "  \"expectMultipleValues\": false,\n" +
               "  \"required\": true,\n" +
               "  \"valuesEndpoint\": {\n" +
               "    \"protocol\": \"INLINE\",\n" +
               "    \"mode\": \"SUGGESTIONS\",\n" +
               "    \"items\": [\n" +
               "      { \"value\": \"AND\", \"label\": " + ls("Tous les filtres (ET)", "All filters (AND)", ctx) + " },\n" +
               "      { \"value\": \"OR\",  \"label\": " + ls("Au moins un filtre (OU)", "Any filter (OR)", ctx) + " }\n" +
               "    ]\n" +
               "  },\n" +
               "  \"constraints\": [\n" +
               "    {\n" +
               "      \"name\": \"boolDslSyntax\",\n" +
               "      \"type\": \"custom\",\n" +
               "      \"params\": { \"key\": \"filterql-boolean-dsl\" },\n" +
               "      \"errorMessage\": " + ls(
                       "Syntaxe invalide. Utilisez les opérateurs & | ! et les parenthèses.",
                       "Invalid syntax. Use & | ! operators and parentheses.", ctx) + ",\n" +
               "      \"description\": " + ls(
                       "Valide selon la grammaire EBNF du protocole FilterQL.",
                       "Validates against the FilterQL protocol EBNF grammar.", ctx) + "\n" +
               "    }\n" +
               "  ]\n" +
               "}";
    }

    // ─── projection ───────────────────────────────────────────────────────────

    private String buildProjectionField(FormContext ctx) {
        StringBuilder items = new StringBuilder();
        List<String> fields = ctx.allFieldNames();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) items.append(",\n      ");
            String f = fields.get(i);
            items.append("{ \"value\": ").append(q(f))
                 .append(", \"label\": { \"default\": ").append(q(f)).append(" } }");
        }

        return "{\n" +
               "  \"name\": \"projection\",\n" +
               "  \"displayName\": " + ls("Champs à retourner", "Fields to return", ctx) + ",\n" +
               "  \"description\": " + ls(
                       "Sélectionnez les champs à inclure dans la réponse. " +
                       "Syntaxe collection: collection[size=10,sort=field:desc].champ1,champ2",
                       "Select fields to include in the response. " +
                       "Collection syntax: collection[size=10,sort=field:desc].field1,field2", ctx) + ",\n" +
               "  \"dataType\": \"STRING\",\n" +
               "  \"expectMultipleValues\": true,\n" +
               "  \"required\": false,\n" +
               "  \"valuesEndpoint\": {\n" +
               "    \"protocol\": \"INLINE\",\n" +
               "    \"mode\": \"SUGGESTIONS\",\n" +
               "    \"items\": [\n" +
               "      " + items + "\n" +
               "    ]\n" +
               "  },\n" +
               "  \"constraints\": []\n" +
               "}";
    }

    // ─── pagination ───────────────────────────────────────────────────────────

    private String buildPaginationField(FormContext ctx) {
        int defSize = ctx.defaultPageSize();
        int maxSize = ctx.maxPageSize();

        // Collect sortable fields: those whose DIFSP type is NUMBER, DATE or STRING
        List<String> sortable = new ArrayList<>();
        for (String ref : ctx.transformedFieldRefs()) {
            ctx.transformedFieldJson(ref).ifPresent(json -> {
                // Heuristic: if the value subfield's dataType is not OBJECT, it's sortable
                if (json.contains("\"NUMBER\"") || json.contains("\"DATE\"")
                        || json.contains("\"STRING\"")) {
                    // Use the logical camelCase name for sort field value
                    String logicalName = toCamelCase(ref);
                    if (!sortable.contains(logicalName)) sortable.add(logicalName);
                }
            });
        }

        StringBuilder sortItems = new StringBuilder();
        for (int i = 0; i < sortable.size(); i++) {
            if (i > 0) sortItems.append(",\n          ");
            String f = sortable.get(i);
            sortItems.append("{ \"value\": ").append(q(f))
                     .append(", \"label\": { \"default\": ").append(q(f)).append(" } }");
        }

        return "{\n" +
               "  \"name\": \"pagination\",\n" +
               "  \"displayName\": " + ls("Pagination", "Pagination", ctx) + ",\n" +
               "  \"dataType\": \"OBJECT\",\n" +
               "  \"expectMultipleValues\": false,\n" +
               "  \"required\": false,\n" +
               "  \"subFields\": [\n" +
               "    {\n" +
               "      \"name\": \"page\",\n" +
               "      \"displayName\": " + ls("Page (0-indexée)", "Page (0-indexed)", ctx) + ",\n" +
               "      \"dataType\": \"NUMBER\",\n" +
               "      \"expectMultipleValues\": false,\n" +
               "      \"required\": false,\n" +
               "      \"constraints\": [\n" +
               "        { \"name\": \"nonNegative\", \"type\": \"minValue\", \"params\": { \"value\": 0 } }\n" +
               "      ]\n" +
               "    },\n" +
               "    {\n" +
               "      \"name\": \"size\",\n" +
               "      \"displayName\": " + ls("Taille de page", "Page size", ctx) + ",\n" +
               "      \"description\": " + ls("Défaut: " + defSize + ". Maximum: " + maxSize + ".",
                                               "Default: " + defSize + ". Max: " + maxSize + ".", ctx) + ",\n" +
               "      \"dataType\": \"NUMBER\",\n" +
               "      \"expectMultipleValues\": false,\n" +
               "      \"required\": false,\n" +
               "      \"constraints\": [\n" +
               "        { \"name\": \"minSize\", \"type\": \"minValue\", \"params\": { \"value\": 1 } },\n" +
               "        { \"name\": \"maxSize\", \"type\": \"maxValue\", \"params\": { \"value\": " + maxSize + " } }\n" +
               "      ]\n" +
               "    },\n" +
               "    {\n" +
               "      \"name\": \"sort\",\n" +
               "      \"displayName\": " + ls("Critères de tri", "Sort criteria", ctx) + ",\n" +
               "      \"dataType\": \"OBJECT\",\n" +
               "      \"expectMultipleValues\": true,\n" +
               "      \"required\": false,\n" +
               "      \"subFields\": [\n" +
               "        {\n" +
               "          \"name\": \"field\",\n" +
               "          \"displayName\": " + ls("Champ", "Field", ctx) + ",\n" +
               "          \"dataType\": \"STRING\",\n" +
               "          \"expectMultipleValues\": false,\n" +
               "          \"required\": true,\n" +
               "          \"valuesEndpoint\": {\n" +
               "            \"protocol\": \"INLINE\",\n" +
               "            \"mode\": \"CLOSED\",\n" +
               "            \"items\": [\n" +
               "              " + sortItems + "\n" +
               "            ]\n" +
               "          },\n" +
               "          \"constraints\": []\n" +
               "        },\n" +
               "        {\n" +
               "          \"name\": \"direction\",\n" +
               "          \"displayName\": " + ls("Ordre", "Direction", ctx) + ",\n" +
               "          \"dataType\": \"STRING\",\n" +
               "          \"expectMultipleValues\": false,\n" +
               "          \"required\": true,\n" +
               "          \"valuesEndpoint\": {\n" +
               "            \"protocol\": \"INLINE\",\n" +
               "            \"mode\": \"CLOSED\",\n" +
               "            \"items\": [\n" +
               "              { \"value\": \"ASC\",  \"label\": " + ls("Croissant", "Ascending", ctx) + " },\n" +
               "              { \"value\": \"DESC\", \"label\": " + ls("Décroissant", "Descending", ctx) + " }\n" +
               "            ]\n" +
               "          },\n" +
               "          \"constraints\": []\n" +
               "        }\n" +
               "      ],\n" +
               "      \"constraints\": []\n" +
               "    }\n" +
               "  ],\n" +
               "  \"constraints\": []\n" +
               "}";
    }

    // ─── crossConstraint: combineWithRefsExist ────────────────────────────────

    private String buildRefsExistConstraint(FormContext ctx) {
        List<String> fields = new ArrayList<>();
        fields.add("combineWith");
        fields.addAll(ctx.transformedFieldRefs());

        StringBuilder fieldsArr = new StringBuilder("[");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) fieldsArr.append(", ");
            fieldsArr.append(q(fields.get(i)));
        }
        fieldsArr.append("]");

        String descFr = "Chaque identifiant dans 'combineWith' doit correspondre à un filtre " +
                        "effectivement fourni (non null). Exemple: si combineWith='NAME & AGE', " +
                        "les filtres NAME et AGE doivent être présents.";
        String descEn = "Every identifier in 'combineWith' must correspond to an actually " +
                        "provided (non-null) filter. Example: if combineWith='NAME & AGE', " +
                        "both NAME and AGE filters must be present.";

        return "{\n" +
               "  \"name\": \"combineWithRefsExist\",\n" +
               "  \"type\": \"custom\",\n" +
               "  \"fields\": " + fieldsArr + ",\n" +
               "  \"params\": { \"key\": \"filterql-refs-must-be-provided-fields\" },\n" +
               "  \"description\": " + ls(descFr, descEn, ctx) + "\n" +
               "}";
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /** Builds a DIFSP LocalizedString JSON object. */
    private String ls(String fr, String en, FormContext ctx) {
        StringBuilder sb = new StringBuilder("{ \"default\": ").append(q(fr));
        for (String locale : ctx.locales()) {
            if (locale.startsWith("fr")) sb.append(", \"fr\": ").append(q(fr));
            if (locale.startsWith("en")) sb.append(", \"en\": ").append(q(en));
        }
        sb.append(" }");
        return sb.toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** AGE → age, CREATED_AT → createdAt */
    private static String toCamelCase(String ref) {
        String lower = ref.toLowerCase();
        String[] parts = lower.split("_");
        if (parts.length == 1) return lower;
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }
}

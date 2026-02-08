package io.github.cyfko.filterql.core.projection;

import io.github.cyfko.filterql.core.exception.ProjectionDefinitionException;
import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.SortBy;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parser for advanced projection field syntax supporting multi-field declarations,
 * hierarchical pagination, and per-collection sorting options.
 *
 * <p>
 * Enables concise syntax for complex projections with collection-specific pagination
 * and sorting, eliminating prefix duplication while providing precise control over
 * nested data retrieval.
 * </p>
 *
 * <h2>Supported Syntax</h2>
 * <table border="1">
 *   <caption>Projection Field Syntax Examples</caption>
 *   <tr><th>Syntax</th><th>Example</th><th>Result</th></tr>
 *   <tr>
 *     <td>Simple field</td>
 *     <td>{@code "name"}</td>
 *     <td>prefix: "name", fields: ["name"]</td>
 *   </tr>
 *   <tr>
 *     <td>Nested field</td>
 *     <td>{@code "address.city.name"}</td>
 *     <td>prefix: "address.city", fields: ["name"]</td>
 *   </tr>
 *   <tr>
 *     <td>Multi-field collection</td>
 *     <td>{@code "orders.items.productName,price"}</td>
 *     <td>prefix: "orders.items", fields: ["productName", "price"]</td>
 *   </tr>
 *   <tr>
 *     <td>Hierarchical pagination</td>
 *     <td>{@code "orders[size=5].items[page=0,sort=id:asc].name"}</td>
 *     <td>orders: Pagination(5,0,[]), items: Pagination(10,0,[SortBy("id","asc")])</td>
 *   </tr>
 * </table>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>✅ <strong>DRY syntax:</strong> Single prefix declaration for multiple fields</li>
 *   <li>✅ <strong>Scoped pagination:</strong> Per-collection limits/offsets</li>
 *   <li>✅ <strong>Multi-column sorting:</strong> {@code sort=name:asc,price:desc}</li>
 *   <li>✅ <strong>Conflict detection:</strong> Validates duplicate collection options</li>
 *   <li>✅ <strong>Input validation:</strong> Comprehensive error messages for malformed syntax</li>
 *   <li>✅ <strong>Thread-safe:</strong> Stateless parser with immutable results</li>
 * </ul>
 *
 * <h2>Syntax Rules</h2>
 * <ul>
 *   <li>Collection options must be in brackets: {@code [size=10,page=0,sort=field:dir]}</li>
 *   <li>Multi-field syntax uses comma separator after last dot: {@code prefix.field1,field2}</li>
 *   <li>Sort direction must be 'asc' or 'desc' (case-insensitive)</li>
 *   <li>Size and page must be non-negative integers</li>
 *   <li>Field names cannot contain special characters: {@code [],.=:}</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is stateless and thread-safe. All methods are static and produce
 * immutable results ({@link ProjectionField} and {@link Pagination}).
 * </p>
 *
 * @author Frank KOSSI
 * @version 2.0
 * @see ProjectionField
 * @see Pagination
 * @see SortBy
 */
public final class ProjectionFieldParser {

    /**
     * Pattern for validating field names (alphanumeric, underscore, hyphen).
     */
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Pattern for extracting collection options: {@code collectionName[options]}.
     */
    private static final Pattern COLLECTION_OPTIONS_PATTERN = Pattern.compile("([a-zA-Z0-9_-]+)\\[([^]]+)]");

    /**
     * Maximum allowed page size to prevent memory issues.
     */
    private static final int MAX_PAGE_SIZE = 10000;

    /**
     * Default page size when size not specified.
     */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private ProjectionFieldParser() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    /**
     * Parses projection field specification into structured {@link ProjectionField}.
     *
     * <p>
     * Handles both simple fields and multi-field collection projections.
     * Hierarchical options ({@code [size=5]}) are stripped for field parsing but preserved
     * for {@link #parseCollectionOptions(Set)} extraction.
     * </p>
     *
     * <h3>Examples:</h3>
     * <pre>{@code
     * // Simple field
     * ProjectionField field = parse("name");
     * // → prefix: "name", fields: ["name"]
     *
     * // Nested field
     * ProjectionField field = parse("address.city.name");
     * // → prefix: "address.city", fields: ["name"]
     *
     * // Multi-field with pagination
     * ProjectionField field = parse("orders[size=5].items.name,price,quantity");
     * // → prefix: "orders.items", fields: ["name", "price", "quantity"]
     * }</pre>
     *
     * @param fieldSpec projection specification (e.g., {@code "orders.items.name,price"})
     *                  Must not be null or blank
     * @return parsed {@link ProjectionField} with prefix and field list
     * @throws ProjectionDefinitionException if fieldSpec is null/blank, contains invalid syntax,
     *                                  or has invalid field names
     * @throws NullPointerException if fieldSpec is null
     */
    public static ProjectionField parse(String fieldSpec) {
        validateFieldSpec(fieldSpec);

        String trimmedSpec = fieldSpec.trim();
        String cleanedSpec = removeHierarchicalOptions(trimmedSpec);

        validateCleanedSpec(cleanedSpec, trimmedSpec);

        ProjectionField result;
        if (cleanedSpec.contains(",")) {
            result = parseMultiField(cleanedSpec);
        } else if (cleanedSpec.contains(".")) {
            result = parseSingleField(cleanedSpec);
        } else {
            // Simple root field - validate the field name
            validateFieldName(cleanedSpec, trimmedSpec);
            result = new ProjectionField("",List.of(cleanedSpec));
        }

        return result;
    }

    /**
     * Validates the initial field specification.
     *
     * @param fieldSpec field specification to validate
     * @throws IllegalArgumentException if null, blank, or contains only whitespace
     */
    private static void validateFieldSpec(String fieldSpec) {
        if (fieldSpec == null) {
            throw new IllegalArgumentException("Projection field specification cannot be null");
        }
        if (fieldSpec.isBlank()) {
            throw new IllegalArgumentException("Projection field specification cannot be blank");
        }
    }

    /**
     * Validates the cleaned field specification after removing hierarchical options.
     *
     * @param cleanedSpec cleaned field specification
     * @param originalSpec original field specification (for error messages)
     * @throws ProjectionDefinitionException if cleaned spec is empty or contains invalid characters
     */
    private static void validateCleanedSpec(String cleanedSpec, String originalSpec) {
        if (cleanedSpec.isEmpty()) {
            throw new ProjectionDefinitionException(
                    "Field specification contains only brackets without field names: " + originalSpec
            );
        }

        // Check for consecutive dots
        if (cleanedSpec.contains("..")) {
            throw new ProjectionDefinitionException(
                    "Field specification contains consecutive dots: " + originalSpec
            );
        }

        // Check for leading/trailing dots
        if (cleanedSpec.startsWith(".") || cleanedSpec.endsWith(".")) {
            throw new ProjectionDefinitionException(
                    "Field specification cannot start or end with dot: " + originalSpec
            );
        }
    }

    /**
     * Strips hierarchical collection options while preserving field path.
     *
     * <p>
     * Removes all bracketed content from the field specification, leaving only
     * the dot-separated field path.
     * </p>
     *
     * <h3>Examples:</h3>
     * <pre>{@code
     * removeHierarchicalOptions("orders[size=5].items[page=0].name")
     * // → "orders.items.name"
     *
     * removeHierarchicalOptions("users[sort=name:asc].address.city")
     * // → "users.address.city"
     * }</pre>
     *
     * @param fieldSpec original field specification with optional bracketed options
     * @return field path without pagination/sort options
     */
    private static String removeHierarchicalOptions(String fieldSpec) {
        // Optimisation : parcours manuel pour retirer les options entre crochets
        StringBuilder sb = new StringBuilder(fieldSpec.length());
        int len = fieldSpec.length();
        int i = 0;
        while (i < len) {
            char c = fieldSpec.charAt(i);
            if (c == '[') {
                // Sauter jusqu'à la fermeture du crochet
                int depth = 1;
                i++;
                while (i < len && depth > 0) {
                    char cc = fieldSpec.charAt(i);
                    if (cc == '[') depth++;
                    else if (cc == ']') depth--;
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Parses single-field projection (no comma separators).
     *
     * <p>
     * Extracts the prefix (all segments except the last) and the field name (last segment).
     * For simple fields without dots, the prefix and field name are the same.
     * </p>
     *
     * <h3>Examples:</h3>
     * <pre>{@code
     * parseSingleField("name")
     * // → prefix: "name", fields: ["name"]
     *
     * parseSingleField("orders.items.name")
     * // → prefix: "orders.items", fields: ["name"]
     * }</pre>
     *
     * @param fieldSpec clean field path (e.g., {@code "orders.items.name"})
     * @return {@link ProjectionField} with extracted prefix and field
     * @throws IllegalArgumentException if field name contains invalid characters
     */
    private static ProjectionField parseSingleField(String fieldSpec) {
        int lastDotIndex = fieldSpec.lastIndexOf('.');

        String prefix;
        String fieldName;

        if (lastDotIndex < 0) {
            // Simple field without dots
            prefix = fieldSpec;
            fieldName = fieldSpec;
        } else {
            prefix = fieldSpec.substring(0, lastDotIndex);
            fieldName = fieldSpec.substring(lastDotIndex + 1);
        }

        validateFieldName(fieldName, fieldSpec);
        validatePathSegments(prefix, fieldSpec);

        return new ProjectionField(prefix, List.of(fieldName));
    }

    /**
     * Parses multi-field projection with comma-separated fields sharing a common prefix.
     *
     * <p>
     * The common prefix is determined by the last dot before the first comma.
     * All segments after that dot are treated as individual field names.
     * </p>
     *
     * <h3>Examples:</h3>
     * <pre>{@code
     * parseMultiField("orders.items.name,price,quantity")
     * // → prefix: "orders.items", fields: ["name", "price", "quantity"]
     *
     * parseMultiField("name,email,phone")
     * // → prefix: "", fields: ["name", "email", "phone"]
     * }</pre>
     *
     * @param fieldSpec comma-separated field list with optional common prefix
     * @return {@link ProjectionField} with common prefix and field list
     * @throws ProjectionDefinitionException if no valid fields found, fields are empty,
     *                                  or field names contain invalid characters
     */
    private static ProjectionField parseMultiField(String fieldSpec) {
        int firstCommaIndex = fieldSpec.indexOf(',');
        int lastDotBeforeComma = fieldSpec.lastIndexOf('.', firstCommaIndex);

        String prefix;
        String fieldsString;

        if (lastDotBeforeComma < 0) {
            prefix = "";
            fieldsString = fieldSpec;
        } else {
            prefix = fieldSpec.substring(0, lastDotBeforeComma);
            fieldsString = fieldSpec.substring(lastDotBeforeComma + 1);
        }

        // Parsing optimisé des champs (évite split/stream)
        List<String> fields = new ArrayList<>();
        int start = 0;
        int len = fieldsString.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || fieldsString.charAt(i) == ',') {
                int end = i;
                // Ignore les espaces
                while (start < end && Character.isWhitespace(fieldsString.charAt(start))) start++;
                while (end > start && Character.isWhitespace(fieldsString.charAt(end - 1))) end--;
                if (end > start) {
                    String field = fieldsString.substring(start, end);
                    fields.add(field);
                }
                start = i + 1;
            }
        }

        if (fields.isEmpty()) {
            throw new ProjectionDefinitionException(
                    "No valid fields found in multi-field specification: " + fieldSpec
            );
        }

        for (String field : fields) {
            validateFieldName(field, fieldSpec);
        }
        if (!prefix.isEmpty()) {
            validatePathSegments(prefix, fieldSpec);
        }
        return new ProjectionField(prefix, fields);
    }

    /**
     * Validates a field name against the allowed pattern.
     *
     * @param fieldName field name to validate
     * @param originalSpec original specification (for error messages)
     * @throws ProjectionDefinitionException if field name contains invalid characters
     */
    private static void validateFieldName(String fieldName, String originalSpec) {
        if (!FIELD_NAME_PATTERN.matcher(fieldName).matches()) {
            throw new ProjectionDefinitionException(
                    "Invalid field name '" + fieldName + "' in specification: " + originalSpec +
                            ". Field names must contain only alphanumeric characters, underscores, and hyphens."
            );
        }
    }

    /**
     * Validates all segments in a dot-separated path.
     *
     * @param path path to validate
     * @param originalSpec original specification (for error messages)
     * @throws ProjectionDefinitionException if any segment contains invalid characters
     */
    private static void validatePathSegments(String path, String originalSpec) {
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            validateFieldName(segment, originalSpec);
        }
    }

    /**
     * Extracts collection-specific pagination and sorting from all projection fields.
     *
     * <p>
     * Parses bracketed options from hierarchical field paths and validates that
     * no conflicting options exist for the same collection path. Compatible options
     * (exact matches) are merged.
     * </p>
     *
     * <h3>Examples:</h3>
     * <pre>{@code
     * // Single field with options
     * Map<String, Pagination> options = parseCollectionOptions(
     *     Set.of("orders[size=5,page=0,sort=date:desc].items.name")
     * );
     * // → {"orders": Pagination(page=0, size=5, sort=[SortBy("date","desc")])}
     *
     * // Multiple fields, compatible options
     * Map<String, Pagination> options = parseCollectionOptions(
     *     Set.of(
     *         "orders[size=5].items.name",
     *         "orders[size=5].items.price"
     *     )
     * );
     * // → {"orders": Pagination(page=0, size=5)}
     *
     * // Hierarchical pagination
     * Map<String, Pagination> options = parseCollectionOptions(
     *     Set.of("orders[size=10].items[size=5,sort=name:asc].name")
     * );
     * // → {
     * //     "orders": Pagination(page=0, size=10),
     * //     "orders.items": Pagination(page=0, size=5, sort=[SortBy("name","asc")])
     * //    }
     * }</pre>
     *
     * @param projectionSet complete set of projection field specifications
     *                      May be null or empty
     * @return map of collection path to {@link Pagination} configuration
     *         Returns empty map if projectionSet is null/empty
     * @throws ProjectionDefinitionException if conflicting options detected for same collection,
     *                                  or if pagination options are invalid
     */
    public static Map<String, Pagination> parseCollectionOptions(Set<String> projectionSet) {
        if (projectionSet == null || projectionSet.isEmpty()) {
            return Map.of();
        }

        Map<String, Pagination> optionsByCollection = new LinkedHashMap<>();

        for (String fieldSpec : projectionSet) {
            if (fieldSpec == null || fieldSpec.isBlank()) {
                continue; // Skip invalid entries
            }

            Map<String, Pagination> fieldOptions = extractHierarchicalOptions(fieldSpec);

            for (Map.Entry<String, Pagination> entry : fieldOptions.entrySet()) {
                String collectionPath = entry.getKey();
                Pagination newPagination = entry.getValue();

                optionsByCollection.merge(collectionPath, newPagination, (existing, candidate) -> {
                    if (!existing.equals(candidate)) {
                        throw new ProjectionDefinitionException(
                                "Conflicting pagination options for collection '" + collectionPath + "': " +
                                        "existing=" + existing + ", new=" + candidate + ". " +
                                        "All references to the same collection must use identical pagination options."
                        );
                    }
                    return existing;
                });
            }
        }

        return Collections.unmodifiableMap(optionsByCollection);
    }

    /**
     * Extracts pagination and sort options from hierarchical field path segments.
     *
     * <p>
     * Parses each segment of a dot-separated path, extracting bracketed options
     * and building cumulative collection paths.
     * </p>
     *
     * @param fieldSpec field specification with optional bracketed options
     * @return map of collection path to {@link Pagination} configuration
     */
    private static Map<String, Pagination> extractHierarchicalOptions(final String fieldSpec) {
        Map<String, Pagination> options = new LinkedHashMap<>();
        Matcher matcher = COLLECTION_OPTIONS_PATTERN.matcher(fieldSpec);
        List<String> pathSegments = new ArrayList<>();

        // Global collection prefix (if any)
        String prefix = null;
        int prefixEndIndex = fieldSpec.indexOf('[');
        if (prefixEndIndex != -1) {
            prefixEndIndex = fieldSpec.substring(0, prefixEndIndex).lastIndexOf('.');
            prefix = prefixEndIndex == -1 ? null : fieldSpec.substring(0,prefixEndIndex + 1);
        }

        int lastEnd = 0;
        while (matcher.find()) {
            String collectionName = matcher.group(1);
            String optionsStr = matcher.group(2).replaceAll("\\s", "");

            // Build cumulative path
            pathSegments.add(collectionName);
            String collectionPath = String.join(".", pathSegments);

            if (prefix != null) {
                collectionPath = prefix + collectionPath;
            }

            // Parse and store options
            Pagination pagination = parseSegmentOptions(optionsStr, fieldSpec);
            options.put(collectionPath, pagination);

            lastEnd = matcher.end();
        }

        return options;
    }

    /**
     * Parses segment options from bracketed syntax.
     *
     * <p>
     * Supported options:
     * </p>
     * <ul>
     *   <li>{@code size=N} - Page size (must be positive, max 10000)</li>
     *   <li>{@code page=N} - Page number (must be non-negative)</li>
     *   <li>{@code sort=field:dir[,field2:dir2]} - Sort specification</li>
     * </ul>
     *
     * <h3>Examples:</h3>
     * <pre>{@code
     * parseSegmentOptions("size=20,page=1", ...)
     * // → Pagination(page=1, size=20)
     *
     * parseSegmentOptions("sort=name:asc,price:desc", ...)
     * // → Pagination(page=0, size=10, sort=[...])
     *
     * parseSegmentOptions("size=50,page=2,sort=date:desc", ...)
     * // → Pagination(page=2, size=50, sort=[...])
     * }</pre>
     *
     * @param optionsStr raw options string from brackets
     * @param fullSpec full field specification (for error messages)
     * @return parsed {@link Pagination} with validated options
     * @throws ProjectionDefinitionException if options are malformed or contain invalid values
     */
    private static Pagination parseSegmentOptions(String optionsStr, String fullSpec) {
        int page = 0; // Default page 0
        Integer size = null;
        List<SortBy> sortFields = new ArrayList<>();

        String[] parts = optionsStr.split(",");
        for (String part : parts) {
            part = part.trim();

            if (part.isEmpty()) {
                continue;
            }

            try {
                if (part.startsWith("size=")) {
                    size = parsePositiveInt(part.substring(5), "size", fullSpec);
                    if (size > MAX_PAGE_SIZE) {
                        throw new ProjectionDefinitionException(
                                "Page size " + size + " exceeds maximum allowed size of " +
                                        MAX_PAGE_SIZE + " in: " + fullSpec
                        );
                    }
                } else if (part.startsWith("page=")) {
                    page = parseNonNegativeInt(part.substring(5), "page", fullSpec);
                } else if (part.startsWith("sort=")) {
                    String sortStr = part.substring(5);
                    sortFields.addAll(parseSortFields(sortStr, fullSpec));
                } else if (part.contains(":")) {
                    sortFields.addAll(parseSortFields(part, fullSpec));
                } else {
                    throw new ProjectionDefinitionException(
                            "Unknown option '" + part + "' in: " + fullSpec + ". " +
                                    "Valid options are: size, page, sort"
                    );
                }
            } catch (NumberFormatException e) {
                throw new ProjectionDefinitionException(
                        "Invalid numeric value in option '" + part + "' in: " + fullSpec, e
                );
            }
        }

        return new Pagination(
                page,
                size != null ? size : DEFAULT_PAGE_SIZE,
                sortFields.isEmpty() ? null : sortFields
        );
    }

    /**
     * Parses and validates a positive integer value.
     *
     * @param value string value to parse
     * @param optionName name of the option (for error messages)
     * @param fullSpec full specification (for error messages)
     * @return parsed positive integer
     * @throws ProjectionDefinitionException if value is not a positive integer
     */
    private static int parsePositiveInt(String value, String optionName, String fullSpec) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new ProjectionDefinitionException(
                    optionName + " must be positive, got: " + parsed + " in: " + fullSpec
            );
        }
        return parsed;
    }

    /**
     * Parses and validates a non-negative integer value.
     *
     * @param value string value to parse
     * @param optionName name of the option (for error messages)
     * @param fullSpec full specification (for error messages)
     * @return parsed non-negative integer
     * @throws ProjectionDefinitionException if value is negative
     */
    private static int parseNonNegativeInt(String value, String optionName, String fullSpec) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new ProjectionDefinitionException(
                    optionName + " cannot be negative, got: " + parsed + " in: " + fullSpec
            );
        }
        return parsed;
    }

    /**
     * Parses comma-separated sort specifications.
     *
     * <p>
     * Each sort specification has the format {@code field:direction}, where
     * direction is either 'asc' or 'desc' (case-insensitive). If direction
     * is omitted, 'asc' is assumed.
     * </p>
     *
     * <h3>Examples:</h3>
     * <pre>{@code
     * parseSortFields("name:asc,price:desc", ...)
     * // → [SortBy("name","asc"), SortBy("price","desc")]
     *
     * parseSortFields("date", ...)
     * // → [SortBy("date","asc")]
     *
     * parseSortFields("name:ASC,price:DESC", ...)
     * // → [SortBy("name","asc"), SortBy("price","desc")]
     * }</pre>
     *
     * @param sortStr sort specification string (comma-separated)
     * @param fullSpec full field specification (for error messages)
     * @return list of {@link SortBy}s with validated directions
     * @throws ProjectionDefinitionException if sort specification is malformed or
     *                                  contains invalid field names/directions
     */
    private static List<SortBy> parseSortFields(String sortStr, String fullSpec) {
        if (sortStr.isBlank()) {
            throw new ProjectionDefinitionException(
                    "Sort specification cannot be empty in: " + fullSpec
            );
        }

        List<SortBy> sortFields = new ArrayList<>();
        String[] specs = sortStr.split(",");

        for (String spec : specs) {
            spec = spec.trim();
            if (spec.isEmpty()) {
                continue;
            }

            int colonIndex = spec.indexOf(':');
            String field;
            String direction;

            if (colonIndex > 0) {
                field = spec.substring(0, colonIndex).trim();
                direction = spec.substring(colonIndex + 1).trim().toLowerCase();

                if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
                    throw new ProjectionDefinitionException(
                            "Invalid sort direction '" + direction + "' for field '" + field +
                                    "' in: " + fullSpec + ". Must be 'asc' or 'desc'"
                    );
                }
            } else {
                field = spec;
                direction = "asc";
            }

            // Validate field name
            if (!FIELD_NAME_PATTERN.matcher(field).matches()) {
                throw new ProjectionDefinitionException(
                        "Invalid sort field name '" + field + "' in: " + fullSpec + ". " +
                                "Field names must contain only alphanumeric characters, underscores, and hyphens."
                );
            }

            sortFields.add(new SortBy(field, direction));
        }

        return sortFields;
    }

    /**
     * Immutable representation of parsed projection field components.
     *
     * <p>
     * Contains a common prefix path and a list of projected leaf fields that share
     * that prefix. This structure eliminates duplication in projection specifications
     * and provides a clear separation between collection paths and field names.
     * </p>
     *
     * <h3>Field Distinction:</h3>
     * <pre>{@code
     * // Simple field → empty prefix
     * new ProjectionField("", List.of("name"))
     * // → represents: "name"
     *
     * // Nested field → non-empty prefix
     * new ProjectionField("user", List.of("name"))
     * // → represents: "user.name"
     *
     * // Same-name nested → distinguishable by prefix
     * new ProjectionField("name", List.of("name"))
     * // → represents: "name.name" (NOT "name"!)
     * }</pre>
     *
     * <h3>Multi-Field Examples:</h3>
     * <pre>{@code
     * // Multi-field at root
     * new ProjectionField("", List.of("id", "name", "email"))
     * // → represents: "id", "name", "email"
     *
     * // Multi-field projection
     * new ProjectionField("orders.items", List.of("name", "price", "quantity"))
     * // → represents: "orders.items.name", "orders.items.price", "orders.items.quantity"
     * }</pre>
     *
     * <h3>Thread Safety:</h3>
     * <p>
     * This record is immutable and thread-safe. The fields list is defensively
     * copied and wrapped in an unmodifiable view.
     * </p>
     *
     * @param prefix collection path prefix (e.g., {@code "orders.items"}).
     *               Empty string for root-level fields.
     * @param fields list of projected field names sharing the prefix.
     *               Must not be null or empty.
     */
    public record ProjectionField(String prefix, List<String> fields) {

        /**
         * Validates parameters and ensures field list immutability.
         *
         * @throws NullPointerException if prefix or fields is null
         * @throws IllegalArgumentException if fields is empty
         */
        public ProjectionField {
            Objects.requireNonNull(prefix, "prefix cannot be null");
            Objects.requireNonNull(fields, "fields cannot be null");

            if (fields.isEmpty()) {
                throw new IllegalArgumentException("fields cannot be empty");
            }

            // Defensive copy and make immutable
            fields = Collections.unmodifiableList(new ArrayList<>(fields));
        }

        /**
         * Determines if this represents a collection projection (nested field).
         *
         * <p>
         * A projection is considered a collection projection if it has a non-empty
         * prefix, indicating a nested structure.
         * </p>
         *
         * <h3>Examples:</h3>
         * <pre>{@code
         * new ProjectionField("", List.of("name")).isCollection()
         * // → false (simple root field)
         *
         * new ProjectionField("user", List.of("name")).isCollection()
         * // → true (nested field: user.name)
         *
         * new ProjectionField("name", List.of("name")).isCollection()
         * // → true (nested field: name.name, NOT simple "name")
         *
         * new ProjectionField("orders.items", List.of("name")).isCollection()
         * // → true (collection projection)
         * }</pre>
         *
         * @return {@code true} if prefix is non-empty (indicates nested structure),
         *         {@code false} for root-level fields (empty prefix)
         */
        public boolean isCollection() {
            return !prefix.isEmpty();
        }

        /**
         * Determines if this is a simple root-level field.
         *
         * <p>
         * A field is considered simple if it has an empty prefix and contains
         * only a single field.
         * </p>
         *
         * <h3>Examples:</h3>
         * <pre>{@code
         * new ProjectionField("", List.of("name")).isSimpleField()
         * // → true (simple: "name")
         *
         * new ProjectionField("", List.of("id", "name")).isSimpleField()
         * // → false (multiple fields)
         *
         * new ProjectionField("user", List.of("name")).isSimpleField()
         * // → false (nested field)
         * }</pre>
         *
         * @return {@code true} if this is a simple root-level field
         */
        public boolean isSimpleField() {
            return prefix.isEmpty() && fields.size() == 1;
        }

        /**
         * Returns the complete path to the first projected field.
         *
         * <p>
         * Combines the prefix with the first field name using dot notation.
         * If prefix is empty, returns only the field name.
         * </p>
         *
         * <h3>Examples:</h3>
         * <pre>{@code
         * new ProjectionField("", List.of("name")).getFullPath()
         * // → "name"
         *
         * new ProjectionField("user", List.of("name")).getFullPath()
         * // → "user.name"
         *
         * new ProjectionField("name", List.of("name")).getFullPath()
         * // → "name.name"
         *
         * new ProjectionField("orders.items", List.of("name", "price")).getFullPath()
         * // → "orders.items.name"
         * }</pre>
         *
         * @return full field path (prefix + first field), never null
         */
        public String getFullPath() {
            if (prefix.isEmpty()) {
                return fields.getFirst();
            }
            return prefix + '.' + fields.getFirst();
        }

        /**
         * Returns all full paths for all projected fields.
         *
         * <p>
         * Useful when you need to expand the multi-field projection into
         * individual full paths.
         * </p>
         *
         * <h3>Examples:</h3>
         * <pre>{@code
         * new ProjectionField("orders.items", List.of("name", "price")).getAllFullPaths()
         * // → ["orders.items.name", "orders.items.price"]
         *
         * new ProjectionField("", List.of("id", "name")).getAllFullPaths()
         * // → ["id", "name"]
         *
         * new ProjectionField("user", List.of("id")).getAllFullPaths()
         * // → ["user.id"]
         * }</pre>
         *
         * @return immutable list of all full field paths
         */
        public List<String> getAllFullPaths() {
            if (prefix.isEmpty()) {
                return List.copyOf(fields);
            }

            return fields.stream()
                    .map(field -> prefix + '.' + field)
                    .collect(Collectors.toUnmodifiableList());
        }

        /**
         * Returns the nesting level of this projection.
         *
         * <p>
         * Calculates the depth by counting dots in the full path.
         * </p>
         *
         * <h3>Examples:</h3>
         * <pre>{@code
         * new ProjectionField("", List.of("name")).getNestingLevel()
         * // → 0 (root level)
         *
         * new ProjectionField("user", List.of("name")).getNestingLevel()
         * // → 1 (user.name)
         *
         * new ProjectionField("user.profile", List.of("email")).getNestingLevel()
         * // → 2 (user.profile.email)
         * }</pre>
         *
         * @return nesting level (0 for root-level fields)
         */
        public int getNestingLevel() {
            if (prefix.isEmpty()) {
                return 0;
            }
            return (int) prefix.chars().filter(ch -> ch == '.').count() + 1;
        }

        @Override
        public String toString() {
            if (fields.size() == 1) {
                return getFullPath();
            }
            if (prefix.isEmpty()) {
                return "[" + String.join(",", fields) + "]";
            }
            return prefix + ".[" + String.join(",", fields) + "]";
        }
    }
}
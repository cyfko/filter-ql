package io.github.cyfko.filterql.jpa.projection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight view over a RowBuffer for nested prefix access.
 * <p>
 * This class provides zero-allocation access to nested fields via a prefix.
 * Instead of creating a new Map structure when accessing
 * {@code row.get("address")},
 * a NestedView is returned that delegates calls to the source RowBuffer with
 * the prefix applied.
 * </p>
 *
 * <h2>Usage</h2>
 * 
 * <pre>{@code
 * RowBuffer row = ...; // contains "address.city.name"
 * NestedView address = (NestedView) row.get("address");
 * NestedView city = (NestedView) address.get("city");
 * Object name = city.get("name"); // Returns actual value
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public final class NestedView {

    private final RowBuffer source;
    private final String prefix;

    /**
     * Creates a NestedView over the given RowBuffer with the specified prefix.
     *
     * @param source the source RowBuffer
     * @param prefix the prefix for field access (e.g., "address")
     */
    public NestedView(RowBuffer source, String prefix) {
        this.source = source;
        this.prefix = prefix;
    }

    /**
     * Gets the value for the given field name, prefixed with this view's prefix.
     * <p>
     * If the full path (prefix + "." + field) matches a scalar field, returns the
     * value.
     * If it matches a collection, returns the collection.
     * If neither, returns a new NestedView for further nesting.
     * </p>
     *
     * @param field the field name relative to this view's prefix
     * @return the value, collection, or a nested NestedView
     * @throws IllegalArgumentException if the field path is not found
     */
    public Object get(String field) {
        String fullPath = prefix + "." + field;
        return source.get(fullPath);
    }

    /**
     * Gets the value, or null if not found.
     *
     * @param field the field name relative to this view's prefix
     * @return value or null
     */
    public Object getOrNull(String field) {
        String fullPath = prefix + "." + field;
        return source.getOrNull(fullPath);
    }

    /**
     * Returns the prefix of this view.
     *
     * @return the prefix string
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns the source RowBuffer.
     *
     * @return source RowBuffer
     */
    public RowBuffer getSource() {
        return source;
    }

    /**
     * Converts this nested view to a Map representation.
     * <p>
     * Useful for serialization or when a Map structure is required.
     * </p>
     *
     * @return Map representation of this nested structure
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        String prefixDot = prefix + ".";
        FieldSchema schema = source.getSchema();

        for (int i = 0; i < schema.fieldCount(); i++) {
            if (schema.isInternal(i))
                continue;

            String dtoField = schema.dtoField(i);
            if (dtoField.startsWith(prefixDot)) {
                String relativePath = dtoField.substring(prefixDot.length());
                Object value = source.get(i);
                insertNested(result, relativePath, value);
            }
        }

        // Add collections with this prefix
        for (int c : schema.collectionIndexes()) {
            String collName = schema.dtoField(c);
            if (collName.startsWith(prefixDot)) {
                String relativeName = collName.substring(prefixDot.length());
                List<RowBuffer> children = source.getCollection(c);
                if (children != null) {
                    List<Map<String, Object>> childMaps = new java.util.ArrayList<>(children.size());
                    for (RowBuffer child : children) {
                        childMaps.add(child.toMap());
                    }
                    insertNested(result, relativeName, childMaps);
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void insertNested(Map<String, Object> map, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            Object existing = current.get(segment);
            if (existing instanceof Map) {
                current = (Map<String, Object>) existing;
            } else {
                Map<String, Object> newMap = new LinkedHashMap<>();
                current.put(segment, newMap);
                current = newMap;
            }
        }

        current.put(segments[segments.length - 1], value);
    }

    @Override
    public String toString() {
        return "NestedView[" + prefix + "]";
    }
}

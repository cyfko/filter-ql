package io.github.cyfko.filterql.jpa.projection;

import java.util.*;

/**
 * Indexed row buffer replacing {@code Map<String, Object>} for efficient
 * storage.
 * <p>
 * Uses a fixed-size {@code Object[]} array with schema-based access instead of
 * HashMap lookups. This significantly reduces memory allocation, GC pressure,
 * and access time.
 * </p>
 *
 * <h2>Memory Comparison</h2>
 * <ul>
 * <li>LinkedHashMap: ~100+ bytes per entry (Entry objects, hash buckets)</li>
 * <li>RowBuffer: ~24 bytes + 8×N (array header + references)</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * 
 * <pre>{@code
 * FieldSchema schema = FieldSchema.fromMappings(mappings);
 * RowBuffer row = new RowBuffer(schema);
 * 
 * // Fill from tuple
 * for (int i = 0; i < schema.fieldCount(); i++) {
 *     row.set(i, tuple.get(schema.dtoField(i)));
 * }
 * 
 * // Access by index (O(1))
 * Object value = row.get(0);
 * 
 * // Access by name (O(1) via schema lookup)
 * Object value = row.get("fieldName");
 * 
 * // Final conversion to Map (only at return)
 * Map<String, Object> result = row.toMap();
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public final class RowBuffer {

    private final FieldSchema schema;
    private final Object[] values;

    /**
     * Creates a new RowBuffer with the given schema.
     * All slots are initialized to null.
     *
     * @param schema the field schema defining structure
     */
    public RowBuffer(FieldSchema schema) {
        this.schema = schema;
        this.values = new Object[schema.totalSlots()];
    }

    // ==================== Value Access ====================

    /**
     * Gets the value at the given index.
     *
     * @param index slot index
     * @return value at index (maybe null)
     */
    public Object get(int index) {
        return values[index];
    }

    /**
     * Gets the value for the given DTO field name (scalar, collection, or nested
     * prefix).
     * <p>
     * If the field is a scalar, returns the value directly.
     * If the field is a collection, returns the List of child RowBuffers.
     * If the field is a prefix (e.g., "address" when schema has
     * "address.city.name"),
     * returns a {@link NestedView} for zero-allocation nested access.
     * </p>
     *
     * @param dtoField DTO field name or prefix
     * @return value, collection, or NestedView for nested access
     * @throws IllegalArgumentException if field/prefix not found in schema
     */
    public Object get(String dtoField) {
        return getOrNested(dtoField);
    }

    /**
     * Handles scalar, collection, and prefix access.
     * <p>
     * Used internally by {@link #get(String)} and by {@link NestedView}.
     * </p>
     *
     * @param dtoField DTO field name or prefix
     * @return value, collection, or NestedView
     * @throws IllegalArgumentException if field/prefix not found
     */
    public Object getOrNested(String dtoField) {
        // 1. Try exact match (scalar field)
        Indexer indexer = schema.indexOfDto(dtoField);
        if (indexer != Indexer.NONE) {
            int idx = indexer.isCollection() ? schema.collectionSlot(indexer.index()) : indexer.index();
            return values[idx];
        }

        // 2. Try as prefix for nested access
        if (hasPrefix(dtoField)) {
            return new NestedView(this, dtoField);
        }

        throw new IllegalArgumentException("Unknown field: " + dtoField);
    }

    /**
     * Checks if the given string is a valid prefix for any field in the schema.
     *
     * @param prefix the prefix to check
     * @return true if at least one field starts with prefix + "."
     */
    public boolean hasPrefix(String prefix) {
        String prefixDot = prefix + ".";

        // Check scalar fields
        for (int i = 0; i < schema.fieldCount(); i++) {
            if (schema.dtoField(i).startsWith(prefixDot)) {
                return true;
            }
        }

        // Check collection names
        for (int c = 0; c < schema.collectionCount(); c++) {
            if (schema.collectionName(c).startsWith(prefixDot)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the value for the given DTO field name, or null if not found.
     *
     * @param dtoField DTO field name
     * @return value for field or null
     */
    public Object getOrNull(String dtoField) {
        Indexer indexer = schema.indexOfDto(dtoField);
        if (indexer == Indexer.NONE) {
            return null;
        }
        int idx = indexer.isCollection() ? schema.collectionSlot(indexer.index()) : indexer.index();
        return values[idx];
    }

    /**
     * Sets the value at the given index.
     *
     * @param index slot index
     * @param value value to set
     */
    public void set(int index, Object value) {
        values[index] = value;
    }

    /**
     * Sets the value for the given DTO field name.
     *
     * @param dtoField DTO field name
     * @param value    value to set
     */
    public void set(String dtoField, Object value) {
        Indexer indexer = schema.indexOfDto(dtoField);
        if (indexer == Indexer.NONE || indexer.isCollection()) {
            throw new IllegalArgumentException("Unknown field: " + dtoField);
        }
        values[indexer.index()] = value;
    }

    // ==================== Collection Access ====================

    /**
     * Gets the collection at the given collection index.
     *
     * @param collectionIndex collection index (0-based)
     * @return list of child RowBuffers (may be null if not initialized)
     */
    @SuppressWarnings("unchecked")
    public List<RowBuffer> getCollection(int collectionIndex) {
        int slot = schema.collectionSlot(collectionIndex);
        return (List<RowBuffer>) values[slot];
    }

    /**
     * Sets the collection at the given collection index.
     *
     * @param collectionIndex collection index (0-based)
     * @param children        list of child RowBuffers
     */
    public void setCollection(int collectionIndex, List<RowBuffer> children) {
        int slot = schema.collectionSlot(collectionIndex);
        values[slot] = children;
    }

    /**
     * Initializes an empty collection at the given collection index.
     *
     * @param collectionIndex collection index (0-based)
     */
    public void initCollection(int collectionIndex) {
        int slot = schema.collectionSlot(collectionIndex);
        if (values[slot] == null) {
            values[slot] = new ArrayList<RowBuffer>();
        }
    }

    /**
     * Adds a child RowBuffer to the collection at the given index.
     *
     * @param collectionIndex collection index (0-based)
     * @param child           child RowBuffer to add
     */
    @SuppressWarnings("unchecked")
    public void addToCollection(int collectionIndex, RowBuffer child) {
        int slot = schema.collectionSlot(collectionIndex);
        List<RowBuffer> list = (List<RowBuffer>) values[slot];
        if (list == null) {
            list = new ArrayList<>();
            values[slot] = list;
        }
        list.add(child);
    }

    // ==================== Conversion ====================

    /**
     * Converts this RowBuffer to a Map for final output, excluding specified slots.
     * <p>
     * This method should only be called once at the end of processing,
     * as it allocates a new Map. Internal fields and excluded slots are excluded.
     * Nested fields are properly structured as nested Maps.
     * </p>
     *
     * @return Map representation of this row
     */
    public Map<String, Object> toMap() {
        final Set<Integer> excludedSlots = schema.getSerialisationExcludedSlots();
        Map<String, Object> result = new LinkedHashMap<>();

        // Add scalar fields
        for (int i = 0; i < schema.fieldCount(); i++) {
            if (schema.isInternal(i)) {
                continue;
            }

            if (excludedSlots != null && excludedSlots.contains(i)) {
                continue;
            }

            Object value = values[i];
            if (schema.isNested(i)) {
                insertNested(result, schema.nestedPath(i), value);
            } else {
                result.put(schema.dtoField(i), value);
            }
        }

        // Add collections
        for (int c = 0; c < schema.collectionCount(); c++) {
            String collName = schema.collectionName(c);
            List<RowBuffer> children = getCollection(c);

            if (children != null) {
                List<Map<String, Object>> childMaps = new ArrayList<>(children.size());
                for (RowBuffer child : children) {
                    childMaps.add(child.toMap());
                }

                // Handle nested collection placement
                if (collName.contains(".")) {
                    insertNested(result, schema.collectionPaths(c), childMaps);
                } else {
                    result.put(collName, childMaps);
                }
            } else {
                // Empty collection
                if (collName.contains(".")) {
                    insertNested(result, schema.collectionPaths(c), new ArrayList<>());
                } else {
                    result.put(collName, new ArrayList<>());
                }
            }
        }

        return result;
    }

    /**
     * Inserts a value into a nested map structure using pre-split path segments.
     *
     * @param map      target map
     * @param segments path segments
     * @param value    value to insert
     */
    @SuppressWarnings("unchecked")
    private void insertNested(Map<String, Object> map, String[] segments, Object value) {
        Map<String, Object> current = map;

        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            Object existing = current.get(segment);

            if (existing instanceof Map) {
                current = (Map<String, Object>) existing;
            } else {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(segment, nested);
                current = nested;
            }
        }

        current.put(segments[segments.length - 1], value);
    }

    // ==================== Accessors ====================

    public FieldSchema getSchema() {
        return schema;
    }

    /**
     * Retrieves the number of fields on this row ignoring serialisation excluded
     * fields
     * 
     * @return the number of fields on this row
     */
    public int fields() {
        return values.length - schema.getNumberOfInternalFields() - schema.getSerialisationExcludedSlots().size();
    }

    /**
     * Checks whether this row contain the non-excluded {@code fieldName} value
     * whether it is {@code null} or not.
     * 
     * @param fieldName the field name to lookup
     * @return true
     */
    public boolean contains(String fieldName) {
        return schema.indexOfDto(fieldName) != Indexer.NONE;
    }
}

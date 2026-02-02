package io.github.cyfko.filterql.jpa.strategies.helper;

import io.github.cyfko.projection.metamodel.model.projection.ComputedField;

import java.util.*;

import static io.github.cyfko.filterql.jpa.strategies.helper.QueryPlan.PREFIX_FOR_COMPUTED;

/**
 * Pre-computed schema for efficient field access in multi-query projections.
 * <p>
 * Built once per execution plan and reused for all rows. Provides O(1) field
 * access by index instead of O(hash) Map lookups. All string splitting and
 * nested path resolution is done at construction time.
 * </p>
 *
 * <h2>Key Benefits</h2>
 * <ul>
 * <li>O(1) field access via integer indices</li>
 * <li>Pre-split nested paths (no runtime String.split())</li>
 * <li>Pre-computed collection slot positions</li>
 * <li>Immutable and thread-safe after construction</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public final class FieldSchema {

    /** Entity field names for query construction */
    private final String[] entityFields;

    /** DTO field names for result mapping */
    private final String[] dtoFields;

    /** Pre-split nested paths (null if not nested) */
    private final String[][] nestedPaths;

    /** Whether each field is internal (not exposed in final result) */
    private final FieldStatus[] fieldStatuses;

    private final int numInternalFields;

    private final int[] collectionSlots;

    /** Fast lookup: dtoField -> index */
    private final Map<String, Integer> dtoIndexMap;

    /** Fast lookup: entityField -> index */
    private final Map<String, Integer> entityIndexMap;

    /** Fast lookup: computedField -> dependency indexes */
    private Map<String, DependencyInfo[]> computedFieldIndexMap;

    private FieldSchema(Builder builder) {
        this.entityFields = builder.entityFields.toArray(new String[0]);
        this.dtoFields = builder.dtoFields.toArray(new String[0]);
        this.nestedPaths = builder.nestedPaths.toArray(new String[0][]);
        this.fieldStatuses = toPrimitiveBooleanArray(builder.internal);
        this.numInternalFields = countInternalFields(this.fieldStatuses);
        this.collectionSlots = toCollectionSlots(this.fieldStatuses);
        this.computedFieldIndexMap = builder.computedFieldIndexMap;

        // Build index maps
        this.dtoIndexMap = new HashMap<>(dtoFields.length);
        this.entityIndexMap = new HashMap<>(entityFields.length);
        for (int i = 0; i < dtoFields.length; i++) {
            entityIndexMap.put(entityFields[i], i);
            dtoIndexMap.put(dtoFields[i], i);
        }
    }

    public Map<String, DependencyInfo[]> getComputedFieldIndexMap() {
        return computedFieldIndexMap;
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a new builder for constructing a FieldSchema.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // ==================== Field Access ====================

    /**
     * Returns the number of scalar fields in this schema.
     *
     * @return field count
     */
    public int fieldCount() {
        return entityFields.length;
    }

    /**
     * Returns the total number of slots (fields + collections).
     *
     * @return total slot count
     */
    public int totalSlots() {
        return entityFields.length;
    }

    /**
     * Returns the entity field name at the given index.
     *
     * @param index field index
     * @return entity field name
     */
    public String entityField(int index) {
        return entityFields[index];
    }

    /**
     * Returns the DTO field name at the given index.
     *
     * @param index field index
     * @return DTO field name
     */
    public String dtoField(int index) {
        return dtoFields[index];
    }

    /**
     * Checks if the field at the given index is nested (contains dots).
     *
     * @param index field index
     * @return true if nested
     */
    public boolean isNested(int index) {
        return nestedPaths[index] != null;
    }

    /**
     * Returns the pre-split nested path segments for the field at the given index.
     * Returns null if the field is not nested.
     *
     * @param index field index
     * @return path segments or null
     */
    public String[] nestedPath(int index) {
        return nestedPaths[index];
    }

    /**
     * Checks if the field at the given index is internal (not exposed in result).
     *
     * @param index field index
     * @return true if internal
     */
    public boolean isInternal(int index) {
        return fieldStatuses[index] == FieldStatus.SQL_ONLY;
    }

    public FieldStatus getFieldStatus(int index) {
        return fieldStatuses[index];
    }

    /**
     * Returns the index for a DTO field name, or {@link Indexer#NONE} if not found.
     *
     * @param dtoField DTO field name
     * @return index or {@link Indexer#NONE}
     */
    public Indexer indexOfDto(String dtoField) {
        Integer idx = dtoIndexMap.get(dtoField);
        if (idx == null) {
            return Indexer.NONE;
        }
        return new Indexer(idx,  fieldStatuses[idx] == FieldStatus.SQL_IGNORE_COLLECTION);
    }

    /**
     * Returns the index for an entity field name, or -1 if not found.
     *
     * @param entityField entity field name
     * @return index or -1
     */
    public int indexOfEntity(String entityField) {
        Integer idx = entityIndexMap.get(entityField);
        return idx != null ? idx : -1;
    }

    public int getNumberOfInternalFields() {
        return numInternalFields;
    }

    // ==================== Collection Access ====================

    /**
     * Returns an array of collection slots.
     *
     * @return collection slot array
     */
    public int[] collectionIndexes() {
        return collectionSlots;
    }

    public String[] collectionPaths(int collectionIndex) {
        return nestedPaths[collectionIndex];
    }

    // ==================== Utility Methods ====================

    private static FieldStatus[] toPrimitiveBooleanArray(List<FieldStatus> list) {
        FieldStatus[] arr = new FieldStatus[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private static int countInternalFields(FieldStatus[] internal) {
        int numFields = 0;
        for (FieldStatus s : internal) {
            if (s == FieldStatus.SQL_ONLY)
                numFields++;
        }
        return numFields;
    }

    private static int[] toCollectionSlots(FieldStatus[] internal) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < internal.length; i++) {
            if (internal[i] == FieldStatus.SQL_IGNORE_COLLECTION)
                list.add(i);
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    // ==================== Builder ====================

    /**
     * Builder for constructing FieldSchema instances.
     */
    public static final class Builder {
        private final List<String> entityFields = new ArrayList<>();
        private final List<String> dtoFields = new ArrayList<>();
        private final List<String[]> nestedPaths = new ArrayList<>();
        private final List<FieldStatus> internal = new ArrayList<>();
        private final Map<String, DependencyInfo[]> computedFieldIndexMap = new HashMap<>();

        private Builder() {
        }

        /**
         * Adds a field to the schema.
         *
         * @param entityField entity field name (for queries)
         * @param dtoField    DTO field name (for results)
         * @param status  usage status of the field
         */
        public void addField(String entityField, String dtoField, FieldStatus status) {
            // Check if already added
            int existingIdx = findEntityFieldDuplicateIndex(entityField);
            if (existingIdx > -1) {
                // If the existing field was internal and we're adding it as visible,
                // we need to update dtoField to make it accessible
                if (status == FieldStatus.SQL && internal.get(existingIdx) == FieldStatus.SQL_ONLY) {
                    internal.set(existingIdx, status);
                    dtoFields.set(existingIdx, dtoField);
                    // Also update nested paths
                    if (dtoField.contains(".")) {
                        nestedPaths.set(existingIdx, dtoField.split("\\."));
                    } else {
                        nestedPaths.set(existingIdx, null);
                    }
                }
                return;
            }

            entityFields.add(entityField);
            dtoFields.add(dtoField);
            internal.add(status);

            // Pre-split nested paths
            if (dtoField.contains(".") && status != FieldStatus.SQL_ONLY) {
                nestedPaths.add(dtoField.split("\\."));
            } else {
                nestedPaths.add(null);
            }

        }

        // reducer == null For scalar dependency
        public void addComputedField(ComputedField field, String dtoField) {
            String[] dependencies = field.dependencies();
            ComputedField.ReducerMapping[] reducers = field.reducers();

            for (int i = 0; i < dependencies.length; i++) {
                var dependency = dependencies[i];

                // Check reduce function
                String reducer = null;
                // Because reducers are always a tiny list, it is much faster to check if a
                // dependency
                // have a reduce function by doing like this instead of using Set.contains()
                for (var r : reducers) {
                    if (r.dependencyIndex() == i)
                        reducer = r.reducer();
                }

                // Check if already added
                int idx = findEntityFieldDuplicateIndex(dependency);

                // Define slot
                if (idx == -1) {
                    idx = internal.size();
                    if (reducer == null) {
                        internal.add(FieldStatus.SQL_ONLY);
                        entityFields.add(dependency);
                    } else {
                        // This does not serve much! Also usage of 'PREFIX_FOR_COMPUTED' should prevent
                        // sql generation
                        // for this dependency. See the note below.
                        internal.add(FieldStatus.SQL_IGNORE); // Must keep lists synchronized!
                        entityFields.add(PREFIX_FOR_COMPUTED + dependency);
                    }

                    // NOTE: We also need to add this to always ensure that 'dtoFields' and
                    // 'entityFields' have the same length.
                    // Doing that allows us to simplify subsequent processing.
                    dtoFields.add(PREFIX_FOR_COMPUTED + dtoField + idx);
                    // Keep nestedPaths synchronized with other lists
                    nestedPaths.add(null);
                }

                DependencyInfo[] dependencyInfos = computedFieldIndexMap.computeIfAbsent(dtoField,
                        k -> new DependencyInfo[dependencies.length]);
                dependencyInfos[i] = new DependencyInfo(idx, reducer);
            }

            // Now add the field in Dto to have a slot for computed field outcome
            // (added ONCE per computed field, after processing all dependencies)
            // This slot is NOT internal - it's the visible output for the user
            entityFields.add(PREFIX_FOR_COMPUTED);
            dtoFields.add(dtoField);
            internal.add(FieldStatus.SQL_IGNORE); // computed output is visible to user
            nestedPaths.add(null); // Keep nestedPaths synchronized
        }

        private int findEntityFieldDuplicateIndex(String entityField) {
            for (int i = 0; i < entityFields.size(); i++) {
                if (entityFields.get(i).equals(entityField))
                    return i;
            }
            return -1;
        }

        /**
         * Adds a collection slot to the schema.
         *
         * @param dtoCollectionName DTO name for the collection
         */
        public void addCollection(String dtoCollectionName) {
            addField(dtoCollectionName, dtoCollectionName, FieldStatus.SQL_IGNORE_COLLECTION);
        }

        /**
         * Builds the FieldSchema.
         *
         * @return constructed FieldSchema
         */
        public FieldSchema build() {
            return new FieldSchema(this);
        }
    }

    public record DependencyInfo(int index, String reducer) {
    }

    public enum FieldStatus{
        SQL, // Generate the SQL by projecting the target field as its DTO alias
        SQL_ONLY, // When generating the result row exclude the target field but consider it for SQL
        SQL_IGNORE, // When generating SQL query, exclude the target field
        SQL_IGNORE_COLLECTION, // When generating SQL query, exclude the target field because it is a collection
    }
}

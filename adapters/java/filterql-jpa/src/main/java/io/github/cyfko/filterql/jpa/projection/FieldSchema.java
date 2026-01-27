package io.github.cyfko.filterql.jpa.projection;

import io.github.cyfko.projection.metamodel.model.projection.ComputedField;

import java.util.*;

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
    public static final String PREFIX_FOR_COMPUTED = "_c_";

    private final String[][] paths;

    /** Entity field names for query construction */
    private final String[] entityFields;

    /** DTO field names for result mapping */
    private final String[] dtoFields;

    /** Pre-split nested paths (null if not nested) */
    private final String[][] nestedPaths;

    /** Whether each field is internal (not exposed in final result) */
    private final boolean[] internal;

    private final int numInternalFields;

    /** Fast lookup: dtoField -> index */
    private final Map<String, Integer> dtoIndexMap;

    /** Fast lookup: entityField -> index */
    private final Map<String, Integer> entityIndexMap;

    /** Indices of collection slots */
    private final int[] collectionSlots;

    /** DTO names of collections at each collection slot */
    private final String[] collectionNames;

    /** Fast lookup: collectionName -> index */
    private final Map<String, Integer> collectionSlotMap;

    /** Total number of slots (fields + collections) */
    private final int totalSlots;

    /** Set of slot indices to exclude from output, or null for none */
    private Set<Integer> serialisationExcludedSlots = new HashSet<>();

    /** Fast lookup: computedField -> dependency indexes */
    private Map<String, DependencyInfo[]> computedFieldIndexMap = new HashMap<>();

    private final List<Boolean> reducers = new ArrayList<>();

    private FieldSchema(Builder builder) {
        this.entityFields = builder.entityFields.toArray(new String[0]);
        this.dtoFields = builder.dtoFields.toArray(new String[0]);
        this.nestedPaths = builder.nestedPaths.toArray(new String[0][]);
        this.internal = toPrimitiveBooleanArray(builder.internal);
        this.numInternalFields = countInternalFields(this.internal);
        this.collectionSlots = toPrimitiveIntArray(builder.collectionSlots);
        this.collectionNames = builder.collectionNames.toArray(new String[0]);
        this.paths = toCollectionNamePath(builder.collectionNames);
        this.totalSlots = entityFields.length + collectionSlots.length;
        this.computedFieldIndexMap = builder.computedFieldIndexMap;

        // Build index maps
        this.dtoIndexMap = new HashMap<>(dtoFields.length);
        this.entityIndexMap = new HashMap<>(entityFields.length);
        for (int i = 0; i < dtoFields.length; i++) {
            entityIndexMap.put(entityFields[i], i);
            dtoIndexMap.put(dtoFields[i], i);
        }

        // Build collection index maps
        this.collectionSlotMap = new HashMap<>(collectionSlots.length);
        for (int i = 0; i < collectionSlots.length; i++) {
            collectionSlotMap.put(collectionNames[i], i);
        }
    }

    public Map<String, DependencyInfo[]> getComputedFieldIndexMap() {
        return computedFieldIndexMap;
    }

    private String[][] toCollectionNamePath(List<String> collectionNames) {
        return collectionNames.stream().map(n -> n.split("\\.")).toArray(String[][]::new);
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

    /**
     * Creates a FieldSchema from scalar field mappings.
     *
     * @param mappings list of entity-to-DTO field mappings
     * @return constructed FieldSchema
     */
    public static FieldSchema fromMappings(List<MultiQueryExecutionPlan.ScalarFieldMapping> mappings) {
        Builder builder = builder();
        for (MultiQueryExecutionPlan.ScalarFieldMapping mapping : mappings) {
            builder.addField(mapping.entityField(), mapping.dtoField(), mapping.isInternal());
        }
        return builder.build();
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
        return totalSlots;
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
        return internal[index];
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
            idx = collectionSlotMap.get(dtoField);
            return idx != null ? new Indexer(idx, true) : Indexer.NONE;
        }
        return new Indexer(idx, false);
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

    /**
     * Retrieve the set of slot indices to exclude from output, or null none
     * 
     * @return excluded slots.
     */
    public Set<Integer> getSerialisationExcludedSlots() {
        return serialisationExcludedSlots;
    }

    /**
     * Exclude the provided specified slots on serialization.
     *
     * @param serialisationExcludedSlots set of slot indices to exclude from
     *                                   serialisation output, or null for none
     */
    public void setSerialisationExcludedSlots(Set<Integer> serialisationExcludedSlots) {
        this.serialisationExcludedSlots = Objects.requireNonNull(serialisationExcludedSlots);
    }

    public int getNumberOfInternalFields() {
        return numInternalFields;
    }

    // ==================== Collection Access ====================

    /**
     * Returns the number of collection slots.
     *
     * @return collection count
     */
    public int collectionCount() {
        return collectionSlots.length;
    }

    /**
     * Returns the slot index for the i-th collection.
     *
     * @param collectionIndex collection index (0-based)
     * @return slot index in RowBuffer
     */
    public int collectionSlot(int collectionIndex) {
        return collectionSlots[collectionIndex];
    }

    /**
     * Returns the DTO name for the i-th collection.
     *
     * @param collectionIndex collection index (0-based)
     * @return collection DTO name
     */
    public String collectionName(int collectionIndex) {
        return collectionNames[collectionIndex];
    }

    public String[] collectionPaths(int collectionIndex) {
        return paths[collectionIndex];
    }

    // ==================== Utility Methods ====================

    private static boolean[] toPrimitiveBooleanArray(List<Boolean> list) {
        boolean[] arr = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private static int[] toPrimitiveIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private static int countInternalFields(boolean[] internal) {
        int numFields = 0;
        for (boolean b : internal) {
            if (b)
                numFields++;
        }
        return numFields;
    }

    // ==================== Builder ====================

    /**
     * Builder for constructing FieldSchema instances.
     */
    public static final class Builder {
        private final List<String> entityFields = new ArrayList<>();
        private final List<String> dtoFields = new ArrayList<>();
        private final List<String[]> nestedPaths = new ArrayList<>();
        private final List<Boolean> internal = new ArrayList<>();
        private final List<Integer> collectionSlots = new ArrayList<>();
        private final List<String> collectionNames = new ArrayList<>();
        private final Map<String, DependencyInfo[]> computedFieldIndexMap = new HashMap<>();

        private Builder() {
        }

        /**
         * Adds a field to the schema.
         *
         * @param entityField entity field name (for queries)
         * @param dtoField    DTO field name (for results)
         * @param isInternal  true if field should not appear in final result
         * @return this builder
         */
        public Builder addField(String entityField, String dtoField, boolean isInternal) {
            // Check if already added
            if (findEntityFieldDuplicateIndex(entityField, isInternal) > -1)
                return this;

            entityFields.add(entityField);
            dtoFields.add(dtoField);
            internal.add(isInternal);

            // Pre-split nested paths
            if (dtoField.contains(".") && !isInternal) {
                nestedPaths.add(dtoField.split("\\."));
            } else {
                nestedPaths.add(null);
            }

            return this;
        }

        // reducer == null For scalar dependency
        public Builder addComputedField(ComputedField field, String dtoField) {
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
                int idx = findEntityFieldDuplicateIndex(dependency, true);

                // Define slot
                if (idx == -1) {
                    idx = internal.size();
                    if (reducer == null) {
                        internal.add(true);
                        entityFields.add(dependency);
                    } else {
                        // This does not serve much! Also usage of 'PREFIX_FOR_COMPUTED' should prevent
                        // sql generation
                        // for this dependency. See the note below.
                        internal.add(true); // Must keep lists synchronized!
                        entityFields.add(PREFIX_FOR_COMPUTED + dependency);
                    }

                    // NOTE: We also need to add this to always ensure that 'dtoFields' and
                    // 'entityFields' have the same length.
                    // Doing that allows us to simplify subsequent processing.
                    dtoFields.add(PREFIX_FOR_COMPUTED + dtoField + idx);
                }

                DependencyInfo[] dependencyInfos = computedFieldIndexMap.computeIfAbsent(dtoField,
                        k -> new DependencyInfo[dependencies.length]);
                dependencyInfos[i] = new DependencyInfo(idx, reducer);

                // Now add the field in Dto to have a slot for computed field outcome
                entityFields.add(PREFIX_FOR_COMPUTED);
                dtoFields.add(dtoField);
                internal.add(true);
            }

            return this;
        }

        private int findEntityFieldDuplicateIndex(String entityField, boolean isInternal) {
            for (int i = 0; i < entityFields.size(); i++) {
                if (!isInternal && internal.get(i)) {
                    internal.set(i, false);
                }
                if (entityFields.get(i).equals(entityField))
                    return i;
            }
            return -1;
        }

        /**
         * Adds a collection slot to the schema.
         *
         * @param dtoCollectionName DTO name for the collection
         * @return this builder
         */
        public Builder addCollection(String dtoCollectionName) {
            // Collection slots come after field slots
            int slotIndex = entityFields.size() + collectionSlots.size();
            collectionSlots.add(slotIndex);
            collectionNames.add(dtoCollectionName);
            return this;
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
}

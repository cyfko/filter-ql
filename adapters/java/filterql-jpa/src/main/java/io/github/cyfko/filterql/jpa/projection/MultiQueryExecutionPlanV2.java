package io.github.cyfko.filterql.jpa.projection;

import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.SortBy;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import io.github.cyfko.projection.metamodel.model.PersistenceMetadata;
import io.github.cyfko.projection.metamodel.model.projection.ComputedField;
import io.github.cyfko.projection.metamodel.model.projection.ComputedField.ReducerMapping;
import io.github.cyfko.projection.metamodel.model.projection.ProjectionMetadata;
import jakarta.persistence.criteria.Root;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimized execution plan with integrated {@link FieldSchema} for O(1) field
 * access.
 * <p>
 * Key improvements over V1 {@link MultiQueryExecutionPlan}:
 * <ul>
 * <li>Integrated FieldSchema for indexed access instead of string lookups</li>
 * <li>Pre-computed sort field indices</li>
 * <li>Pre-resolved computed field dependencies</li>
 * <li>Flattened collection plan array for direct access</li>
 * </ul>
 * </p>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public final class MultiQueryExecutionPlanV2 {

    public static final String PREFIX_FOR_INTERNAL_USAGE = "_i_";

    private final Class<?> rootEntityClass;
    private final FieldSchema rootSchema;
    private final List<String> rootIdFields;
    private final CollectionPlanV2[] collectionPlans;
    private final ComputedFieldInfo[] computedFields;

    private MultiQueryExecutionPlanV2(
            Class<?> rootEntityClass,
            FieldSchema rootSchema,
            List<String> rootIdFields,
            CollectionPlanV2[] collectionPlans,
            ComputedFieldInfo[] computedFields) {
        this.rootEntityClass = rootEntityClass;
        this.rootSchema = rootSchema;
        this.rootIdFields = Collections.unmodifiableList(rootIdFields);
        this.collectionPlans = collectionPlans;
        this.computedFields = computedFields;
    }

    // ==================== Factory ====================

    /**
     * Builds an optimized execution plan from entity projection specification.
     *
     * @param <E>      root entity type
     * @param root     JPA root for path resolution
     * @param ps       projection specification
     * @param dtoClass the DTO class for accessing projection metadata
     * @return optimized execution plan
     */
    public static <E> MultiQueryExecutionPlanV2 build(Root<E> root, MultiQueryExecutionPlan.ProjectionSpec ps,
            Class<?> dtoClass) {
        @SuppressWarnings("unchecked")
        Class<E> rootEntityClass = (Class<E>) root.getJavaType();

        // 0. Expand compact notation
        Set<String> expandedProjection = MultiQueryExecutionPlan.expandCompactNotation(ps.entityFields());

        // 1. Resolve all paths
        Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath = new LinkedHashMap<>();
        for (String field : expandedProjection) {
            PathResolverUtils.PathResolutionMetadata meta = PathResolverUtils.resolvePathWithMeta(root, field);
            metadataByPath.put(field, meta);
        }

        // 2. Find root entity ID fields
        List<String> rootIdFields = PersistenceRegistry.getIdFields(rootEntityClass);

        // 3. Build root schema and separate collections
        FieldSchema.Builder schemaBuilder = FieldSchema.builder();
        Map<Integer, List<CollectionPathInfo>> collectionsByDepth = new TreeMap<>();
        Set<String> computedFieldNames = ps.computedFields();

        for (Map.Entry<String, PathResolverUtils.PathResolutionMetadata> entry : metadataByPath.entrySet()) {
            String entityField = entry.getKey();
            PathResolverUtils.PathResolutionMetadata meta = entry.getValue();

            if (!meta.hasCollections()) {
                String dtoField = ps.entityToDtoMapping().getOrDefault(entityField, entityField);
                boolean isComputed = computedFieldNames.contains(dtoField);
                schemaBuilder.addField(entityField, isComputed ? entityField : dtoField, false);
            } else {
                int depth = meta.collectionSegments().size();
                collectionsByDepth.computeIfAbsent(depth, k -> new ArrayList<>())
                        .add(new CollectionPathInfo(entityField, meta));
            }
        }

        // Add dedicated slots for computed field outputs (using DTO field name)
        for (String computedDtoField : computedFieldNames) {
            schemaBuilder.addField("_computed_" + computedDtoField, computedDtoField, false);
        }

        // 4. Always include root ID fields
        Set<String> includedEntityFields = expandedProjection.stream()
                .filter(f -> !metadataByPath.get(f).hasCollections())
                .collect(Collectors.toSet());

        for (String idField : rootIdFields) {
            if (!includedEntityFields.contains(idField)) {
                schemaBuilder.addField(idField, PREFIX_FOR_INTERNAL_USAGE + idField, true);
            }
        }

        // 5. First pass: identify all collection paths and their parent-child
        // relationships
        Map<String, Set<String>> childCollectionsByParent = new LinkedHashMap<>();
        childCollectionsByParent.put("", new LinkedHashSet<>()); // Root's direct children

        List<String> allCollectionPaths = new ArrayList<>();
        for (Map.Entry<Integer, List<CollectionPathInfo>> depthEntry : collectionsByDepth.entrySet()) {
            int depth = depthEntry.getKey();
            Map<String, List<CollectionPathInfo>> byCollection = groupByCollectionPath(depthEntry.getValue(), depth);
            for (String collPath : byCollection.keySet()) {
                allCollectionPaths.add(collPath);
                String parent = getParentPath(collPath);
                childCollectionsByParent.computeIfAbsent(parent, k -> new LinkedHashSet<>()).add(collPath);
            }
        }

        // 5b. Add collection slots for direct children (depth 1) to root schema
        for (String directChild : childCollectionsByParent.getOrDefault("", Set.of())) {
            String collectionName = directChild.contains(".")
                    ? directChild.substring(directChild.lastIndexOf('.') + 1)
                    : directChild;
            schemaBuilder.addCollection(collectionName);
        }

        // 6. Build collection plans, enriching each with slots for its sub-collections
        List<CollectionPlanV2> collectionPlanList = new ArrayList<>();

        for (Map.Entry<Integer, List<CollectionPathInfo>> depthEntry : collectionsByDepth.entrySet()) {
            int depth = depthEntry.getKey();
            List<CollectionPathInfo> paths = depthEntry.getValue();

            // Group by collection path
            Map<String, List<CollectionPathInfo>> byCollection = groupByCollectionPath(paths, depth);

            for (Map.Entry<String, List<CollectionPathInfo>> collEntry : byCollection.entrySet()) {
                String collectionPath = collEntry.getKey();
                List<CollectionPathInfo> pathsInColl = collEntry.getValue();

                // Find sub-collections of this collection
                Set<String> subCollNames = new LinkedHashSet<>();
                for (String subCollPath : childCollectionsByParent.getOrDefault(collectionPath, Set.of())) {
                    String subCollName = subCollPath.substring(subCollPath.lastIndexOf('.') + 1);
                    subCollNames.add(subCollName);
                }

                CollectionPlanV2 plan = buildCollectionPlan(
                        rootEntityClass, collectionPath, pathsInColl, depth,
                        metadataByPath, ps.collectionPagination(), ps.entityToDtoMapping(),
                        subCollNames);

                collectionPlanList.add(plan);
            }
        }

        FieldSchema rootSchema = schemaBuilder.build();

        // 7. Build computed field info
        ComputedFieldInfo[] computedFieldsArray = buildComputedFieldInfo(
                rootSchema, ps.entityToDtoMapping(), computedFieldNames, dtoClass);

        return new MultiQueryExecutionPlanV2(
                rootEntityClass,
                rootSchema,
                rootIdFields,
                collectionPlanList.toArray(new CollectionPlanV2[0]),
                computedFieldsArray);
    }

    private static String getParentPath(String collectionPath) {
        if (!collectionPath.contains(".")) {
            return "";
        }
        return collectionPath.substring(0, collectionPath.lastIndexOf('.'));
    }

    private static Map<String, List<CollectionPathInfo>> groupByCollectionPath(
            List<CollectionPathInfo> paths, int targetDepth) {
        Map<String, List<CollectionPathInfo>> grouped = new LinkedHashMap<>();

        for (CollectionPathInfo info : paths) {
            List<String> collectionSegments = info.meta.collectionSegments();
            List<String> allSegments = info.meta.allSegments();

            // Find the collection path up to target depth
            int collCount = 0;
            int collIndex = -1;
            for (int i = 0; i < allSegments.size(); i++) {
                if (collectionSegments.contains(allSegments.get(i))) {
                    collCount++;
                    if (collCount == targetDepth) {
                        collIndex = i;
                        break;
                    }
                }
            }

            if (collIndex >= 0) {
                String collectionPath = String.join(".", allSegments.subList(0, collIndex + 1));
                grouped.computeIfAbsent(collectionPath, k -> new ArrayList<>()).add(info);
            }
        }

        return grouped;
    }

    private static CollectionPlanV2 buildCollectionPlan(
            Class<?> rootEntityClass,
            String collectionPath,
            List<CollectionPathInfo> pathsInCollection,
            int depth,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath,
            Map<String, Pagination> optionsByCollection,
            Map<String, String> entityToDtoFieldMap,
            Set<String> subCollectionNames) {
        CollectionPathInfo sampleInfo = pathsInCollection.get(0);
        String collectionFieldName = collectionPath.substring(collectionPath.lastIndexOf('.') + 1);

        PathResolverUtils.CollectionInfo collInfo = sampleInfo.meta.getCollectionInfo(collectionFieldName);
        if (collInfo == null) {
            throw new IllegalStateException("No collection info for: " + collectionFieldName);
        }

        Class<?> parentEntityClass = determineParentEntityClass(rootEntityClass, collectionPath, metadataByPath);
        Map<String, PersistenceMetadata> parentMetadata = PersistenceRegistry.getMetadataFor(parentEntityClass);

        if (parentMetadata == null) {
            throw new IllegalStateException("No metadata for parent: " + parentEntityClass.getName());
        }

        PersistenceMetadata collMeta = parentMetadata.get(collectionFieldName);
        if (collMeta == null || !collMeta.isCollection()) {
            throw new IllegalStateException("Field is not a collection: " + collectionFieldName);
        }

        Class<?> elementClass = collMeta.relatedType();
        List<String> elementIdFields = PersistenceRegistry.getIdFields(elementClass);

        String parentRefField = determineParentReferenceField(collMeta, parentEntityClass, elementClass);

        // Build child schema
        FieldSchema.Builder childSchemaBuilder = FieldSchema.builder();
        int collectionPathLength = collectionPath.split("\\.").length;

        for (CollectionPathInfo info : pathsInCollection) {
            List<String> allSegments = info.meta.allSegments();

            if (allSegments.size() > collectionPathLength) {
                String entityFieldPath = String.join(".",
                        allSegments.subList(collectionPathLength, allSegments.size()));
                String dtoFieldPath = entityToDtoFieldMap.getOrDefault(info.entityField, entityFieldPath);

                // Extract just field name for nested
                String dtoFieldName = dtoFieldPath.contains(".")
                        ? dtoFieldPath.substring(dtoFieldPath.lastIndexOf('.') + 1)
                        : dtoFieldPath;

                childSchemaBuilder.addField(entityFieldPath, dtoFieldName, false);
            }
        }

        // Always include element IDs
        Set<String> includedFields = pathsInCollection.stream()
                .map(i -> {
                    List<String> segs = i.meta.allSegments();
                    if (segs.size() > collectionPathLength) {
                        return String.join(".", segs.subList(collectionPathLength, segs.size()));
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String idField : elementIdFields) {
            if (!includedFields.contains(idField)) {
                childSchemaBuilder.addField(idField, PREFIX_FOR_INTERNAL_USAGE + idField, true);
            }
        }

        // Add slots for sub-collections (if any)
        for (String subCollName : subCollectionNames) {
            childSchemaBuilder.addCollection(subCollName);
        }

        FieldSchema childSchema = childSchemaBuilder.build();

        // Get pagination options
        Pagination options = optionsByCollection != null ? optionsByCollection.get(collectionPath) : null;

        // Pre-compute sort field indices
        int[] sortFieldIndices;
        boolean[] sortDescending;
        if (options != null && options.sort() != null && !options.sort().isEmpty()) {
            List<SortBy> sorts = options.sort();
            sortFieldIndices = new int[sorts.size()];
            sortDescending = new boolean[sorts.size()];

            for (int i = 0; i < sorts.size(); i++) {
                String sortField = sorts.get(i).field();
                int idx = childSchema.indexOfEntity(sortField);
                if (idx < 0) {
                    // Try DTO field
                    idx = childSchema.indexOfDto(sortField);
                }
                sortFieldIndices[i] = idx >= 0 ? idx : 0;
                sortDescending[i] = "desc".equalsIgnoreCase(sorts.get(i).direction());
            }
        } else {
            sortFieldIndices = new int[0];
            sortDescending = new boolean[0];
        }

        // Map collection to DTO name
        String dtoCollectionName = entityToDtoFieldMap.getOrDefault(collectionPath, collectionFieldName);

        return new CollectionPlanV2(
                depth,
                collectionPath,
                dtoCollectionName,
                parentRefField,
                elementIdFields,
                elementClass,
                childSchema,
                options != null ? options.size() : null,
                options != null ? options.offset() : null,
                sortFieldIndices,
                sortDescending);
    }

    private static Class<?> determineParentEntityClass(
            Class<?> rootEntityClass,
            String collectionPath,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath) {
        String[] segments = collectionPath.split("\\.");
        if (segments.length == 1) {
            return rootEntityClass;
        }

        String parentCollPath = String.join(".", Arrays.copyOf(segments, segments.length - 1));

        for (PathResolverUtils.PathResolutionMetadata meta : metadataByPath.values()) {
            if (meta.allSegments().size() > segments.length - 1) {
                String pathPrefix = String.join(".", meta.allSegments().subList(0, segments.length - 1));
                if (pathPrefix.equals(parentCollPath)) {
                    String parentField = segments[segments.length - 2];
                    PathResolverUtils.CollectionInfo parentInfo = meta.getCollectionInfo(parentField);
                    if (parentInfo != null && parentInfo.isEntityCollection()) {
                        Class<?> grandParent = determineParentEntityClass(rootEntityClass, parentCollPath,
                                metadataByPath);
                        return PersistenceRegistry.getMetadataFor(grandParent).get(parentField).relatedType();
                    }
                }
            }
        }

        throw new IllegalStateException("Cannot determine parent class for: " + collectionPath);
    }

    private static String determineParentReferenceField(
            PersistenceMetadata collMeta,
            Class<?> parentEntityClass,
            Class<?> elementClass) {
        Optional<String> mappedBy = collMeta.getMappedBy();
        if (mappedBy.isPresent()) {
            return mappedBy.get();
        }

        Map<String, PersistenceMetadata> elementMetadata = PersistenceRegistry.getMetadataFor(elementClass);
        if (elementMetadata != null) {
            String parentClassName = parentEntityClass.getName();
            for (Map.Entry<String, PersistenceMetadata> entry : elementMetadata.entrySet()) {
                if (entry.getValue().relatedType().equals(parentClassName)) {
                    return entry.getKey();
                }
            }
        }

        String simpleName = parentEntityClass.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private static ComputedFieldInfo[] buildComputedFieldInfo(
            FieldSchema rootSchema,
            Map<String, String> entityToDtoMapping,
            Set<String> computedFieldNames,
            Class<?> dtoClass) {
        List<ComputedFieldInfo> infos = new ArrayList<>();

        // Get projection metadata for accessing reducer info
        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(dtoClass);

        for (Map.Entry<String, String> entry : entityToDtoMapping.entrySet()) {
            String entityPath = entry.getKey();
            String dtoField = entry.getValue();

            if (computedFieldNames.contains(dtoField)) {
                // entityPath may contain comma-separated dependencies
                String[] dependencyPaths = entityPath.split(",");
                int[] dependencySlots = new int[dependencyPaths.length];

                // Extract reducers from metadata first to know which deps have reducers
                ReducerMapping[] reducers = new ReducerMapping[0];
                Set<Integer> reducerDependencyIndices = new HashSet<>();
                if (metadata != null) {
                    var computedField = metadata.getComputedField(dtoField, false);
                    if (computedField.isPresent() && computedField.get().hasReducers()) {
                        reducers = computedField.get().reducers();
                        for (ReducerMapping rm : reducers) {
                            reducerDependencyIndices.add(rm.dependencyIndex());
                        }
                    }
                }

                for (int i = 0; i < dependencyPaths.length; i++) {
                    String dep = dependencyPaths[i].trim();

                    // Skip slot lookup for dependencies with reducers - they're resolved by
                    // aggregate queries
                    if (reducerDependencyIndices.contains(i)) {
                        dependencySlots[i] = -1; // Will be resolved via aggregate query
                    } else {
                        int idx = rootSchema.indexOfEntity(dep);
                        dependencySlots[i] = idx >= 0 ? idx : -1;
                    }
                }

                // Output slot is where the computed value goes (lookup by DTO field name)
                int outputSlot = rootSchema.indexOfDto(dtoField);

                infos.add(new ComputedFieldInfo(dtoField, outputSlot, dependencySlots, dependencyPaths, reducers));
            }
        }

        return infos.toArray(new ComputedFieldInfo[0]);
    }

    // ==================== Accessors ====================

    public Class<?> rootEntityClass() {
        return rootEntityClass;
    }

    public FieldSchema rootSchema() {
        return rootSchema;
    }

    public List<String> rootIdFields() {
        return rootIdFields;
    }

    public CollectionPlanV2[] collectionPlans() {
        return collectionPlans;
    }

    public boolean hasCollections() {
        return collectionPlans.length > 0;
    }

    public ComputedFieldInfo[] computedFields() {
        return computedFields;
    }

    public boolean hasComputedFields() {
        return computedFields.length > 0;
    }

    // ==================== Inner Classes ====================

    private record CollectionPathInfo(String entityField, PathResolverUtils.PathResolutionMetadata meta) {
    }

    /**
     * Pre-computed plan for a single collection with integrated schema.
     */
    public record CollectionPlanV2(
            int depth,
            String collectionPath,
            String dtoCollectionName,
            String parentReferenceField,
            List<String> idFields,
            Class<?> elementClass,
            FieldSchema childSchema,
            Integer limitPerParent,
            Integer offsetPerParent,
            int[] sortFieldIndices,
            boolean[] sortDescending) {
    }

    /**
     * Pre-computed info for computed field resolution.
     */
    public record ComputedFieldInfo(
            String dtoFieldName,
            int outputSlot,
            int[] dependencySlots,
            String[] dependencyPaths, // For fallback lookup
            ReducerMapping[] reducers // Reducers for aggregate fields
    ) {
        /**
         * @return true if this computed field has aggregate reducers
         */
        public boolean isAggregate() {
            return reducers != null && reducers.length > 0;
        }
    }
}

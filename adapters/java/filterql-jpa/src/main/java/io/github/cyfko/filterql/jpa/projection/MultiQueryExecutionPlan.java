package io.github.cyfko.filterql.jpa.projection;

import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.SortBy;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.model.PersistenceMetadata;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils.CollectionInfo;
import jakarta.persistence.criteria.Root;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Execution plan for multi-query projection strategy with DTO-first approach.
 * <p>
 * Key innovation: Uses DTO field names throughout the result tree while keeping
 * entity field names only for query construction. This eliminates the need for
 * post-processing transformation.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>DTO field names in CollectionNode.collectionName and
 * ScalarFieldMapping</li>
 * <li>Entity field names preserved only for query path resolution</li>
 * <li>Compact notation support with automatic expansion</li>
 * <li>Composite key support via PersistenceRegistry</li>
 * <li>Hierarchical collection navigation</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public class MultiQueryExecutionPlan {
    public static final String PREFIX_FOR_INTERNAL_USAGE = "_i_";

    private final Class<?> rootEntityClass;
    private final List<ScalarFieldMapping> rootScalarFields;
    private final List<String> rootIdFields;
    private final List<CollectionLevelPlan> collectionLevels;
    private final Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath;

    private MultiQueryExecutionPlan(
            Class<?> rootEntityClass,
            List<ScalarFieldMapping> rootScalarFields,
            List<String> rootIdFields,
            List<CollectionLevelPlan> collectionLevels,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath) {
        this.rootEntityClass = rootEntityClass;
        this.rootScalarFields = Collections.unmodifiableList(rootScalarFields);
        this.rootIdFields = rootIdFields;
        this.collectionLevels = Collections.unmodifiableList(collectionLevels);
        this.metadataByPath = Collections.unmodifiableMap(metadataByPath);
    }

    /**
     * Builds an execution plan from entity projection with DTO field mapping.
     *
     * @param <E>  root entity type
     * @param root JPA root for path resolution
     * @param ps   projection specification
     * @return execution plan
     */
    public static <E> MultiQueryExecutionPlan build(Root<E> root, ProjectionSpec ps) {
        // noinspection unchecked
        Class<E> rootEntityClass = (Class<E>) root.getJavaType();

        // 0. Expand compact notation
        Set<String> expandedProjection = expandCompactNotation(ps.entityFields);

        // 1. Resolve all paths using PathResolverUtils
        Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath = new LinkedHashMap<>();
        for (String field : expandedProjection) {
            PathResolverUtils.PathResolutionMetadata meta = PathResolverUtils.resolvePathWithMeta(root, field);
            metadataByPath.put(field, meta);
        }

        // 2. Find root entity ID fields
        List<String> rootIdFields = PersistenceRegistry.getIdFields(rootEntityClass);

        // 3. Separate root scalar fields from collection paths
        List<ScalarFieldMapping> rootScalarFields = new ArrayList<>();
        Map<Integer, Set<String>> pathsByDepth = new TreeMap<>();

        for (Map.Entry<String, PathResolverUtils.PathResolutionMetadata> entry : metadataByPath.entrySet()) {
            String entityField = entry.getKey();
            PathResolverUtils.PathResolutionMetadata meta = entry.getValue();

            if (!meta.hasCollections()) {
                // Scalar field - map to DTO name
                String dtoField = ps.entityToDtoMapping.getOrDefault(entityField, entityField);
                rootScalarFields.add(new ScalarFieldMapping(entityField,
                        ps.computedFields.contains(dtoField) ? entityField : dtoField));
            } else {
                // Collection path
                int depth = meta.collectionSegments().size();
                pathsByDepth.computeIfAbsent(depth, k -> new LinkedHashSet<>()).add(entityField);

                // Also insert mapping for collection names (without fields)
                String dtoField = ps.entityToDtoMapping.getOrDefault(entityField, entityField);
                int dotIndex = dtoField.lastIndexOf('.');
                dtoField = dotIndex == -1 ? dtoField : dtoField.substring(0, dotIndex);

                dotIndex = entityField.lastIndexOf('.');
                entityField = dotIndex == -1 ? entityField : entityField.substring(0, dotIndex);

                // ok
                ps.entityToDtoMapping.putIfAbsent(entityField, dtoField);
            }
        }

        // 4. Always include root ID (but don't expose in DTO if not requested)
        Set<String> includedFields = rootScalarFields.stream().map(ScalarFieldMapping::entityField)
                .collect(Collectors.toSet());
        for (String idField : rootIdFields) {
            if (includedFields.contains(idField))
                continue;
            rootScalarFields.add(ScalarFieldMapping.internal(idField));
        }

        // 5. Build collection level plans
        List<CollectionLevelPlan> collectionLevels = buildCollectionLevels(
                rootEntityClass,
                pathsByDepth,
                metadataByPath,
                ps.collectionPagination,
                ps.entityToDtoMapping);

        return new MultiQueryExecutionPlan(
                rootEntityClass,
                rootScalarFields,
                rootIdFields,
                collectionLevels,
                metadataByPath);
    }

    /**
     * Expands compact comma notation into individual paths.
     * <p>
     * Examples:
     * <ul>
     * <li>"address.city,street" → ["address.city", "address.street"]</li>
     * <li>"firstName,lastName" → ["firstName", "lastName"]</li>
     * <li>"orders.items.productName,quantity" → ["orders.items.productName",
     * "orders.items.quantity"]</li>
     * </ul>
     * </p>
     *
     * @param projection projection set (may contain comma notation)
     * @return expanded projection set
     */
    public static Set<String> expandCompactNotation(Set<String> projection) {
        return expandCompactNotation(projection.toArray(new String[projection.size()]));
    }

    /**
     * Expands compact comma notation into individual paths.
     * <p>
     * Examples:
     * <ul>
     * <li>"address.city,street" → ["address.city", "address.street"]</li>
     * <li>"firstName,lastName" → ["firstName", "lastName"]</li>
     * <li>"orders.items.productName,quantity" → ["orders.items.productName",
     * "orders.items.quantity"]</li>
     * </ul>
     * </p>
     *
     * @param projection projection set (may contain comma notation)
     * @return expanded projection set
     */
    public static Set<String> expandCompactNotation(String... projection) {
        Set<String> expanded = new LinkedHashSet<>();

        for (String field : projection) {
            if (!field.contains(",")) {
                expanded.add(field);
                continue;
            }

            // Find the last dot before the first comma
            int firstComma = field.indexOf(',');
            int lastDotBeforeComma = field.lastIndexOf('.', firstComma);

            String prefix = "";
            String compactPart = field;

            if (lastDotBeforeComma > 0) {
                prefix = field.substring(0, lastDotBeforeComma + 1);
                compactPart = field.substring(lastDotBeforeComma + 1);
            }

            // Split by comma and reconstruct
            String[] parts = compactPart.split(",");
            for (String part : parts) {
                expanded.add(prefix + part.trim());
            }
        }

        return expanded;
    }

    /**
     * Builds collection level plans by grouping paths by depth.
     */
    private static List<CollectionLevelPlan> buildCollectionLevels(
            Class<?> rootEntityClass,
            Map<Integer, Set<String>> pathsByDepth,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath,
            Map<String, Pagination> optionsByCollection,
            Map<String, String> entityToDtoFieldMap) {
        List<CollectionLevelPlan> levels = new ArrayList<>();

        for (Map.Entry<Integer, Set<String>> entry : pathsByDepth.entrySet()) {
            int depth = entry.getKey();
            Set<String> pathsAtDepth = entry.getValue();

            Map<String, List<String>> pathsByCollection = groupPathsByCollectionAtDepth(
                    pathsAtDepth,
                    metadataByPath,
                    depth);

            Set<CollectionNode> nodes = new LinkedHashSet<>();

            for (Map.Entry<String, List<String>> collectionEntry : pathsByCollection.entrySet()) {
                String collectionPath = collectionEntry.getKey();
                List<String> pathsInCollection = collectionEntry.getValue();

                CollectionNode node = buildCollectionNode(
                        rootEntityClass,
                        collectionPath,
                        pathsInCollection,
                        depth,
                        metadataByPath,
                        optionsByCollection,
                        entityToDtoFieldMap);

                nodes.add(node);
            }

            levels.add(new CollectionLevelPlan(depth, nodes));
        }

        return levels;
    }

    private static Map<String, List<String>> groupPathsByCollectionAtDepth(
            Set<String> paths,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath,
            int targetDepth) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();

        for (String path : paths) {
            PathResolverUtils.PathResolutionMetadata meta = metadataByPath.get(path);
            List<String> collectionSegments = meta.collectionSegments();

            if (collectionSegments.size() < targetDepth) {
                continue;
            }

            List<String> allSegments = meta.allSegments();
            int collectionIndex = findNthOccurrence(allSegments, collectionSegments, targetDepth);
            String collectionPath = String.join(".", allSegments.subList(0, collectionIndex + 1));

            grouped.computeIfAbsent(collectionPath, k -> new ArrayList<>()).add(path);
        }

        return grouped;
    }

    private static int findNthOccurrence(List<String> allSegments, List<String> collectionSegments, int n) {
        int count = 0;
        for (int i = 0; i < allSegments.size(); i++) {
            if (collectionSegments.contains(allSegments.get(i))) {
                count++;
                if (count == n) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Cannot find " + n + "th collection segment");
    }

    private static CollectionNode buildCollectionNode(
            Class<?> rootEntityClass,
            String collectionPath,
            List<String> pathsInCollection,
            int depth,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath,
            Map<String, Pagination> optionsByCollection,
            Map<String, String> entityToDtoFieldMap) {
        String samplePath = pathsInCollection.get(0);
        PathResolverUtils.PathResolutionMetadata sampleMeta = metadataByPath.get(samplePath);

        String entityCollectionFieldName = collectionPath.substring(collectionPath.lastIndexOf('.') + 1);

        CollectionInfo collectionInfo = sampleMeta.getCollectionInfo(entityCollectionFieldName);
        if (collectionInfo == null) {
            throw new IllegalStateException(
                    "No collection info found for: " + entityCollectionFieldName + " in path: " + collectionPath);
        }

        Class<?> parentEntityClass = determineParentEntityClass(rootEntityClass, collectionPath, metadataByPath);
        Map<String, PersistenceMetadata> parentMetadata = PersistenceRegistry.getMetadataFor(parentEntityClass);

        if (parentMetadata == null) {
            throw new IllegalStateException("No metadata for parent entity: " + parentEntityClass.getName());
        }

        PersistenceMetadata collectionFieldMetadata = parentMetadata.get(entityCollectionFieldName);
        if (collectionFieldMetadata == null || !collectionFieldMetadata.isCollection()) {
            throw new IllegalStateException(
                    String.format("Field '%s' is not a collection in %s",
                            entityCollectionFieldName, parentEntityClass.getSimpleName()));
        }

        Class<?> elementClass = collectionFieldMetadata.relatedType();
        List<String> elementIdFields = PersistenceRegistry.getIdFields(elementClass);

        String parentReferenceField = determineParentReferenceField(
                collectionFieldMetadata,
                parentEntityClass,
                elementClass);

        // Extract fields to select with DTO mapping
        List<ScalarFieldMapping> fieldsToSelect = extractFieldsToSelect(
                collectionPath,
                pathsInCollection,
                metadataByPath,
                entityToDtoFieldMap);

        // Always include element IDs (internal, not exposed in DTO)
        Set<String> includedFields = fieldsToSelect.stream().map(ScalarFieldMapping::entityField)
                .collect(Collectors.toSet());
        for (String idField : elementIdFields) {
            if (includedFields.contains(idField))
                continue;
            fieldsToSelect.add(ScalarFieldMapping.internal(idField));
        }

        Pagination options = optionsByCollection != null ? optionsByCollection.get(collectionPath) : null;

        // Map collection name to DTO
        String dtoCollectionName = entityToDtoFieldMap.getOrDefault(collectionPath, entityCollectionFieldName);

        return new CollectionNode(
                collectionPath,
                dtoCollectionName,
                parentReferenceField,
                elementIdFields,
                elementClass,
                fieldsToSelect,
                options != null ? options.size() : null,
                options != null ? options.offset() : null,
                options != null ? options.sort() : List.of(),
                false);
    }

    private static String determineParentReferenceField(
            PersistenceMetadata collectionMetadata,
            Class<?> parentEntityClass,
            Class<?> elementClass) {
        Optional<String> mappedBy = collectionMetadata.getMappedBy();
        if (mappedBy.isPresent()) {
            return mappedBy.get();
        }

        Map<String, PersistenceMetadata> elementMetadata = PersistenceRegistry.getMetadataFor(elementClass);
        if (elementMetadata != null) {
            String parentClassName = parentEntityClass.getName();

            for (Map.Entry<String, PersistenceMetadata> entry : elementMetadata.entrySet()) {
                String fieldName = entry.getKey();
                PersistenceMetadata fieldMetadata = entry.getValue();

                if (fieldMetadata.relatedType().equals(parentClassName)) {
                    return fieldName;
                }
            }
        }

        String simpleName = parentEntityClass.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private static Class<?> determineParentEntityClass(
            Class<?> rootEntityClass,
            String collectionPath,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath) {
        String[] segments = collectionPath.split("\\.");

        if (segments.length == 1) {
            return rootEntityClass;
        }

        String parentCollectionPath = String.join(".", Arrays.copyOf(segments, segments.length - 1));

        for (PathResolverUtils.PathResolutionMetadata meta : metadataByPath.values()) {
            if (meta.allSegments().size() > segments.length - 1) {
                String pathPrefix = String.join(".", meta.allSegments().subList(0, segments.length - 1));
                if (pathPrefix.equals(parentCollectionPath)) {
                    String parentCollectionField = segments[segments.length - 2];
                    CollectionInfo parentInfo = meta.getCollectionInfo(parentCollectionField);
                    if (parentInfo != null && parentInfo.isEntityCollection()) {
                        Class<?> grandParent = determineParentEntityClass(rootEntityClass, parentCollectionPath,
                                metadataByPath);
                        return PersistenceRegistry.getMetadataFor(grandParent).get(parentCollectionField).relatedType();
                    }
                }
            }
        }

        throw new IllegalStateException("Cannot determine parent class for: " + collectionPath);
    }

    private static List<ScalarFieldMapping> extractFieldsToSelect(
            String collectionPath,
            List<String> pathsInCollection,
            Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath,
            Map<String, String> entityToDtoFieldMap) {
        List<ScalarFieldMapping> fields = new ArrayList<>();
        int collectionDepth = collectionPath.split("\\.").length;

        for (String path : pathsInCollection) {
            PathResolverUtils.PathResolutionMetadata meta = metadataByPath.get(path);
            List<String> allSegments = meta.allSegments();

            if (allSegments.size() > collectionDepth) {
                String entityFieldPath = String.join(".", allSegments.subList(collectionDepth, allSegments.size()));
                String dtoFieldPath = entityToDtoFieldMap.getOrDefault(path, entityFieldPath);

                // Extract just the field name from the full path for nested fields
                String dtoFieldName = dtoFieldPath.contains(".")
                        ? dtoFieldPath.substring(dtoFieldPath.lastIndexOf('.') + 1)
                        : dtoFieldPath;

                fields.add(new ScalarFieldMapping(entityFieldPath, dtoFieldName));
            }
        }

        return fields;
    }

    // Getters

    public Class<?> getRootEntityClass() {
        return rootEntityClass;
    }

    public List<ScalarFieldMapping> getRootScalarFields() {
        return rootScalarFields;
    }

    public List<String> getRootIdField() {
        return rootIdFields;
    }

    public List<CollectionLevelPlan> getCollectionLevels() {
        return collectionLevels;
    }

    public PathResolverUtils.PathResolutionMetadata getMetadata(String path) {
        return metadataByPath.get(path);
    }

    /**
     * Mapping between entity field name (for queries) and DTO field name (for
     * results).
     * If dtoField is null, the field is internal (e.g., ID) and should not appear
     * in DTO.
     */
    public record ScalarFieldMapping(String entityField, String dtoField) {
        public ScalarFieldMapping {
            Objects.requireNonNull(entityField);
            Objects.requireNonNull(dtoField);
        }

        static ScalarFieldMapping internal(String entityField) {
            return new ScalarFieldMapping(entityField, PREFIX_FOR_INTERNAL_USAGE + entityField);
        }

        public boolean isInternal() {
            return dtoField.startsWith(PREFIX_FOR_INTERNAL_USAGE);
        }
    }

    /**
     * Plan for a single collection depth level.
     */
    public record CollectionLevelPlan(int depth, Set<CollectionNode> collections) {
    }

    /**
     * Node representing a single collection with DTO field names for result tree.
     */
    public record CollectionNode(
            String collectionPath,
            String collectionName, // DTO name for insertion in result tree
            String parentReferenceField,
            List<String> idFields,
            Class<?> elementClass,
            List<ScalarFieldMapping> fieldsToSelect,
            Integer limitPerParent,
            Integer offsetPerParent,
            List<SortBy> sortFields,
            boolean isLeaf) {
    }

    /**
     * Record representing the result of a projection transformation:
     * <ul>
     * <li>{@code entityFields} - set of entity field paths selected for query</li>
     * <li>{@code computedFields} - set of computed DTO field paths</li>
     * <li>{@code collectionPagination} - mapping of collection paths to their
     * pagination options</li>
     * <li>{@code entityToDtoMapping} - map from each entity path to the
     * corresponding DTO field name</li>
     * </ul>
     *
     * @param entityFields         set of entity field paths for projection
     * @param collectionPagination map of entity field path to pagination options
     * @param entityToDtoMapping   entity field path to DTO field name mapping
     */
    public record ProjectionSpec(
            Set<String> entityFields,
            Set<String> computedFields,
            Map<String, Pagination> collectionPagination,
            Map<String, String> entityToDtoMapping) {
    }
}
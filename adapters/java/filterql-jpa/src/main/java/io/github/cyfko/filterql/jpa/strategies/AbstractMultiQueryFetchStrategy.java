package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.model.SortBy;
import io.github.cyfko.filterql.core.projection.ProjectionFieldParser;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.projection.FieldSchema;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import io.github.cyfko.filterql.jpa.projection.RowBuffer;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import io.github.cyfko.projection.metamodel.model.PersistenceMetadata;
import io.github.cyfko.projection.metamodel.model.projection.ComputedField;
import io.github.cyfko.projection.metamodel.model.projection.DirectMapping;
import io.github.cyfko.projection.metamodel.model.projection.ProjectionMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Abstract base class for multi-query JPA projection strategies using Template
 * Method Pattern.
 * <p>
 * Defines a clear 5-step execution pipeline:
 * <ol>
 * <li>{@link #step1_BuildExecutionContext} - Prepare query, root, execution
 * plan</li>
 * <li>{@link #step2_ExecuteRootQuery} - Execute the main entity query</li>
 * <li>{@link #step3_ExecuteCollectionQueries} - Load nested collections</li>
 * <li>{@link #step4_transform} - Calculate @Computed fields and convert to
 * Convert to List&lt;Map&gt;</li>
 * </ol>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public abstract class AbstractMultiQueryFetchStrategy implements ExecutionStrategy<List<RowBuffer>> {

    protected static final Logger logger = Logger.getLogger(AbstractMultiQueryFetchStrategy.class.getName());

    protected static final String PREFIX_FOR_INTERNAL_USAGE = "_i_";
    protected static final String PREFIX_FOR_COMPUTED = "_c_";
    protected static final String SUFFIX_PARENT_ID = "pid_";

    protected final Class<?> dtoClass;
    protected final Class<?> rootEntityClass;
    protected final InstanceResolver instanceResolver;

    protected AbstractMultiQueryFetchStrategy(Class<?> dtoClass, InstanceResolver instanceResolver) {
        this.dtoClass = Objects.requireNonNull(dtoClass, "dtoClass cannot be null");
        this.instanceResolver = instanceResolver;

        var projMeta = ProjectionRegistry.getMetadataFor(dtoClass);
        if (projMeta == null) {
            throw new IllegalArgumentException("No projection metadata found for: " + dtoClass.getName());
        }
        this.rootEntityClass = projMeta.entityClass();
        if (this.rootEntityClass == null) {
            throw new IllegalArgumentException("No entity class for projection: " + dtoClass.getName());
        }
    }

    // ==================== Template Method ====================

    /**
     * Template method defining the execution pipeline.
     * This method is final to ensure the execution order is preserved.
     */
    @Override
    public final <Context> List<RowBuffer> execute(Context ctx, PredicateResolver<?> pr,
            QueryExecutionParams params) {

        EntityManager em = (EntityManager) ctx;
        long startTime = System.nanoTime();

        // Step 1: Build execution context
        ExecutionContext exeCtx = step1_BuildExecutionContext(em, pr, params, dtoClass);
        logStep("step1_BuildExecutionContext", startTime);

        // Step 2: Execute root query
        long t2 = System.nanoTime();
        Map<Object, RowBuffer> rootResults = step2_ExecuteRootQuery(exeCtx, params);
        logStep("step2_ExecuteRootQuery", t2);

        if (rootResults.isEmpty()) {
            logger.fine("Root query returned no results");
            return List.of();
        }

        // Step 3: Execute collection queries
        long t3 = System.nanoTime();
        step3_ExecuteCollectionQueries(exeCtx, rootResults);
        logStep("step3_ExecuteCollectionQueries", t3);

        // Step 4: Apply computed fields
        long t4 = System.nanoTime();
        List<RowBuffer> output = step4_transform(exeCtx, rootResults);
        logStep("step4_step4_transform", t4);

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        logger.info(() -> String.format("Multi-query completed in %dms: %d results", durationMs, output.size()));

        return output;
    }

    // ==================== Abstract Template Steps ====================

    /**
     * Step 1: Build the execution context with query, root, and plan.
     */
    protected abstract ExecutionContext step1_BuildExecutionContext(EntityManager em,
            PredicateResolver<?> pr,
            QueryExecutionParams params,
            Class<?> dtoClass);

    /**
     * Step 2: Execute the root entity query.
     */
    protected abstract Map<Object, RowBuffer> step2_ExecuteRootQuery(ExecutionContext ctx, QueryExecutionParams params);

    /**
     * Step 3: Execute queries for nested collections.
     */
    protected abstract void step3_ExecuteCollectionQueries(ExecutionContext ctx, Map<Object, RowBuffer> rootResults);

    /**
     * Step 4: Apply computed field calculations to rows and build the final output
     * as List of Maps.
     */
    protected abstract List<RowBuffer> step4_transform(ExecutionContext ctx, Map<Object, RowBuffer> rootResults);

    // ==================== Utility Methods ====================

    protected void logStep(String stepName, long startNanos) {
        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logger.fine(() -> String.format("  %s: %dms", stepName, durationMs));
        }
    }

    // ==================== Execution Context ====================

    /**
     * Execution context holding query state and plan.
     * Uses public fields for easy access in subclasses.
     */
    public record ExecutionContext(
            EntityManager em,
            CriteriaBuilder cb,
            Root<?> root,
            CriteriaQuery<Tuple> query,
            PredicateResolver<?> predicateResolver,
            QueryPlan plan,
            Map<String, Pagination> collectionPagination) {
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
    public static final class MultiQueryExecutionPlan {

        private final Class<?> rootEntityClass;
        private final FieldSchema rootSchema;
        private final List<String> rootIdFields;
        private final CollectionPlan[] collectionPlans;
        private final ComputedFieldInfo[] computedFields;

        private MultiQueryExecutionPlan(
                Class<?> rootEntityClass,
                FieldSchema rootSchema,
                List<String> rootIdFields,
                CollectionPlan[] collectionPlans,
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
        public static <E> MultiQueryExecutionPlan build(Root<E> root, ProjectionSpec ps, Class<?> dtoClass) {
            @SuppressWarnings("unchecked")
            Class<E> rootEntityClass = (Class<E>) root.getJavaType();

            // 0. Expand compact notation
            Set<String> expandedProjection = expandCompactNotation(ps.entityFields());

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
                schemaBuilder.addField(PREFIX_FOR_COMPUTED + computedDtoField, computedDtoField, false);
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
                Map<String, List<CollectionPathInfo>> byCollection = groupByCollectionPath(depthEntry.getValue(),
                        depth);
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
            List<CollectionPlan> collectionPlanList = new ArrayList<>();

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

                    CollectionPlan plan = buildCollectionPlan(
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

            return new MultiQueryExecutionPlan(
                    rootEntityClass,
                    rootSchema,
                    rootIdFields,
                    collectionPlanList.toArray(new CollectionPlan[0]),
                    computedFieldsArray);
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
            return expandCompactNotation(projection.toArray(new String[0]));
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

        private static CollectionPlan buildCollectionPlan(
                Class<?> rootEntityClass,
                String collectionPath,
                List<CollectionPathInfo> pathsInCollection,
                int depth,
                Map<String, PathResolverUtils.PathResolutionMetadata> metadataByPath,
                Map<String, Pagination> optionsByCollection,
                Map<String, String> entityToDtoFieldMap,
                Set<String> subCollectionNames) {
            CollectionPathInfo sampleInfo = pathsInCollection.getFirst();
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
                        idx = childSchema.indexOfDto(sortField).index();
                    }
                    sortFieldIndices[i] = Math.max(idx, 0);
                    sortDescending[i] = "desc".equalsIgnoreCase(sorts.get(i).direction());
                }
            } else {
                sortFieldIndices = new int[0];
                sortDescending = new boolean[0];
            }

            // Map collection to DTO name
            String dtoCollectionName = entityToDtoFieldMap.getOrDefault(collectionPath, collectionFieldName);

            return new CollectionPlan(
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
                    ComputedField.ReducerMapping[] reducers = new ComputedField.ReducerMapping[0];
                    Set<Integer> reducerDependencyIndices = new HashSet<>();
                    if (metadata != null) {
                        var computedField = metadata.getComputedField(dtoField, false);
                        if (computedField.isPresent() && computedField.get().hasReducers()) {
                            reducers = computedField.get().reducers();
                            for (ComputedField.ReducerMapping rm : reducers) {
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
                    int outputSlot = rootSchema.indexOfDto(dtoField).index();

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

        public CollectionPlan[] collectionPlans() {
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
        public record CollectionPlan(
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
                ComputedField.ReducerMapping[] reducers // Reducers for aggregate fields
        ) {
            /**
             * @return true if this computed field has aggregate reducers
             */
            public boolean isAggregate() {
                return reducers != null && reducers.length > 0;
            }
        }
    }

    public static class QueryPlan {
        private final Class<?> entityClass;
        private final FieldSchema schema;
        private final List<String> idFields;
        private Map<String, QueryPlan>[] collectionPlans;

        private QueryPlan(Class<?> entityClass, FieldSchema schema, Map<String, QueryPlan>[] collectionPlans) {
            this.entityClass = entityClass;
            this.schema = schema;
            this.idFields = PersistenceRegistry.getIdFields(entityClass);
            this.collectionPlans = collectionPlans;
        }

        public static Builder builder(Class<?> projectionClass) {
            return new Builder(projectionClass);
        }

        public FieldSchema getSchema() {
            return schema;
        }

        public Class<?> getEntityClass() {
            return entityClass;
        }

        public List<String> getIdFields() {
            return idFields;
        }

        public Map<String, QueryPlan>[] getCollectionPlans() {
            return collectionPlans;
        }

        public static class Builder {
            private final Class<?> projectionClass;
            private final FieldSchema.Builder schemaBuilder;
            private Map<Integer, Map<String, QueryPlan.Builder>> collectionPlans = new TreeMap<>();

            // Parent context for collection plans
            private final Class<?> parentEntityClass;
            private final String parentReferenceField;

            public Builder(Class<?> projectionClass) {
                this(projectionClass, null, null);
            }

            /**
             * Constructor for collection plan builders with parent context.
             */
            public Builder(Class<?> projectionClass, Class<?> parentEntityClass, String parentReferenceField) {
                this.projectionClass = projectionClass;
                this.schemaBuilder = FieldSchema.builder();
                this.parentEntityClass = parentEntityClass;
                this.parentReferenceField = parentReferenceField;
            }

            public QueryPlan build() {
                Class<?> entityClass = ProjectionRegistry.getMetadataFor(projectionClass).entityClass();

                // Add ID fields for linking
                List<String> idFields = PersistenceRegistry.getIdFields(entityClass);
                for (String idField : idFields) {
                    schemaBuilder.addField(idField, idField, true);
                }

                // Add parent ID fields for collection plans (enables parent-child linking)
                if (parentEntityClass != null && parentReferenceField != null) {
                    List<String> parentIdFields = PersistenceRegistry.getIdFields(parentEntityClass);
                    for (int i = 0; i < parentIdFields.size(); i++) {
                        schemaBuilder.addField(
                                parentReferenceField + "." + parentIdFields.get(i),
                                PREFIX_FOR_INTERNAL_USAGE + SUFFIX_PARENT_ID + i,
                                true);
                    }
                }

                // Add sub-collection plans
                @SuppressWarnings("unchecked")
                Map<String, QueryPlan>[] plans = collectionPlans.values().stream()
                        // 1. On transforme chaque Map<String, Builder> en Map<String, QueryPlan>
                        .map(builderMap -> builderMap.entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey, // La clé String ne change pas
                                        e -> e.getValue().build() // On transforme le Builder en QueryPlan
                                )))
                        // 2. On rassemble le tout dans un tableau
                        .toArray(Map[]::new);

                return new QueryPlan(entityClass, schemaBuilder.build(), plans);
            }

            public Builder add(ProjectionFieldParser.ProjectionField pf) {
                String[] segments = pf.prefix().split("\\.");
                add(projectionClass, pf, segments, 0);
                return this;
            }

            private void add(Class<?> dtoClass, ProjectionFieldParser.ProjectionField pf, String[] segments, int index) {
                ProjectionMetadata pm = ProjectionRegistry.getMetadataFor(dtoClass);

                if (pf.prefix().isEmpty() || index >= segments.length) { // Add scalar fields
                    for (String field : pf.fields()) {
                        pm.getComputedField(field, true).ifPresentOrElse(
                                computedField -> {
                                    var dtoPath = pf.prefix().isEmpty() ? field : String.join(".", pf.prefix(), field);
                                    schemaBuilder.addComputedField(computedField, dtoPath);
                                },
                                () -> {
                                    String entityPath = ProjectionRegistry.toEntityPath(field, projectionClass, true);
                                    schemaBuilder.addField(entityPath, field, false);
                                });
                    }

                    return;
                }

                // Check if the prefix is scalar
                DirectMapping dm = pm.getDirectMapping(segments[index], true)
                        .orElseThrow(() -> new IllegalArgumentException(String.format(
                                "Invalid segment. Segment: %s, Path: %s",
                                segments[index],
                                pf.prefix()))
                        );

                // If collection, add to collections plan and return; Otherwise continue
                if (dm.collection().isPresent()) {
                    String collectionPath = toPathName(segments, index);

                    // Create or find the plan for this collection
                    Builder builder;
                    if (collectionPlans.containsKey(index) && collectionPlans.get(index).containsKey(collectionPath)) {
                        builder = collectionPlans.get(index).get(collectionPath);
                    } else {
                        // Determine parent context for the collection
                        Class<?> currentEntityClass = ProjectionRegistry.getMetadataFor(dtoClass).entityClass();
                        Class<?> collectionElementProjection = dm.dtoFieldType();
                        Class<?> collectionEntityClass = ProjectionRegistry.getMetadataFor(collectionElementProjection)
                                .entityClass();

                        String parentRefField = resolveParentReferenceField(
                                currentEntityClass,
                                collectionEntityClass,
                                segments[index]);

                        builder = new Builder(collectionElementProjection, currentEntityClass, parentRefField);
                    }

                    // Insert this builder as a new collection level plan
                    builder.add(dm.dtoFieldType(), pf, segments, index + 1); // TODO: on peut également projeter toute
                                                                             // une collection sans forcement avoir un
                                                                             // champ imbriqué
                    collectionPlans.computeIfAbsent(index, k -> new HashMap<>())
                            .put(collectionPath, builder);

                    // Add collection slot within the parent
                    schemaBuilder.addCollection(segments[index]);
                } else {
                    add(dm.dtoFieldType(), pf, segments, index + 1);
                }

            }

            private static String toPathName(String[] segments, int index) {
                StringBuilder collectionPathBuilder = new StringBuilder(segments[0]);
                for (int i = 1; i < index; i++) {
                    collectionPathBuilder.append(".").append(segments[i]);
                }
                return collectionPathBuilder.toString();
            }

            /**
             * Resolves the parent reference field in the collection element entity.
             * Uses mappedBy if available, otherwise searches for a field referencing the
             * parent.
             */
            private static String resolveParentReferenceField(
                    Class<?> parentEntityClass,
                    Class<?> elementEntityClass,
                    String collectionFieldName) {

                // Try via parent entity's persistence metadata (mappedBy)
                Map<String, PersistenceMetadata> parentMeta = PersistenceRegistry.getMetadataFor(parentEntityClass);
                if (parentMeta != null) {
                    PersistenceMetadata collMeta = parentMeta.get(collectionFieldName);
                    if (collMeta != null) {
                        Optional<String> mappedBy = collMeta.getMappedBy();
                        if (mappedBy.isPresent()) {
                            return mappedBy.get();
                        }
                    }
                }

                // Try via element entity metadata (find a reference to parent)
                Map<String, PersistenceMetadata> elementMeta = PersistenceRegistry.getMetadataFor(elementEntityClass);
                if (elementMeta != null) {
                    for (Map.Entry<String, PersistenceMetadata> entry : elementMeta.entrySet()) {
                        Class<?> relatedType = entry.getValue().relatedType();
                        if (relatedType != null && relatedType.equals(parentEntityClass)) {
                            return entry.getKey();
                        }
                    }
                }

                // Fallback: naming convention
                String simpleName = parentEntityClass.getSimpleName();
                return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
            }
        }

    }

    /**
     * Pre-computed plan for a single collection with integrated schema.
     */
    public record CollectionPlan(String collectionPath, Class<?> entityClass, FieldSchema schema) { }
}

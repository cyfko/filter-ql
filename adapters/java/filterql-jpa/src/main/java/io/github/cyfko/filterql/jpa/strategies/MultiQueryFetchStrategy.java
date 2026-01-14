package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.config.ProjectionPolicy;
import io.github.cyfko.filterql.core.exception.ProjectionDefinitionException;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.SortBy;
import io.github.cyfko.filterql.core.projection.ProjectionFieldParser;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.projection.AggregateQueryExecutor;
import io.github.cyfko.filterql.jpa.projection.FieldSchema;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import io.github.cyfko.filterql.jpa.projection.MultiQueryExecutionPlan;
import io.github.cyfko.filterql.jpa.projection.MultiQueryExecutionPlanV2;
import io.github.cyfko.filterql.jpa.projection.MultiQueryExecutionPlanV2.CollectionPlanV2;
import io.github.cyfko.filterql.jpa.projection.MultiQueryExecutionPlanV2.ComputedFieldInfo;
import io.github.cyfko.filterql.jpa.projection.RowBuffer;
import io.github.cyfko.projection.metamodel.model.projection.ComputedField.ReducerMapping;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.filterql.jpa.utils.ProjectionUtils;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import io.github.cyfko.projection.metamodel.model.projection.ProjectionMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Optimized multi-query JPA projection strategy (V2) with indexed row access.
 * <p>
 * Key improvements over V1 {@link MultiQueryFetchStrategy}:
 * <ul>
 * <li>Uses {@link RowBuffer} instead of {@code Map<String, Object>} for row
 * storage</li>
 * <li>Uses {@link FieldSchema} for O(1) field access instead of hash
 * lookups</li>
 * <li>Pre-computed nested paths (no runtime string splitting)</li>
 * <li>Single-pass computed field handling with pre-resolved dependencies</li>
 * <li>~6x memory reduction for large result sets</li>
 * </ul>
 * </p>
 *
 * <h2>Memory Comparison</h2>
 * <table>
 * <tr>
 * <th>Metric</th>
 * <th>V1</th>
 * <th>V2</th>
 * </tr>
 * <tr>
 * <td>Per-row overhead</td>
 * <td>~100+ bytes (LinkedHashMap)</td>
 * <td>~24 bytes (Object[])</td>
 * </tr>
 * <tr>
 * <td>1000 rows × 10 fields</td>
 * <td>~800KB</td>
 * <td>~120KB</td>
 * </tr>
 * </table>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see MultiQueryFetchStrategy
 */
public class MultiQueryFetchStrategy implements ExecutionStrategy<List<Map<String, Object>>> {

    private static final Logger logger = Logger.getLogger(MultiQueryFetchStrategy.class.getName());
    private static final int BATCH_SIZE = 1000;

    private final Class<?> rootEntityClass;
    private final Class<?> dtoClass;
    private final InstanceResolver instanceResolver;

    /**
     * Initializes the V2 strategy with the given projection class.
     *
     * @param projectionClass class annotated with @Projection describing the
     *                        structure
     * @throws IllegalStateException if no projection metadata is available
     */
    public MultiQueryFetchStrategy(Class<?> projectionClass) {
        this(projectionClass, null);
    }

    /**
     * Initializes the V2 strategy with projection class and instance resolver for
     * computed fields.
     *
     * @param projectionClass  class annotated with @Projection
     * @param instanceResolver resolver for computed field providers, or null if
     *                         none needed
     */
    public MultiQueryFetchStrategy(Class<?> projectionClass, InstanceResolver instanceResolver) {
        Objects.requireNonNull(projectionClass, "Projection class is required");
        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(projectionClass);

        if (metadata == null) {
            throw new IllegalStateException("Not a projection or entity: " + projectionClass.getName());
        }

        if (metadata.computedFields().length > 0 && instanceResolver == null) {
            throw new IllegalStateException("Projection <" + projectionClass.getSimpleName() +
                    "> has computed fields but no InstanceResolver provided");
        }

        this.dtoClass = projectionClass;
        this.rootEntityClass = metadata.entityClass();
        this.instanceResolver = instanceResolver;
    }

    @Override
    public <Context> List<Map<String, Object>> execute(Context ctx, PredicateResolver<?> pr,
            QueryExecutionParams params) {
        EntityManager em = (EntityManager) ctx;
        long startTime = System.nanoTime();

        // 1. Build optimized execution plan
        ExecutionContext execCtx = buildExecutionContext(em, pr, params);

        logger.fine(() -> String.format("V2 Plan: %d root fields, %d collections, %d computed",
                execCtx.plan.rootSchema().fieldCount(),
                execCtx.plan.collectionPlans().length,
                execCtx.plan.computedFields().length));

        // 2. Execute root query with RowBuffer
        Map<Object, RowBuffer> rootResults = executeRootQuery(execCtx, params);

        if (rootResults.isEmpty()) {
            logger.fine("Root query returned no results");
            return List.of();
        }

        logger.fine(() -> "Root query returned " + rootResults.size() + " entities");

        // 3. Execute collection queries level-by-level
        if (execCtx.plan.hasCollections()) {
            executeCollectionQueries(execCtx, rootResults);
        }

        // 4. Pre-fetch aggregate values for computed fields with reducers
        ComputedFieldInfo[] computedFields = execCtx.plan.computedFields();
        boolean hasComputed = computedFields.length > 0 && dtoClass != rootEntityClass;

        // Check if any computed field has aggregates (early exit optimization)
        boolean hasAggregates = false;
        if (hasComputed) {
            for (ComputedFieldInfo cf : computedFields) {
                if (cf.isAggregate()) {
                    hasAggregates = true;
                    break;
                }
            }
        }

        Map<String, Map<Object, Number>> aggregateResults = Collections.emptyMap();
        if (hasAggregates) {
            aggregateResults = prefetchAggregates(execCtx, computedFields, rootResults.keySet());
        }

        // Compute excluded slots once (dependency fields not directly projected)
        Set<Integer> excludedSlots = hasComputed
                ? computeExcludedSlots(computedFields, execCtx)
                : null;

        // 5. Single pass: apply computed fields + convert to Map
        List<Map<String, Object>> results = new ArrayList<>(rootResults.size());

        if (hasAggregates) {
            // Need rootId for aggregate lookups
            final Map<String, Map<Object, Number>> finalAggregateResults = aggregateResults;
            for (Map.Entry<Object, RowBuffer> entry : rootResults.entrySet()) {
                Object rootId = entry.getKey();
                RowBuffer row = entry.getValue();
                applyComputedFieldsToRow(row, computedFields, execCtx, rootId, finalAggregateResults);
                results.add(row.toMap(excludedSlots));
            }
        } else {
            // Fast path: no aggregates, just process rows
            for (RowBuffer row : rootResults.values()) {
                if (hasComputed) {
                    applyComputedFieldsToRow(row, computedFields, execCtx, null, Collections.emptyMap());
                }
                results.add(row.toMap(excludedSlots));
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        logger.info(() -> String.format("V2 Multi-query completed in %dms: %d roots", durationMs, results.size()));

        return results;
    }

    // ==================== Execution Context ====================

    private ExecutionContext buildExecutionContext(EntityManager em, PredicateResolver<?> pr,
            QueryExecutionParams params) {
        boolean ignoreCase = params.projectionPolicy().fieldCase() == ProjectionPolicy.FieldCase.CASE_INSENSITIVE;
        MultiQueryExecutionPlan.ProjectionSpec projSpec = transformProjection(params.projection(), ignoreCase);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> root = query.from(rootEntityClass);

        MultiQueryExecutionPlanV2 plan = MultiQueryExecutionPlanV2.build(root, projSpec, dtoClass);

        return new ExecutionContext(em, cb, root, query, pr, projSpec, plan);
    }

    private record ExecutionContext(
            EntityManager em,
            CriteriaBuilder cb,
            Root<?> root,
            CriteriaQuery<Tuple> query,
            PredicateResolver<?> predicateResolver,
            MultiQueryExecutionPlan.ProjectionSpec projectionSpec,
            MultiQueryExecutionPlanV2 plan) {
    }

    // ==================== Projection Transformation ====================

    private MultiQueryExecutionPlan.ProjectionSpec transformProjection(Set<String> dtoProjection, boolean ignoreCase) {
        Set<String> entityProjection = new HashSet<>();
        Set<String> computedFields = new HashSet<>();
        Map<String, Pagination> entityCollectionOptions = new HashMap<>();
        Map<String, String> entityToDtoFieldMap = new HashMap<>();
        Map<String, Pagination> dtoCollectionOptions = ProjectionFieldParser.parseCollectionOptions(dtoProjection);

        if (dtoProjection == null || dtoProjection.isEmpty()) {
            dtoProjection = new HashSet<>();
            ProjectionMetadata meta = ProjectionRegistry.getMetadataFor(dtoClass);
            for (var dm : meta.directMappings()) {
                if (dm.collection().isPresent() || dm.isNested()
                        || ProjectionRegistry.getMetadataFor(dm.dtoFieldType()) != null)
                    continue;
                dtoProjection.add(dm.dtoField());
            }
            for (var cf : meta.computedFields()) {
                dtoProjection.add(cf.dtoField());
            }
        }

        for (String dtoField : dtoProjection) {
            Set<String> expanded = MultiQueryExecutionPlan.expandCompactNotation(dtoField.replaceAll("\\[.*?]", ""));

            for (String baseDtoField : expanded) {
                String entityPath;
                try {
                    entityPath = ProjectionRegistry.toEntityPath(baseDtoField, dtoClass, ignoreCase);
                } catch (IllegalArgumentException e) {
                    throw new ProjectionDefinitionException(baseDtoField + " does not resolve to a valid path.", e);
                }
                entityProjection.add(entityPath);
                entityToDtoFieldMap.put(entityPath, baseDtoField);

                if (ProjectionRegistry.getMetadataFor(dtoClass).isComputedField(baseDtoField, ignoreCase)) {
                    computedFields.add(baseDtoField);
                }

                if (dtoCollectionOptions.containsKey(baseDtoField)) {
                    entityCollectionOptions.put(entityPath, dtoCollectionOptions.get(baseDtoField));
                }
            }
        }

        return new MultiQueryExecutionPlan.ProjectionSpec(
                entityProjection, computedFields, entityCollectionOptions, entityToDtoFieldMap);
    }

    // ==================== Root Query Execution ====================

    private Map<Object, RowBuffer> executeRootQuery(ExecutionContext ctx, QueryExecutionParams params) {
        FieldSchema schema = ctx.plan.rootSchema();
        List<Selection<?>> selections = new ArrayList<>(schema.fieldCount());

        // Build selections using schema (skip computed output slots)
        for (int i = 0; i < schema.fieldCount(); i++) {
            String entityField = schema.entityField(i);
            // Skip computed output placeholder slots - they're not in the entity
            if (entityField.startsWith("_computed_")) {
                continue;
            }
            Path<?> path = PathResolverUtils.resolvePath(ctx.root, entityField);
            selections.add(path.alias(schema.dtoField(i)));
        }

        ctx.query.multiselect(selections);

        // Apply filter predicate
        @SuppressWarnings({ "rawtypes", "unchecked" })
        Predicate filterPredicate = ctx.predicateResolver.resolve((Root) ctx.root, ctx.query, ctx.cb);
        if (filterPredicate != null) {
            ctx.query.where(filterPredicate);
        }

        // Apply sorting
        if (params.hasPagination() && params.pagination().hasSort()) {
            List<Order> orders = new ArrayList<>();
            for (SortBy sortField : params.pagination().sort()) {
                Path<?> path = PathResolverUtils.resolvePath(ctx.root, sortField.field());
                orders.add("desc".equalsIgnoreCase(sortField.direction()) ? ctx.cb.desc(path) : ctx.cb.asc(path));
            }
            ctx.query.orderBy(orders);
        }

        // Apply pagination
        int offset = params.hasPagination() ? params.pagination().offset() : 0;
        int limit = params.hasPagination() ? params.pagination().size() : 50;

        List<Tuple> tuples = ctx.em.createQuery(ctx.query)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();

        // Convert to RowBuffer map keyed by ID
        Map<Object, RowBuffer> results = new LinkedHashMap<>();
        List<String> idFields = ctx.plan.rootIdFields();

        for (Tuple tuple : tuples) {
            RowBuffer row = tupleToRowBuffer(tuple, schema);
            Object rootId = extractCompositeKey(row, idFields, schema);
            results.put(rootId, row);

            // Initialize collection slots
            for (int c = 0; c < schema.collectionCount(); c++) {
                row.initCollection(c);
            }
        }

        return results;
    }

    private RowBuffer tupleToRowBuffer(Tuple tuple, FieldSchema schema) {
        RowBuffer buffer = new RowBuffer(schema);

        for (int i = 0; i < schema.fieldCount(); i++) {
            try {
                Object value = tuple.get(schema.dtoField(i));
                buffer.set(i, value);
            } catch (IllegalArgumentException e) {
                // Field may not be in tuple (internal field added for ID)
                try {
                    Object value = tuple.get(schema.entityField(i));
                    buffer.set(i, value);
                } catch (IllegalArgumentException ignored) {
                    // Skip if not found
                }
            }
        }

        return buffer;
    }

    // ==================== Collection Query Execution ====================

    private void executeCollectionQueries(ExecutionContext ctx, Map<Object, RowBuffer> rootResults) {
        // Group collection plans by depth
        Map<Integer, List<CollectionPlanV2>> plansByDepth = new TreeMap<>();
        for (CollectionPlanV2 cplan : ctx.plan.collectionPlans()) {
            plansByDepth.computeIfAbsent(cplan.depth(), k -> new ArrayList<>()).add(cplan);
        }

        // Track parent lookup by collection path
        Map<String, Map<Object, RowBuffer>> parentIndexByPath = new HashMap<>();
        parentIndexByPath.put("", rootResults);

        // Track which schema applies to each collection path
        Map<String, FieldSchema> schemaByPath = new HashMap<>();
        schemaByPath.put("", ctx.plan.rootSchema());

        Set<Object> currentLevelParentIds = rootResults.keySet();

        for (Map.Entry<Integer, List<CollectionPlanV2>> depthEntry : plansByDepth.entrySet()) {
            List<CollectionPlanV2> plansAtDepth = depthEntry.getValue();

            Set<Object> nextLevelIds = new HashSet<>();

            for (CollectionPlanV2 cplan : plansAtDepth) {
                CollectionQueryResult result = executeCollectionQuery(ctx, cplan, currentLevelParentIds);

                // Attach children to parents
                String parentPath = getParentPath(cplan.collectionPath());
                Map<Object, RowBuffer> parentIndex = parentIndexByPath.get(parentPath);
                FieldSchema parentSchema = schemaByPath.get(parentPath);

                if (parentIndex != null && parentSchema != null) {
                    // Get the collection name relative to parent
                    String collName = cplan.collectionPath().contains(".")
                            ? cplan.collectionPath().substring(cplan.collectionPath().lastIndexOf('.') + 1)
                            : cplan.collectionPath();

                    int collectionSlotIndex = findCollectionSlotIndex(parentSchema, collName);

                    // If not found by short name, try the DTO collection name
                    if (collectionSlotIndex < 0) {
                        collectionSlotIndex = findCollectionSlotIndex(parentSchema, cplan.dtoCollectionName());
                    }

                    for (Map.Entry<Object, List<RowBuffer>> entry : result.childrenByParent.entrySet()) {
                        RowBuffer parent = parentIndex.get(entry.getKey());
                        if (parent != null && collectionSlotIndex >= 0) {
                            parent.setCollection(collectionSlotIndex, entry.getValue());
                        }
                    }
                }

                // Build child index for next level
                parentIndexByPath.put(cplan.collectionPath(), result.childIndex);
                schemaByPath.put(cplan.collectionPath(), cplan.childSchema());
                nextLevelIds.addAll(result.childIndex.keySet());
            }

            currentLevelParentIds = nextLevelIds;
            if (currentLevelParentIds.isEmpty())
                break;
        }
    }

    private record CollectionQueryResult(
            Map<Object, List<RowBuffer>> childrenByParent,
            Map<Object, RowBuffer> childIndex) {
    }

    private CollectionQueryResult executeCollectionQuery(
            ExecutionContext ctx,
            CollectionPlanV2 cplan,
            Set<Object> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return new CollectionQueryResult(Collections.emptyMap(), Collections.emptyMap());
        }

        Map<Object, List<RowBuffer>> resultsByParent = new LinkedHashMap<>();
        Map<Object, RowBuffer> childIndex = new LinkedHashMap<>();
        List<Object> parentIdList = new ArrayList<>(parentIds);

        FieldSchema childSchema = cplan.childSchema();

        for (int batchStart = 0; batchStart < parentIdList.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, parentIdList.size());
            List<Object> batchIds = parentIdList.subList(batchStart, batchEnd);

            CriteriaQuery<Tuple> query = ctx.cb.createTupleQuery();
            Root<?> collRoot = query.from(cplan.elementClass());

            List<Selection<?>> selections = new ArrayList<>();

            // Parent ID fields
            Path<?> parentRefPath = collRoot.get(cplan.parentReferenceField());
            List<String> parentIdFields = PersistenceRegistry.getIdFields(parentRefPath.getJavaType());

            for (int i = 0; i < parentIdFields.size(); i++) {
                Path<?> parentIdPath = parentRefPath.get(parentIdFields.get(i));
                selections.add(
                        parentIdPath.alias(MultiQueryExecutionPlanV2.PREFIX_FOR_INTERNAL_USAGE + "parent_id_" + i));
            }

            // Child fields using schema
            for (int i = 0; i < childSchema.fieldCount(); i++) {
                Path<?> path = PathResolverUtils.resolvePath(collRoot, childSchema.entityField(i));
                selections.add(path.alias(childSchema.dtoField(i)));
            }

            query.multiselect(selections);

            // Build IN predicate
            Predicate wherePredicate = buildInPredicate(ctx.cb, parentRefPath, parentIdFields, batchIds);
            query.where(wherePredicate);

            // Apply sorting using pre-computed indices
            if (cplan.sortFieldIndices().length > 0) {
                List<Order> orders = new ArrayList<>();
                for (int i = 0; i < cplan.sortFieldIndices().length; i++) {
                    int fieldIdx = cplan.sortFieldIndices()[i];
                    if (fieldIdx >= 0 && fieldIdx < childSchema.fieldCount()) {
                        Path<?> sortPath = PathResolverUtils.resolvePath(collRoot, childSchema.entityField(fieldIdx));
                        orders.add(cplan.sortDescending()[i] ? ctx.cb.desc(sortPath) : ctx.cb.asc(sortPath));
                    }
                }
                if (!orders.isEmpty()) {
                    query.orderBy(orders);
                }
            }

            List<Tuple> tuples = ctx.em.createQuery(query).getResultList();

            // Process results
            Map<Object, List<RowBuffer>> batchResult = new LinkedHashMap<>();

            for (Tuple tuple : tuples) {
                Object parentId = extractParentIdFromTuple(tuple, parentIdFields.size());
                RowBuffer childRow = tupleToRowBuffer(tuple, childSchema);
                Object childId = extractCompositeKey(childRow, cplan.idFields(), childSchema);

                batchResult.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childRow);
                childIndex.put(childId, childRow);
            }

            // Apply per-parent pagination
            if (cplan.offsetPerParent() != null || cplan.limitPerParent() != null) {
                for (Map.Entry<Object, List<RowBuffer>> entry : batchResult.entrySet()) {
                    List<RowBuffer> children = entry.getValue();
                    int offset = cplan.offsetPerParent() != null ? cplan.offsetPerParent() : 0;
                    int limit = cplan.limitPerParent() != null ? cplan.limitPerParent() : children.size();
                    int end = Math.min(children.size(), offset + limit);
                    if (offset > 0 || limit < children.size()) {
                        entry.setValue(children.subList(Math.min(offset, children.size()), end));
                    }
                }
            }

            // Merge batch results
            for (Map.Entry<Object, List<RowBuffer>> entry : batchResult.entrySet()) {
                resultsByParent.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }

        return new CollectionQueryResult(resultsByParent, childIndex);
    }

    // ==================== Computed Fields ====================

    /**
     * Computes the set of slots to exclude from toMap() output.
     * These are dependency fields that are only used for computed field calculation
     * and are not directly projected.
     *
     * @param computedFields array of computed field metadata
     * @param ctx            execution context
     * @return set of slot indices to exclude
     */
    private Set<Integer> computeExcludedSlots(ComputedFieldInfo[] computedFields, ExecutionContext ctx) {
        Set<Integer> excluded = new HashSet<>();
        Map<String, String> entityToDto = ctx.projectionSpec.entityToDtoMapping();

        for (ComputedFieldInfo info : computedFields) {
            for (String depPath : info.dependencyPaths()) {
                String trimmedPath = depPath.trim();
                String dtoField = entityToDto.get(trimmedPath);
                boolean isDirectlyProjected = dtoField != null
                        && !ctx.projectionSpec.computedFields().contains(dtoField);
                if (!isDirectlyProjected) {
                    int depSlot = ctx.plan.rootSchema().indexOfEntity(trimmedPath);
                    if (depSlot >= 0) {
                        excluded.add(depSlot);
                    }
                }
            }
        }

        return excluded;
    }

    /**
     * Applies computed fields to a single row using O(1) slot access.
     * For aggregate reducers, uses pre-fetched values from aggregate queries.
     * 
     * @param row              the RowBuffer to process
     * @param computedFields   array of computed field metadata
     * @param ctx              execution context
     * @param rootId           the root entity ID for looking up aggregates
     * @param aggregateResults pre-fetched aggregate results by (path -> (rootId ->
     *                         value))
     */
    private void applyComputedFieldsToRow(
            RowBuffer row,
            ComputedFieldInfo[] computedFields,
            ExecutionContext ctx,
            Object rootId,
            Map<String, Map<Object, Number>> aggregateResults) {

        for (ComputedFieldInfo info : computedFields) {
            ReducerMapping[] reducers = info.reducers();
            String[] dependencyPaths = info.dependencyPaths();
            int[] dependencySlots = info.dependencySlots();

            // Gather dependencies using O(1) slot access
            Object[] params = new Object[dependencyPaths.length];

            for (int i = 0; i < params.length; i++) {
                String depPath = dependencyPaths[i].trim();

                // Check if this dependency has a reducer (aggregate)
                ReducerMapping reducer = null;
                for (ReducerMapping rm : reducers) {
                    if (rm.dependencyIndex() == i) {
                        reducer = rm;
                        break;
                    }
                }

                if (reducer != null) {
                    // This is an aggregate dependency - use pre-fetched value
                    Map<Object, Number> byId = aggregateResults.get(depPath);
                    params[i] = (byId != null) ? byId.get(rootId) : null;
                } else {
                    // Regular scalar dependency - use slot access
                    int slot = dependencySlots[i];
                    if (slot >= 0) {
                        params[i] = row.get(slot);
                    } else {
                        // Fallback to name lookup if slot not found
                        params[i] = row.getOrNull(depPath);
                    }
                }
            }

            try {
                Object computed = ProjectionUtils.computeField(instanceResolver, dtoClass, info.dtoFieldName(), params);

                // Store computed value using O(1) slot access
                int outputSlot = info.outputSlot();
                if (outputSlot >= 0) {
                    row.set(outputSlot, computed);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute field: " + info.dtoFieldName(), e);
            }
        }
    }

    /**
     * Pre-fetches aggregate values for all computed fields with reducers.
     * Executes batch queries for each aggregate path.
     * 
     * @param exeCtx         Execution context
     * @param computedFields computed field metadata
     * @param rootIds        all root entity IDs
     * @return Map of (path -> (rootId -> aggregateValue))
     */
    private Map<String, Map<Object, Number>> prefetchAggregates(
            ExecutionContext exeCtx,
            ComputedFieldInfo[] computedFields,
            Collection<?> rootIds) {

        Map<String, Map<Object, Number>> results = new HashMap<>();

        if (rootIds.isEmpty()) {
            return results;
        }

        // Get the ID field name from the plan
        List<String> idFields = exeCtx.plan.rootIdFields();
        if (idFields.isEmpty()) {
            logger.warning("No root ID fields found in plan, cannot execute aggregate queries");
            return results;
        }
        String rootIdField = idFields.get(0);

        logger.fine(() -> "Pre-fetching aggregates for " + rootIds.size() + " root IDs using ID field: " + rootIdField);

        AggregateQueryExecutor executor = new AggregateQueryExecutor(exeCtx.em, rootEntityClass, rootIdField);

        for (ComputedFieldInfo info : computedFields) {
            if (!info.isAggregate()) {
                continue;
            }

            logger.fine(() -> "Processing aggregate computed field: " + info.dtoFieldName());

            for (ReducerMapping reducer : info.reducers()) {
                String path = info.dependencyPaths()[reducer.dependencyIndex()].trim();

                logger.fine(() -> "Executing aggregate query: " + reducer.reducer() + " on path: " + path);

                // Skip if already computed
                if (results.containsKey(path)) {
                    continue;
                }

                try {
                    Map<Object, Number> aggregates = executor.executeAggregateQuery(path, reducer.reducer(), rootIds);
                    results.put(path, aggregates);

                    logger.fine(() -> "Aggregate query returned " + aggregates.size() + " results for path: " + path);
                } catch (Exception e) {
                    logger.warning("Failed to execute aggregate query for " + path + ": " + e.getMessage());
                    e.printStackTrace();
                    results.put(path, Collections.emptyMap());
                }
            }
        }

        return results;
    }

    // ==================== Utility Methods ====================

    private Object extractCompositeKey(RowBuffer row, List<String> idFields, FieldSchema schema) {
        if (idFields.size() == 1) {
            int idx = schema.indexOfEntity(idFields.getFirst());
            if (idx < 0) {
                idx = schema.indexOfDto(MultiQueryExecutionPlanV2.PREFIX_FOR_INTERNAL_USAGE + idFields.getFirst());
            }
            return idx >= 0 ? row.get(idx) : null;
        } else {
            List<Object> values = new ArrayList<>(idFields.size());
            for (String idField : idFields) {
                int idx = schema.indexOfEntity(idField);
                if (idx < 0) {
                    idx = schema.indexOfDto(MultiQueryExecutionPlanV2.PREFIX_FOR_INTERNAL_USAGE + idField);
                }
                values.add(idx >= 0 ? row.get(idx) : null);
            }
            return new CompositeKey(values);
        }
    }

    private Object extractParentIdFromTuple(Tuple tuple, int fieldCount) {
        String prefix = MultiQueryExecutionPlanV2.PREFIX_FOR_INTERNAL_USAGE;
        if (fieldCount == 1) {
            return tuple.get(prefix + "parent_id_0");
        } else {
            List<Object> values = new ArrayList<>(fieldCount);
            for (int i = 0; i < fieldCount; i++) {
                values.add(tuple.get(prefix + "parent_id_" + i));
            }
            return new CompositeKey(values);
        }
    }

    private Predicate buildInPredicate(CriteriaBuilder cb, Path<?> parentRefPath, List<String> parentIdFields,
            List<Object> parentIds) {
        if (parentIdFields.size() == 1) {
            Path<?> idPath = parentRefPath.get(parentIdFields.getFirst());
            return idPath.in(parentIds);
        } else {
            List<Predicate> orPredicates = new ArrayList<>();
            for (Object parentId : parentIds) {
                if (parentId instanceof CompositeKey key) {
                    List<Predicate> andPredicates = new ArrayList<>();
                    for (int i = 0; i < parentIdFields.size(); i++) {
                        Path<?> fieldPath = parentRefPath.get(parentIdFields.get(i));
                        andPredicates.add(cb.equal(fieldPath, key.values().get(i)));
                    }
                    orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
                }
            }
            return cb.or(orPredicates.toArray(new Predicate[0]));
        }
    }

    private String getParentPath(String collectionPath) {
        if (!collectionPath.contains(".")) {
            return "";
        }
        return collectionPath.substring(0, collectionPath.lastIndexOf('.'));
    }

    private int findCollectionSlotIndex(FieldSchema schema, String collectionName) {
        for (int c = 0; c < schema.collectionCount(); c++) {
            if (schema.collectionName(c).equals(collectionName)) {
                return c;
            }
        }
        return -1;
    }

    private record CompositeKey(List<Object> values) {
        public CompositeKey {
            values = List.copyOf(values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CompositeKey key))
                return false;
            return Objects.equals(values, key.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(values);
        }
    }
}

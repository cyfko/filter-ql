package io.github.cyfko.filterql.jpa.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.cyfko.filterql.core.config.ProjectionPolicy;
import io.github.cyfko.filterql.core.exception.ProjectionDefinitionException;
import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.model.SortBy;
import io.github.cyfko.filterql.core.projection.ProjectionFieldParser;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.predicate.IdPredicateBuilder;
import io.github.cyfko.filterql.jpa.projection.FieldSchema;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import static io.github.cyfko.filterql.jpa.strategies.AbstractMultiQueryFetchStrategy.MultiQueryExecutionPlan.*;
import io.github.cyfko.filterql.jpa.projection.RowBuffer;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.filterql.jpa.utils.ProjectionUtils;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import io.github.cyfko.projection.metamodel.model.projection.ProjectionMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

public class MultiQueryFetchStrategy extends AbstractMultiQueryFetchStrategy {
    private static final int BATCH_SIZE = 1000;

    /**
     * Initializes the strategy with the given projection class.
     */
    public MultiQueryFetchStrategy(Class<?> dtoClass) {
        this(dtoClass, null);
    }

    /**
     * Initializes the strategy with projection class and instance resolver.
     */
    public MultiQueryFetchStrategy(Class<?> dtoClass, InstanceResolver instanceResolver) {
        super(dtoClass, instanceResolver);
    }

    @Override
    protected ExecutionContext step1_BuildExecutionContext(EntityManager em, PredicateResolver<?> pr,
            QueryExecutionParams params) {
        boolean ignoreCase = params.projectionPolicy().fieldCase() == ProjectionPolicy.FieldCase.CASE_INSENSITIVE;
        ProjectionSpec projSpec = null;

        // Tranform projection
        {
            Set<String> dtoProjection = params.projection();
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
                Set<String> expanded = MultiQueryExecutionPlan
                        .expandCompactNotation(dtoField.replaceAll("\\[.*?]", ""));

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

            projSpec = new ProjectionSpec(entityProjection, computedFields,
                    entityCollectionOptions, entityToDtoFieldMap);
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> root = query.from(rootEntityClass);

        MultiQueryExecutionPlan plan = MultiQueryExecutionPlan.build(root, projSpec, dtoClass);

        return new ExecutionContext(em, cb, root, query, pr, projSpec, plan);
    }

    @Override
    protected Map<Object, RowBuffer> step2_ExecuteRootQuery(ExecutionContext ctx, QueryExecutionParams params) {
        FieldSchema schema = ctx.plan().rootSchema();
        List<Selection<?>> selections = new ArrayList<>(schema.fieldCount());

        // Build selections using schema (skip computed output slots)
        for (int i = 0; i < schema.fieldCount(); i++) {
            String entityField = schema.entityField(i);
            // Skip computed output placeholder slots - they're not in the entity
            if (entityField.startsWith("_computed_")) {
                continue;
            }
            Path<?> path = PathResolverUtils.resolvePath(ctx.root(), entityField);
            selections.add(path.alias(schema.dtoField(i)));
        }

        ctx.query().multiselect(selections);

        // Apply filter predicate
        @SuppressWarnings({ "rawtypes", "unchecked" })
        Predicate filterPredicate = ctx.predicateResolver().resolve((Root) ctx.root(), ctx.query(), ctx.cb());
        if (filterPredicate != null) {
            ctx.query().where(filterPredicate);
        }

        // Apply sorting
        if (params.hasPagination() && params.pagination().hasSort()) {
            List<Order> orders = new ArrayList<>();
            for (SortBy sortField : params.pagination().sort()) {
                Path<?> path = PathResolverUtils.resolvePath(ctx.root(), sortField.field());
                orders.add("desc".equalsIgnoreCase(sortField.direction()) ? ctx.cb().desc(path) : ctx.cb().asc(path));
            }
            ctx.query().orderBy(orders);
        }

        // Apply pagination
        int offset = params.hasPagination() ? params.pagination().offset() : 0;
        int limit = params.hasPagination() ? params.pagination().size() : 50;

        List<Tuple> tuples = ctx.em().createQuery(ctx.query())
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();

        // Convert to RowBuffer map keyed by ID
        Map<Object, RowBuffer> results = new LinkedHashMap<>();
        List<String> idFields = ctx.plan().rootIdFields();

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

    @Override
    protected void step3_ExecuteCollectionQueries(ExecutionContext ctx, Map<Object, RowBuffer> rootResults) {
        // Group collection plans by depth
        Map<Integer, List<CollectionPlan>> plansByDepth = new TreeMap<>();
        for (CollectionPlan cplan : ctx.plan().collectionPlans()) {
            plansByDepth.computeIfAbsent(cplan.depth(), k -> new ArrayList<>()).add(cplan);
        }

        // Track parent lookup by collection path
        Map<String, Map<Object, RowBuffer>> parentIndexByPath = new HashMap<>();
        parentIndexByPath.put("", rootResults);

        // Track which schema applies to each collection path
        Map<String, FieldSchema> schemaByPath = new HashMap<>();
        schemaByPath.put("", ctx.plan().rootSchema());

        Set<Object> currentLevelParentIds = rootResults.keySet();

        for (Map.Entry<Integer, List<CollectionPlan>> depthEntry : plansByDepth.entrySet()) {
            List<CollectionPlan> plansAtDepth = depthEntry.getValue();

            Set<Object> nextLevelIds = new HashSet<>();

            for (CollectionPlan cplan : plansAtDepth) {
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

    @Override
    protected void step4_ApplyComputedFields(ExecutionContext ctx, Map<Object, RowBuffer> rootResults) {
        for (RowBuffer row : rootResults.values()) {
            for (ComputedFieldInfo info : ctx.plan().computedFields()) {
                int[] dependencySlots = info.dependencySlots();
                String[] dependencyPaths = info.dependencyPaths();

                // Gather dependencies using O(1) slot access
                Object[] params = new Object[dependencySlots.length];
                for (int i = 0; i < params.length; i++) {
                    int slot = dependencySlots[i];
                    if (slot >= 0) {
                        params[i] = row.get(slot);
                    } else {
                        // Fallback to name lookup
                        params[i] = row.getOrNull(dependencyPaths[i].trim());
                    }
                }

                try {
                    Object computed = ProjectionUtils.computeField(instanceResolver, dtoClass, info.dtoFieldName(),
                            params);
                    int outputSlot = info.outputSlot();
                    if (outputSlot >= 0) {
                        row.set(outputSlot, computed);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to compute field: " + info.dtoFieldName(), e);
                }

            }
        }
    }

    @Override
    protected List<Map<String, Object>> step5_BuildFinalOutput(ExecutionContext ctx,
            Map<Object, RowBuffer> rootResults) {
        // Compute excluded slots once (dependency fields not directly projected)
        Set<Integer> excludedSlots = computeExcludedSlots(ctx.plan().computedFields(), ctx);

        List<Map<String, Object>> results = new ArrayList<>(rootResults.size());
        for (RowBuffer row : rootResults.values()) {
            results.add(row.toMap(excludedSlots));
        }
        return results;
    }

    // ==================== Utility Methods ====================

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

    private Object extractCompositeKey(RowBuffer row, List<String> idFields, FieldSchema schema) {
        if (idFields.size() == 1) {
            int idx = schema.indexOfEntity(idFields.getFirst());
            if (idx < 0) {
                idx = schema.indexOfDto(MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE + idFields.getFirst());
            }
            return idx >= 0 ? row.get(idx) : null;
        } else {
            List<Object> values = new ArrayList<>(idFields.size());
            for (String idField : idFields) {
                int idx = schema.indexOfEntity(idField);
                if (idx < 0) {
                    idx = schema.indexOfDto(MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE + idField);
                }
                values.add(idx >= 0 ? row.get(idx) : null);
            }
            return List.copyOf(values);
        }
    }

    private record CollectionQueryResult(
            Map<Object, List<RowBuffer>> childrenByParent,
            Map<Object, RowBuffer> childIndex) {
    }

    private CollectionQueryResult executeCollectionQuery(
            ExecutionContext ctx,
            CollectionPlan cplan,
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

            CriteriaQuery<Tuple> query = ctx.cb().createTupleQuery();
            Root<?> collRoot = query.from(cplan.elementClass());

            List<Selection<?>> selections = new ArrayList<>();

            // Parent ID fields
            Path<?> parentRefPath = collRoot.get(cplan.parentReferenceField());
            List<String> parentIdFields = PersistenceRegistry.getIdFields(parentRefPath.getJavaType());

            for (int i = 0; i < parentIdFields.size(); i++) {
                Path<?> parentIdPath = parentRefPath.get(parentIdFields.get(i));
                selections.add(
                        parentIdPath.alias(MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE + "parent_id_" + i));
            }

            // Child fields using schema
            for (int i = 0; i < childSchema.fieldCount(); i++) {
                Path<?> path = PathResolverUtils.resolvePath(collRoot, childSchema.entityField(i));
                selections.add(path.alias(childSchema.dtoField(i)));
            }

            query.multiselect(selections);

            // Build IN predicate
            Predicate wherePredicate = buildInPredicate(ctx.cb(), parentRefPath, parentIdFields, batchIds);
            query.where(wherePredicate);

            // Apply sorting using pre-computed indices
            if (cplan.sortFieldIndices().length > 0) {
                List<Order> orders = new ArrayList<>();
                for (int i = 0; i < cplan.sortFieldIndices().length; i++) {
                    int fieldIdx = cplan.sortFieldIndices()[i];
                    if (fieldIdx >= 0 && fieldIdx < childSchema.fieldCount()) {
                        Path<?> sortPath = PathResolverUtils.resolvePath(collRoot, childSchema.entityField(fieldIdx));
                        orders.add(cplan.sortDescending()[i] ? ctx.cb().desc(sortPath) : ctx.cb().asc(sortPath));
                    }
                }
                if (!orders.isEmpty()) {
                    query.orderBy(orders);
                }
            }

            List<Tuple> tuples = ctx.em().createQuery(query).getResultList();

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

    private Predicate buildInPredicate(CriteriaBuilder cb, Path<?> parentRefPath, List<String> parentIdFields,
            List<Object> parentIds) {
        // Build paths to ID fields
        List<Path<?>> idPaths = new ArrayList<>(parentIdFields.size());
        for (String idField : parentIdFields) {
            idPaths.add(parentRefPath.get(idField));
        }

        // Composite keys are already List<Object>, simple IDs are scalars
        // IdPredicateBuilder handles both cases
        return IdPredicateBuilder.defaultBuilder().buildIdPredicate(cb, idPaths, parentIds);
    }

    private Object extractParentIdFromTuple(Tuple tuple, int fieldCount) {
        String prefix = MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE;
        if (fieldCount == 1) {
            return tuple.get(prefix + "parent_id_0");
        } else {
            List<Object> values = new ArrayList<>(fieldCount);
            for (int i = 0; i < fieldCount; i++) {
                values.add(tuple.get(prefix + "parent_id_" + i));
            }
            return List.copyOf(values);
        }
    }

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
        Map<String, String> entityToDto = ctx.projectionSpec().entityToDtoMapping();

        for (ComputedFieldInfo info : computedFields) {
            for (String depPath : info.dependencyPaths()) {
                String trimmedPath = depPath.trim();
                String dtoField = entityToDto.get(trimmedPath);
                boolean isDirectlyProjected = dtoField != null
                        && !ctx.projectionSpec().computedFields().contains(dtoField);
                if (!isDirectlyProjected) {
                    int depSlot = ctx.plan().rootSchema().indexOfEntity(trimmedPath);
                    if (depSlot >= 0) {
                        excluded.add(depSlot);
                    }
                }
            }
        }

        return excluded;
    }
}

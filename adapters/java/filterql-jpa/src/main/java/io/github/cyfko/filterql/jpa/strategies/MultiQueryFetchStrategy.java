package io.github.cyfko.filterql.jpa.strategies;

import java.util.*;
import java.util.function.Function;

import io.github.cyfko.filterql.core.config.ProjectionPolicy;
import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.model.SortBy;
import io.github.cyfko.filterql.core.projection.ProjectionFieldParser;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.predicate.IdPredicateBuilder;
import io.github.cyfko.filterql.jpa.projection.*;

import static io.github.cyfko.filterql.jpa.strategies.AbstractMultiQueryFetchStrategy.MultiQueryExecutionPlan.*;

import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.filterql.jpa.utils.ProjectionUtils;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import io.github.cyfko.projection.metamodel.model.projection.ProjectionMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

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
    protected ExecutionContext step1_BuildExecutionContext(EntityManager em,
            PredicateResolver<?> pr,
            QueryExecutionParams params,
            Class<?> dtoClass) {
        boolean ignoreCase = params.projectionPolicy().fieldCase() == ProjectionPolicy.FieldCase.CASE_INSENSITIVE;
        QueryPlan.Builder planBuilder = QueryPlan.builder(dtoClass);
        Map<String, Pagination> collectionPagination;

        // Transform projection
        {
            Set<String> dtoProjection = params.projection();

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

            // Add collection info to plan
            collectionPagination = ProjectionFieldParser.parseCollectionOptions(dtoProjection);

            for (String dtoField : dtoProjection) {
                ProjectionFieldParser.ProjectionField parsed = ProjectionFieldParser.parse(dtoField);
                // nous devons construire le plan!
                // Niveau 0 : champs du DTO et toutes les projections n'incluant pas les
                // collections
                // Cela peut tout champ scalaire simple ou imbriqué
                // Niveau 1 : champs du DTO et toutes les projections incluant les collections
                // dans leurs chemins
                planBuilder.add(parsed);
            }
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> root = query.from(rootEntityClass);

        return new ExecutionContext(em, cb, root, query, pr, planBuilder.build(), collectionPagination);
    }

    @Override
    protected Map<Object, RowBuffer> step2_ExecuteRootQuery(ExecutionContext ctx, QueryExecutionParams params) {
        FieldSchema schema = ctx.plan().getSchema();
        List<Selection<?>> selections = new ArrayList<>(schema.fieldCount());

        // Build selections using schema (skip computed output slots)
        for (int i = 0; i < schema.fieldCount(); i++) {
            String entityField = schema.entityField(i);
            // Skip computed output placeholder slots - they're not in the entity
            if (entityField.startsWith(PREFIX_FOR_COMPUTED)) {
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
        List<String> idFields = ctx.plan().getIdFields();

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
        Map<String, QueryPlan>[] collectionPlans = ctx.plan().getCollectionPlans();
        if (collectionPlans == null || collectionPlans.length == 0) {
            return; // No collections to load
        }

        // Index of parents by collection path
        // Key: parent collection path ("" for root), Value: Map<parentId, RowBuffer>
        Map<String, Map<Object, RowBuffer>> parentIndexByPath = new HashMap<>();
        parentIndexByPath.put("", rootResults);

        // For each depth level
        for (int level = 0; level < collectionPlans.length; level++) {
            Map<String, QueryPlan> levelPlan = collectionPlans[level];

            if (levelPlan == null || levelPlan.isEmpty()) {
                continue;
            }

            // For each collection at this level
            for (Map.Entry<String, QueryPlan> entry : levelPlan.entrySet()) {
                String collectionPath = entry.getKey();
                QueryPlan collectionPlan = entry.getValue();

                // Determine parent path
                String parentPath = getParentPath(collectionPath);
                Map<Object, RowBuffer> parentIndex = parentIndexByPath.get(parentPath);

                if (parentIndex == null || parentIndex.isEmpty()) {
                    continue; // No parents for this collection
                }

                // Collect parent IDs
                Set<Object> parentIds = parentIndex.keySet();

                // Build PredicateResolver to filter by parent IDs (IN clause)
                PredicateResolver<?> collectionPr = buildParentIdPredicate(collectionPlan, parentIds);

                // Get pagination for this collection (stable from root context)
                Pagination collPagination = ctx.collectionPagination().get(collectionPath);
                QueryExecutionParams collectionParams = QueryExecutionParams.builder()
                        .pagination(collPagination)
                        .build();

                // Build execution context for this collection
                ExecutionContext collectionCtx = buildCollectionExecutionContext(
                        ctx.em(),
                        collectionPr,
                        collectionPlan,
                        ctx.collectionPagination());

                // Execute collection query
                Map<Object, RowBuffer> collectionResults = step2_ExecuteRootQuery(collectionCtx, collectionParams);

                // Link results to parent rows
                linkCollectionResultsToParents(
                        collectionResults,
                        parentIndex,
                        collectionPlan,
                        collectionPath);

                // Store child index for next level
                parentIndexByPath.put(collectionPath, collectionResults);
            }
        }
    }


    protected void step3_ExecuteCollectionQueries__(ExecutionContext ctx, Map<Object, RowBuffer> rootResults) {
        // // Group collection plans by depth
        // Map<Integer, List<CollectionPlan>> plansByDepth = new TreeMap<>();
        // for (CollectionPlan cplan : ctx.plan().collectionPlans()) {
        // plansByDepth.computeIfAbsent(cplan.depth(), k -> new
        // ArrayList<>()).add(cplan);
        // }
        //
        // // Track parent lookup by collection path
        // Map<String, Map<Object, RowBuffer>> parentIndexByPath = new HashMap<>();
        // parentIndexByPath.put("", rootResults);
        //
        // // Track which schema applies to each collection path
        // Map<String, FieldSchema> schemaByPath = new HashMap<>();
        // schemaByPath.put("", ctx.plan().getSchema());
        //
        // Set<Object> currentLevelParentIds = rootResults.keySet();
        //
        // for (Map.Entry<Integer, List<CollectionPlan>> depthEntry :
        // plansByDepth.entrySet()) {
        // List<CollectionPlan> plansAtDepth = depthEntry.getValue();
        //
        // Set<Object> nextLevelIds = new HashSet<>();
        //
        // for (CollectionPlan cplan : plansAtDepth) {
        // CollectionQueryResult result = executeCollectionQuery(ctx, cplan,
        // currentLevelParentIds);
        //
        // // Attach children to parents
        // String parentPath = getParentPath(cplan.collectionPath());
        // Map<Object, RowBuffer> parentIndex = parentIndexByPath.get(parentPath);
        // FieldSchema parentSchema = schemaByPath.get(parentPath);
        //
        // if (parentIndex != null && parentSchema != null) {
        // // Get the collection name relative to parent
        // String collName = cplan.collectionPath().contains(".")
        // ? cplan.collectionPath().substring(cplan.collectionPath().lastIndexOf('.') +
        // 1)
        // : cplan.collectionPath();
        //
        // var collectionSlotIndex = parentSchema.indexOfDto(collName);
        //
        // // If not found by short name, try the DTO collection name
        // if (collectionSlotIndex == Indexer.NONE) {
        // collectionSlotIndex = parentSchema.indexOfDto(cplan.dtoCollectionName());
        // }
        //
        // for (Map.Entry<Object, List<RowBuffer>> entry :
        // result.childrenByParent.entrySet()) {
        // RowBuffer parent = parentIndex.get(entry.getKey());
        // if (parent != null && collectionSlotIndex != Indexer.NONE) {
        // parent.setCollection(collectionSlotIndex.index(), entry.getValue());
        // }
        // }
        // }
        //
        // // Build child index for next level
        // parentIndexByPath.put(cplan.collectionPath(), result.childIndex);
        // schemaByPath.put(cplan.collectionPath(), cplan.childSchema());
        // nextLevelIds.addAll(result.childIndex.keySet());
        // }
        //
        // currentLevelParentIds = nextLevelIds;
        // if (currentLevelParentIds.isEmpty())
        // break;
        // }
    }

    @Override
    protected List<RowBuffer> step4_transform(ExecutionContext ctx, Map<Object, RowBuffer> rootResults) {
        Map<String, Function<Object[], Object>> computeMethods = new HashMap<>(10);
        Map<String, Map<Object, Number>> reducedValues = new HashMap<>();
        boolean reducersAggregateDone = false;

        AggregateQueryExecutor aggregateQueryExecutor = new AggregateQueryExecutor(ctx.em(), rootEntityClass,
                PersistenceRegistry.getIdFields(rootEntityClass));

        for (RowBuffer row : rootResults.values()) {
            FieldSchema schema = row.getSchema();

            for (var e : schema.getComputedFieldIndexMap().entrySet()) {
                final var dtoField = e.getKey();
                final var infos = e.getValue();
                Object[] params = new Object[infos.length];

                for (int i = 0; i < params.length; i++) {
                    FieldSchema.DependencyInfo info = infos[i];
                    if (info.reducer() != null) {
                        String collectionPath = schema.dtoField(info.index()).substring(PREFIX_FOR_COMPUTED.length());
                        if (!reducersAggregateDone) {
                            var reduced = aggregateQueryExecutor.executeAggregateQuery(collectionPath, info.reducer(),
                                    ctx.plan().getIdFields());
                            reducedValues.put(collectionPath, reduced);
                        }
                        params[i] = reducedValues.get(collectionPath).get(1); // TODO: a la place de 1 mettre l'ID
                                                                              // composite ou simple
                    } else {
                        params[i] = row.get(info.index());
                    }
                }
                reducersAggregateDone = true;

                try {
                    if (!computeMethods.containsKey(dtoField)) {
                        var method = ProjectionUtils.resolveComputeMethod(instanceResolver, dtoClass, dtoField, params);
                        computeMethods.put(dtoField, method);
                    }
                    Object computed = computeMethods.get(dtoField).apply(params);
                    row.set(dtoField, computed);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to compute field: " + dtoField, ex);
                }
            }
        }

        // computeExcludedSlots(ctx.plan().computedFields(), ctx);
        // Map<String, Function<Object[], Object>> computeMethods = new HashMap<>(100);
        //
        // for (RowBuffer row : rootResults.values()) {
        // for (ComputedFieldInfo info : ctx.plan().computedFields()) {
        // int[] dependencySlots = info.dependencySlots();
        // String[] dependencyPaths = info.dependencyPaths();
        //
        // // Gather dependencies using O(1) slot access
        // Object[] params = new Object[dependencySlots.length];
        // for (int i = 0; i < params.length; i++) {
        // int slot = dependencySlots[i];
        // if (slot >= 0) {
        // params[i] = row.get(slot);
        // } else {
        // // Fallback to name lookup
        // params[i] = row.getOrNull(dependencyPaths[i].trim());
        // }
        // }
        //
        // try {
        // if (!computeMethods.containsKey(info.dtoFieldName())) {
        // var method = ProjectionUtils.resolveComputeMethod(instanceResolver, dtoClass,
        // info.dtoFieldName(), params);
        // computeMethods.put(info.dtoFieldName(), method);
        // }
        // Object computed = computeMethods.get(info.dtoFieldName()).apply(params);
        // int outputSlot = info.outputSlot();
        // if (outputSlot >= 0) {
        // row.set(outputSlot, computed);
        // }
        // } catch (Exception e) {
        // throw new RuntimeException("Failed to compute field: " + info.dtoFieldName(),
        // e);
        // }
        // }
        // }
        return new ArrayList<>(rootResults.values());
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
                idx = schema.indexOfDto(PREFIX_FOR_INTERNAL_USAGE + idFields.getFirst()).index();
            }
            return idx >= 0 ? row.get(idx) : null;
        } else {
            List<Object> values = new ArrayList<>(idFields.size());
            for (String idField : idFields) {
                int idx = schema.indexOfEntity(idField);
                if (idx < 0) {
                    idx = schema.indexOfDto(PREFIX_FOR_INTERNAL_USAGE + idField).index();
                }
                values.add(idx >= 0 ? row.get(idx) : null);
            }
            return List.copyOf(values);
        }
    }

    // ==================== Collection Query Helper Methods ====================

    /**
     * Builds a PredicateResolver that filters collection elements by parent IDs.
     */
    private PredicateResolver<?> buildParentIdPredicate(QueryPlan collectionPlan, Set<Object> parentIds) {
        FieldSchema schema = collectionPlan.getSchema();
        List<String> entityPaths = new ArrayList<>();

        // Find parent ID fields in schema (prefixed by _i_pid_)
        for (int i = 0;; i++) {
            String alias = PREFIX_FOR_INTERNAL_USAGE + SUFFIX_PARENT_ID + i;
            Indexer indexer = schema.indexOfDto(alias);
            if (indexer == Indexer.NONE) {
                break;
            }
            // Get the corresponding entity field path
            entityPaths.add(schema.entityField(indexer.index()));
        }

        final List<String> finalEntityPaths = entityPaths;

        return (root, query, cb) -> {
            List<Path<?>> idPaths = new ArrayList<>(finalEntityPaths.size());
            for (String entityPath : finalEntityPaths) {
                idPaths.add(PathResolverUtils.resolvePath(root, entityPath));
            }

            return IdPredicateBuilder.defaultBuilder()
                    .buildIdPredicate(cb, idPaths, parentIds);
        };
    }

    /**
     * Builds an ExecutionContext for executing a collection query.
     */
    private ExecutionContext buildCollectionExecutionContext(
            EntityManager em,
            PredicateResolver<?> pr,
            QueryPlan collectionPlan,
            Map<String, Pagination> collectionPagination) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> root = query.from(collectionPlan.getEntityClass());

        return new ExecutionContext(
                em,
                cb,
                root,
                query,
                pr,
                collectionPlan,
                collectionPagination);
    }

    /**
     * Links collection results to their parent RowBuffers.
     */
    private void linkCollectionResultsToParents(
            Map<Object, RowBuffer> collectionResults,
            Map<Object, RowBuffer> parentIndex,
            QueryPlan collectionPlan,
            String collectionPath) {

        // Get collection name for the slot in the parent
        String collectionName = collectionPath.contains(".")
                ? collectionPath.substring(collectionPath.lastIndexOf('.') + 1)
                : collectionPath;

        // Group results by parent ID
        Map<Object, List<RowBuffer>> childrenByParentId = new LinkedHashMap<>();

        for (RowBuffer childRow : collectionResults.values()) {
            Object parentId = extractParentIdFromChild(childRow);
            childrenByParentId
                    .computeIfAbsent(parentId, k -> new ArrayList<>())
                    .add(childRow);
        }

        // Get parent schema (from first parent)
        if (parentIndex.isEmpty())
            return;

        FieldSchema parentSchema = parentIndex.values().iterator().next().getSchema();
        Indexer collIdx = parentSchema.indexOfDto(collectionName);

        if (collIdx == Indexer.NONE || !collIdx.isCollection()) {
            logger.warning(() -> "Collection slot not found for: " + collectionName);
            return;
        }

        // Assign children to each parent
        for (Map.Entry<Object, RowBuffer> parentEntry : parentIndex.entrySet()) {
            Object parentId = parentEntry.getKey();
            RowBuffer parentRow = parentEntry.getValue();

            List<RowBuffer> children = childrenByParentId.getOrDefault(parentId, List.of());
            parentRow.setCollection(collIdx.index(), children);
        }
    }

    /**
     * Extracts the parent ID from a child row.
     */
    private Object extractParentIdFromChild(RowBuffer childRow) {
        FieldSchema schema = childRow.getSchema();

        // Count number of parent ID fields
        int count = 0;
        while (schema.indexOfDto(PREFIX_FOR_INTERNAL_USAGE + SUFFIX_PARENT_ID + count) != Indexer.NONE) {
            count++;
        }

        if (count == 0) {
            return null;
        }

        if (count == 1) {
            int idx = schema.indexOfDto(PREFIX_FOR_INTERNAL_USAGE + SUFFIX_PARENT_ID + "0").index();
            return childRow.get(idx);
        } else {
            List<Object> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int idx = schema.indexOfDto(PREFIX_FOR_INTERNAL_USAGE + SUFFIX_PARENT_ID + i).index();
                values.add(childRow.get(idx));
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
        return null;

        // Une query de collection est comme une query normale (ie Root query) mais pour
        // le DTO cible.
        // La subtilité est de relier chaque liste a la ligne du résultat parent.
        // Donc il faudrait grouper les résultats de la requête par parent IDs pour
        // faire la liaison subséquement
        // select [<projection with aliases> + <parent aliases>] where (in predicate
        // filtres... ) sort by ? limit ?

        // map 'niveau' -> collection plans | les résultats peuvent aussi être mappés
        // par niveau
        // map 'collection plan' -> parentId, collection path, field schema

        // if (parentIds == null || parentIds.isEmpty()) {
        // return new CollectionQueryResult(Collections.emptyMap(),
        // Collections.emptyMap());
        // }
        //
        // Map<Object, List<RowBuffer>> resultsByParent = new LinkedHashMap<>();
        // Map<Object, RowBuffer> childIndex = new LinkedHashMap<>();
        // List<Object> parentIdList = new ArrayList<>(parentIds);
        //
        // FieldSchema childSchema = cplan.childSchema();
        //
        // for (int batchStart = 0; batchStart < parentIdList.size(); batchStart +=
        // BATCH_SIZE) {
        // int batchEnd = Math.min(batchStart + BATCH_SIZE, parentIdList.size());
        // List<Object> batchIds = parentIdList.subList(batchStart, batchEnd);
        //
        // CriteriaQuery<Tuple> query = ctx.cb().createTupleQuery();
        // Root<?> collRoot = query.from(cplan.elementClass());
        //
        // List<Selection<?>> selections = new ArrayList<>();
        //
        // // Parent ID fields
        // Path<?> parentRefPath = collRoot.get(cplan.parentReferenceField());
        // List<String> parentIdFields =
        // PersistenceRegistry.getIdFields(parentRefPath.getJavaType());
        //
        // for (int i = 0; i < parentIdFields.size(); i++) {
        // Path<?> parentIdPath = parentRefPath.get(parentIdFields.get(i));
        // selections.add(parentIdPath.alias(PREFIX_FOR_INTERNAL_USAGE +
        // SUFFIX_PARENT_ID + i));
        // }
        //
        // // Child fields using schema
        // for (int i = 0; i < childSchema.fieldCount(); i++) {
        // Path<?> path = PathResolverUtils.resolvePath(collRoot,
        // childSchema.entityField(i));
        // selections.add(path.alias(childSchema.dtoField(i)));
        // }
        //
        // query.multiselect(selections);
        //
        // // Build IN predicate
        // Predicate wherePredicate = buildInPredicate(ctx.cb(), parentRefPath,
        // parentIdFields, batchIds);
        // query.where(wherePredicate);
        //
        // // Apply sorting using pre-computed indices
        // if (cplan.sortFieldIndices().length > 0) {
        // List<Order> orders = new ArrayList<>();
        // for (int i = 0; i < cplan.sortFieldIndices().length; i++) {
        // int fieldIdx = cplan.sortFieldIndices()[i];
        // if (fieldIdx >= 0 && fieldIdx < childSchema.fieldCount()) {
        // Path<?> sortPath = PathResolverUtils.resolvePath(collRoot,
        // childSchema.entityField(fieldIdx));
        // orders.add(cplan.sortDescending()[i] ? ctx.cb().desc(sortPath) :
        // ctx.cb().asc(sortPath));
        // }
        // }
        // if (!orders.isEmpty()) {
        // query.orderBy(orders);
        // }
        // }
        //
        // List<Tuple> tuples = ctx.em().createQuery(query).getResultList();
        //
        // // Process results
        // Map<Object, List<RowBuffer>> batchResult = new LinkedHashMap<>();
        //
        // for (Tuple tuple : tuples) {
        // Object parentId = extractParentIdFromTuple(tuple, parentIdFields.size());
        // RowBuffer childRow = tupleToRowBuffer(tuple, childSchema);
        // Object childId = extractCompositeKey(childRow, cplan.idFields(),
        // childSchema);
        //
        // batchResult.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childRow);
        // childIndex.put(childId, childRow);
        // }
        //
        // // Apply per-parent pagination
        // if (cplan.offsetPerParent() != null || cplan.limitPerParent() != null) {
        // for (Map.Entry<Object, List<RowBuffer>> entry : batchResult.entrySet()) {
        // List<RowBuffer> children = entry.getValue();
        // int offset = cplan.offsetPerParent() != null ? cplan.offsetPerParent() : 0;
        // int limit = cplan.limitPerParent() != null ? cplan.limitPerParent() :
        // children.size();
        // int end = Math.min(children.size(), offset + limit);
        // if (offset > 0 || limit < children.size()) {
        // entry.setValue(children.subList(Math.min(offset, children.size()), end));
        // }
        // }
        // }
        //
        // // Merge batch results
        // for (Map.Entry<Object, List<RowBuffer>> entry : batchResult.entrySet()) {
        // resultsByParent.computeIfAbsent(entry.getKey(), k -> new
        // ArrayList<>()).addAll(entry.getValue());
        // }
        // }
        //
        // return new CollectionQueryResult(resultsByParent, childIndex);
    }

    private String getParentPath(String collectionPath) {
        if (!collectionPath.contains(".")) {
            return "";
        }
        return collectionPath.substring(0, collectionPath.lastIndexOf('.'));
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
        String prefix = PREFIX_FOR_INTERNAL_USAGE;
        if (fieldCount == 1) {
            return tuple.get(prefix + SUFFIX_PARENT_ID + "0");
        } else {
            List<Object> values = new ArrayList<>(fieldCount);
            for (int i = 0; i < fieldCount; i++) {
                values.add(tuple.get(prefix + SUFFIX_PARENT_ID + i));
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
        // Map<String, String> entityToDto = ctx.projectionSpec().entityToDtoMapping();
        //
        // for (ComputedFieldInfo info : computedFields) {
        // for (String depPath : info.dependencyPaths()) {
        // String trimmedPath = depPath.trim();
        // String dtoField = entityToDto.get(trimmedPath);
        // boolean isDirectlyProjected = dtoField != null
        // && !ctx.projectionSpec().computedFields().contains(dtoField);
        // if (!isDirectlyProjected) {
        // int depSlot = ctx.plan().rootSchema().indexOfEntity(trimmedPath);
        // if (depSlot >= 0) {
        // excluded.add(depSlot);
        // }
        // }
        // }
        // }
        //
        // ctx.plan().rootSchema().setSerialisationExcludedSlots(excluded);
        return excluded;
    }
}

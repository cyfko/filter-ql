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
            FieldSchema.FieldStatus fieldStatus = schema.getFieldStatus(i);
            if (fieldStatus == FieldSchema.FieldStatus.SQL_IGNORE ||
                fieldStatus == FieldSchema.FieldStatus.SQL_IGNORE_COLLECTION) {
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
        int limit = params.hasPagination() ? params.pagination().size() : Integer.MAX_VALUE;

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
            for (int c : schema.collectionIndexes()) {
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

    @Override
    protected List<RowBuffer> step4_transform(ExecutionContext ctx, Map<Object, RowBuffer> rootResults) {
        Map<String, Function<Object[], Object>> computeMethods = new HashMap<>(10);
        Map<String, Map<Object, Number>> reducedValues = new HashMap<>();

        AggregateQueryExecutor aggregateQueryExecutor = new AggregateQueryExecutor(ctx.em(), rootEntityClass,
                PersistenceRegistry.getIdFields(rootEntityClass));

        for (var entry : rootResults.entrySet()) {
            Object rowId = entry.getKey(); // The actual ID of this row
            RowBuffer row = entry.getValue();
            FieldSchema schema = row.getSchema();

            for (var e : schema.getComputedFieldIndexMap().entrySet()) {
                final var dtoField = e.getKey();
                final var infos = e.getValue();
                Object[] params = new Object[infos.length];

                for (int i = 0; i < params.length; i++) {
                    FieldSchema.DependencyInfo info = infos[i];
                    if (info.reducer() != null) {
                        String collectionPath = schema.entityField(info.index())
                                .substring(PREFIX_FOR_COMPUTED.length());
                        // Execute aggregate query only once per collection path (for all rows)
                        if (!reducedValues.containsKey(collectionPath)) {
                            var reduced = aggregateQueryExecutor.executeAggregateQuery(
                                    collectionPath,
                                    info.reducer(),
                                    rootResults.keySet() // Pass actual ID values, not field names
                            );
                            reducedValues.put(collectionPath, reduced);
                        }
                        // Get the reduced value for THIS ROW's ID
                        Map<Object, Number> pathValues = reducedValues.get(collectionPath);
                        params[i] = pathValues != null ? pathValues.get(rowId) : null;
                    } else {
                        params[i] = row.get(info.index());
                    }
                }

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

    private String getParentPath(String collectionPath) {
        if (!collectionPath.contains(".")) {
            return "";
        }
        return collectionPath.substring(0, collectionPath.lastIndexOf('.'));
    }

}

package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.config.ProjectionPolicy;
import io.github.cyfko.filterql.core.exception.ProjectionDefinitionException;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.SortBy;
import io.github.cyfko.filterql.core.projection.ProjectionFieldParser;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.utils.ProjectionUtils;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import io.github.cyfko.projection.metamodel.model.projection.ProjectionMetadata;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import io.github.cyfko.filterql.jpa.projection.MultiQueryExecutionPlan;
import io.github.cyfko.filterql.jpa.projection.MultiQueryExecutionPlan.CollectionLevelPlan;
import io.github.cyfko.filterql.jpa.projection.MultiQueryExecutionPlan.CollectionNode;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Multi-query JPA projection strategy implementing a DTO-centric, batch-driven
 * fetch logic.
 * <p>
 * This strategy performs batch queries to efficiently resolve both scalar and
 * collection data for DTO projections, using DTO field names
 * directly in the result aliases and output, thereby obviating the need for
 * post-processing DTO mapping. Entity field names are used
 * only for query and path resolution. This approach provides high performance
 * and clean separation between DTOs and entities in query results.
 * <br>
 * <b>Key innovations:</b>
 * <ul>
 * <li>projection fields names are used as query aliases and output keys, not
 * entity field names.</li>
 * <li>Supports nested projections and batched queries for multi-level
 * collections.</li>
 * <li>Provides direct mapping between entity fields and DTO properties using
 * projection metadata.</li>
 * <li>Composite keys and multi-level collection joins are handled
 * transparently.</li>
 * <li>Automatically project all scalar fields (discarding collections and
 * linked entities) when no explicit
 * projection found.</li>
 * </ul>
 * </p>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 *
 * @example
 * 
 *          <pre>
 * {@code
 * // Batch-fetch users with nested addresses:
 * MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserDTO.class);
 * List<Map<String, Object>> results = strategy.execute(
 *      entityManager,
 *      predicateResolver,
 *      queryParams
 * );
 *
 * // Output format: each map contains DTO field keys (e.g. "name", "address.city").
 * }
 * </pre>
 */
public class MultiQueryFetchStrategy implements ExecutionStrategy<List<Map<String, Object>>> {

    private static final Logger logger = Logger.getLogger(MultiQueryFetchStrategy.class.getName());
    private static final int BATCH_SIZE = 1000;

    private final Class<?> rootEntityClass;
    private final Class<?> dtoClass;
    private final InstanceResolver instanceResolver;

    /**
     * Initializes the strategy, validating that the DTO maps to the entity and that
     * projection metadata exists.
     * <p>
     * Note: The {@code projectionClass} may also be an entity class (annotated with
     * {@link jakarta.persistence.Entity})
     * for auto projection mappings.
     * </p>
     *
     * @param projectionClass class annotated with
     *                        {@link io.github.cyfko.projection.Projection}
     *                        and describing the projection structure.
     * @throws IllegalStateException    if no projection metadata is available for
     *                                  the DTO class
     * @throws IllegalArgumentException if the DTO maps to a different entity than
     *                                  configured
     */
    public MultiQueryFetchStrategy(Class<?> projectionClass) {
        this(projectionClass, null);
    }

    /**
     * Initializes the strategy, validating that the DTO maps to the entity and that
     * projection metadata exists.
     * <p>
     * Note: The {@code projectionClass} may also be an entity class (annotated with
     * {@link jakarta.persistence.Entity})
     * for auto projection mappings.
     * </p>
     *
     * @param projectionClass  class annotated with
     *                         {@link io.github.cyfko.projection.Projection}
     *                         and describing the projection structure.
     * @param instanceResolver the resolver for computed field providers, or
     *                         {@code null} if none are needed
     * @throws IllegalStateException    if no projection metadata is available for
     *                                  the DTO class
     * @throws IllegalArgumentException if the DTO maps to a different entity than
     *                                  configured
     */
    public MultiQueryFetchStrategy(Class<?> projectionClass, InstanceResolver instanceResolver) {
        Objects.requireNonNull(projectionClass,
                "DTO class is required. Usage of entity class itself as the dto class is perfectly fine.");
        ProjectionMetadata metadata = ProjectionRegistry.getMetadataFor(projectionClass);

        if (metadata == null) {
            throw new IllegalStateException(
                    "Provided class is neither a projection nor an entity: " + projectionClass.getName());
        }

        if (metadata.computedFields().length > 0 && instanceResolver == null) {
            throw new IllegalStateException("Projection <" + projectionClass.getSimpleName() + "> has computed fields. "
                    +
                    "Please consider using constructor with non-null arguments: AbstractMultiQueryFetchStrategy(Class<?>, ComputerResolver)");
        }

        this.dtoClass = projectionClass;
        this.rootEntityClass = metadata.entityClass();
        this.instanceResolver = instanceResolver;
    }

    /**
     * Executes the batch-projection fetch logic for the specified parameters.
     * <p>
     * The steps performed are:
     * </p>
     * <ol>
     * <li>Transforms the requested DTO projection to corresponding entity
     * projection fields.</li>
     * <li>Builds and executes the root entity query with DTO field aliases.</li>
     * <li>Initializes nested empty collections as required by the DTO
     * structure.</li>
     * <li>Performs level-by-level batch sub-queries for each collection field,
     * attaching results by parent keys.</li>
     * <li>Returns results directly as a list of DTO-mapped result maps (no
     * post-processing).</li>
     * </ol>
     *
     * @param em     the JPA EntityManager for database query execution
     * @param pr     a predicate resolver for filtering logic
     * @param params query execution parameters, including projection selection,
     *               sorting, and pagination
     * @return a list of result maps, each keyed by DTO field names and containing
     *         hydrated values per output DTO
     *
     * @throws ProjectionDefinitionException if any requested projection field
     *                                       cannot be resolved
     *
     * @example
     * 
     *          <pre>
     * {@code
     * // Fetch users with nested addresses and paginated friends
     * Set<String> projection = Set.of("id", "name", "address.city", "friends[limit=5].name");
     * QueryExecutionParams params = new QueryExecutionParams(projection, ...);
     * List<Map<String, Object>> users =
     *      strategy.execute(entityManager, predicateResolver, params);
     *
     * // For each user map, output keys reflect requested DTO projection: "id", "name", nested "address.city", and "friends"
     * }
     * </pre>
     */
    @Override
    public <Context> List<Map<String, Object>> execute(Context ctx, PredicateResolver<?> pr,
            QueryExecutionParams params) {
        EntityManager em = (EntityManager) ctx;
        long startTime = System.nanoTime();

        // 1. Build execution plan with DTO field mapping
        QueryContext queryCtx;
        {
            // 0. Transform DTO projection to entity projection and build mapping
            boolean ignoreCase = params.projectionPolicy().fieldCase() == ProjectionPolicy.FieldCase.CASE_INSENSITIVE;
            final var transformation = transformProjection(params.projection(), ignoreCase);

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Tuple> rootQuery = cb.createTupleQuery();
            Root<?> root = rootQuery.from(rootEntityClass);

            queryCtx = new QueryContext(em, cb, root, rootQuery, pr, transformation);
        }

        MultiQueryExecutionPlan plan = MultiQueryExecutionPlan.build(queryCtx.root,
                queryCtx.projectionSpec);

        logger.fine(() -> String.format("Execution plan: %d root fields, %d collection levels",
                plan.getRootScalarFields().size(), plan.getCollectionLevels().size()));

        // 2. Execute root query (with DTO aliases)
        Map<Object, Map<String, Object>> rootResults = executeRootQuery(queryCtx,
                plan, params);

        if (rootResults.isEmpty()) {
            logger.fine("Root query returned no results");
            return List.of();
        }

        logger.fine(() -> "Root query returned " + rootResults.size() + " entities");

        // 3. Initialize empty collections
        initializeEmptyCollections(rootResults, plan);

        // 4. Execute collection queries level-by-level
        Map<String, Map<Object, Map<String, Object>>> parentIndexByPath = new HashMap<>();
        parentIndexByPath.put("", rootResults);

        Set<Object> currentLevelParentIds = rootResults.keySet();

        for (CollectionLevelPlan level : plan.getCollectionLevels()) {
            logger.fine(() -> String.format("Executing collection level %d: %d collections",
                    level.depth(), level.collections().size()));

            Set<Object> nextLevelIds = new HashSet<>();

            for (CollectionNode node : level.collections()) {
                // executeCollectionQuery now returns both results and index
                BatchQueryResult queryResult = executeCollectionQuery(queryCtx, node,
                        currentLevelParentIds);

                attachToParentsWithIndex(parentIndexByPath, queryResult.childrenByParent(),
                        node);

                // Use pre-built child index instead of rebuilding it
                parentIndexByPath.put(node.collectionPath(), queryResult.childLookupIndex());
                nextLevelIds.addAll(queryResult.childLookupIndex().keySet());
            }

            currentLevelParentIds = nextLevelIds;

            if (currentLevelParentIds.isEmpty()) {
                logger.fine(() -> "No more IDs at level " + level.depth());
                break;
            }
        }

        // 5. No transformation needed - results already in DTO format!
        List<Map<String, Object>> results = new ArrayList<>(handleComputedFields(rootResults.values(), queryCtx));

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        logger.info(() -> String.format("Multi-query completed in %dms: %d roots",
                durationMs, results.size()));

        return results;
    }

    /**
     * Transforms a set of DTO projection field names into corresponding entity
     * field paths, building the mapping and options.
     * <p>
     * This method also parses any collection-level options (e.g. pagination) from
     * the DTO projection syntax.
     * </p>
     *
     * @param dtoProjection the set of DTO field names requested for projection
     * @param ignoreCase    whether DTO field matching is case-insensitive
     * @return the transformation object containing: entity projection field names,
     *         collection options, and the entity→DTO map
     *
     * @throws ProjectionDefinitionException if any DTO path does not resolve to a
     *                                       valid entity field path
     */
    private MultiQueryExecutionPlan.ProjectionSpec transformProjection(Set<String> dtoProjection, boolean ignoreCase) {

        Set<String> entityProjection = new HashSet<>();
        Set<String> computedFields = new HashSet<>();
        Map<String, Pagination> entityCollectionOptions = new HashMap<>();
        Map<String, String> entityToDtoFieldMap = new HashMap<>();
        Map<String, Pagination> dtoCollectionOptions = ProjectionFieldParser.parseCollectionOptions(dtoProjection);

        if (dtoProjection == null || dtoProjection.isEmpty()) { // Project all fields
            dtoProjection = new HashSet<>();
            ProjectionMetadata meta = ProjectionRegistry.getMetadataFor(dtoClass);
            for (var dm : meta.directMappings()) {
                if (dm.collection().isPresent() || dm.isNested()
                        || ProjectionRegistry.getMetadataFor(dm.dtoFieldType()) != null)
                    continue;
                // Ne projette QUE les champs scalaires
                dtoProjection.add(dm.dtoField());
            }
            for (var cf : meta.computedFields()) {
                dtoProjection.add(cf.dtoField());
            }
        }

        for (String dtoField : dtoProjection) {
            Set<String> expandedDtoProjection = MultiQueryExecutionPlan
                    .expandCompactNotation(dtoField.replaceAll("\\[.*?]", ""));

            for (String baseDtoField : expandedDtoProjection) {
                String entityPath;
                try {
                    entityPath = ProjectionRegistry.toEntityPath(baseDtoField, dtoClass, ignoreCase);
                } catch (IllegalArgumentException e) {
                    throw new ProjectionDefinitionException(
                            baseDtoField + " does not resolve to a valid projection field path.", e);
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
                entityProjection,
                computedFields,
                entityCollectionOptions,
                entityToDtoFieldMap);
    }

    /**
     * Executes root query with DTO field aliases.
     *
     * @param ctx    the query context for execution
     * @param plan   the execution plan for the projection
     * @param params query execution parameters
     * @return a map from root entity keys to their DTO-mapped result maps
     */
    private Map<Object, Map<String, Object>> executeRootQuery(
            QueryContext ctx,
            MultiQueryExecutionPlan plan,
            QueryExecutionParams params) {
        List<Selection<?>> selections = new ArrayList<>();

        for (var mapping : plan.getRootScalarFields()) {
            Path<?> path = PathResolverUtils.resolvePath(ctx.root, mapping.entityField());
            selections.add(path.alias(mapping.dtoField()));
        }

        ctx.query().multiselect(selections);

        // Apply filtering
        // noinspection rawtypes,unchecked
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

        // Convert to map keyed by root ID (supports composite keys)
        Map<Object, Map<String, Object>> results = new LinkedHashMap<>();
        for (Tuple tuple : tuples) {
            Object rootId = extractCompositeKey(tuple, plan.getRootIdField());
            Map<String, Object> rootMap = tupleToMap(tuple);
            results.put(rootId, rootMap);
        }

        return results;
    }

    /**
     * Converts a JPA Tuple to a map, filtering out internal fields at construction
     * for optimal performance.
     * <p>
     * Internal fields (prefixed with PREFIX_FOR_INTERNAL_USAGE) are never inserted
     * into the result map.
     * </p>
     *
     * @param tuple the JPA Tuple to convert
     * @return a map representation of the tuple, with DTO field names as keys
     */
    private Map<String, Object> tupleToMap(Tuple tuple) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (var element : tuple.getElements()) {
            String alias = element.getAlias();

            // Skip internal fields at construction time
            if (alias.startsWith(MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE)) {
                continue;
            }

            Object value = tuple.get(element);

            // Handle nested DTO fields (e.g., "address.city")
            if (alias.contains(".")) {
                insertNestedValue(map, alias, value);
            } else {
                map.put(alias, value);
            }
        }

        return map;
    }

    /**
     * Inserts a value into a nested map structure, supporting nested DTO fields
     * (e.g., "address.city").
     *
     * @param map   the map to update
     * @param path  the dotted path indicating where to insert the value
     * @param value the value to insert
     */
    @SuppressWarnings("unchecked")
    private void insertNestedValue(Map<String, Object> map, String path, Object value) {
        final String[] segments = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            current = (Map<String, Object>) current.computeIfAbsent(
                    segment,
                    k -> new LinkedHashMap<>());
        }

        String lastSegment = segments[segments.length - 1];
        current.put(lastSegment, value);
    }

    /**
     * Initializes empty collections for the root results as required by the DTO
     * structure.
     *
     * @param rootResults the map of root entity results
     * @param plan        the execution plan for the projection
     */
    private void initializeEmptyCollections(
            Map<Object, Map<String, Object>> rootResults,
            MultiQueryExecutionPlan plan) {
        for (CollectionLevelPlan level : plan.getCollectionLevels()) {
            for (CollectionNode node : level.collections()) {
                if (level.depth() == 1) {
                    for (Map<String, Object> rootResult : rootResults.values()) {
                        rootResult.putIfAbsent(node.collectionName(), new ArrayList<>());
                    }
                }
            }
        }
    }

    /**
     * Executes a collection query with DTO field aliases.
     * Returns both the collection results grouped by parent and a child index for
     * next level queries.
     *
     * @param ctx       the query context for execution
     * @param node      the collection node describing the sub-query
     * @param parentIds the set of parent IDs for which to fetch children
     * @return a {@link BatchQueryResult} containing both results grouped by parent
     *         and a child index
     */
    private BatchQueryResult executeCollectionQuery(
            QueryContext ctx,
            CollectionNode node,
            Set<Object> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return new BatchQueryResult(Collections.emptyMap(), Collections.emptyMap());
        }

        Map<Object, List<Map<String, Object>>> resultsByParent = new LinkedHashMap<>();
        Map<Object, Map<String, Object>> childIndex = new LinkedHashMap<>();
        List<Object> parentIdList = new ArrayList<>(parentIds);

        for (int batchStart = 0; batchStart < parentIdList.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, parentIdList.size());
            List<Object> batchIds = parentIdList.subList(batchStart, batchEnd);

            CriteriaQuery<Tuple> query = ctx.cb.createTupleQuery();
            Root<?> collectionRoot = query.from(node.elementClass());

            List<Selection<?>> selections = new ArrayList<>();

            // Parent ID fields
            Path<?> parentRefPath = collectionRoot.get(node.parentReferenceField());
            List<String> parentIdFields = PersistenceRegistry.getIdFields(parentRefPath.getJavaType());

            for (int i = 0; i < parentIdFields.size(); i++) {
                Path<?> parentIdFieldPath = parentRefPath.get(parentIdFields.get(i));
                selections.add(
                        parentIdFieldPath.alias(MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE + "parent_id_" + i));
            }

            // Collection fields with DTO aliases
            for (var mapping : node.fieldsToSelect()) {
                Path<?> path = PathResolverUtils.resolvePath(collectionRoot, mapping.entityField());
                selections.add(path.alias(mapping.dtoField()));
            }

            query.multiselect(selections);

            Predicate wherePredicate = buildInPredicate(ctx.cb, parentRefPath, parentIdFields, batchIds);
            query.where(wherePredicate);

            if (node.sortFields() != null && !node.sortFields().isEmpty()) {
                List<Order> orders = new ArrayList<>();
                for (SortBy sortField : node.sortFields()) {
                    Path<?> sortPath = PathResolverUtils.resolvePath(collectionRoot, sortField.field());
                    orders.add("desc".equalsIgnoreCase(sortField.direction())
                            ? ctx.cb.desc(sortPath)
                            : ctx.cb.asc(sortPath));
                }
                query.orderBy(orders);
            }

            List<Tuple> tuples = ctx.em.createQuery(query).getResultList();

            // Extract both parent and child IDs BEFORE map conversion
            Map<Object, List<Map<String, Object>>> batchResult = new LinkedHashMap<>();

            for (Tuple tuple : tuples) {
                Object parentId = extractParentIdFromTuple(tuple, parentIdFields.size());

                // Extract child ID from tuple BEFORE filtering internal fields
                Object childId = extractCompositeKey(tuple, node.idFields());

                // Now convert to clean map (without internal fields)
                Map<String, Object> childMap = tupleToMap(tuple);

                batchResult.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childMap);
                childIndex.put(childId, childMap);
            }

            // Apply per-parent pagination
            if (node.offsetPerParent() != null || node.limitPerParent() != null) {
                for (Map.Entry<Object, List<Map<String, Object>>> entry : batchResult.entrySet()) {
                    List<Map<String, Object>> children = entry.getValue();
                    int offset = node.offsetPerParent() != null ? node.offsetPerParent() : 0;
                    int limit = node.limitPerParent() != null ? node.limitPerParent() : children.size();
                    int end = Math.min(children.size(), offset + limit);
                    if (offset > 0 || limit < children.size()) {
                        entry.setValue(children.subList(Math.min(offset, children.size()), end));
                    }
                }
            }

            // Merge batch results
            for (Map.Entry<Object, List<Map<String, Object>>> entry : batchResult.entrySet()) {
                resultsByParent.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }

        return new BatchQueryResult(resultsByParent, childIndex);
    }

    /**
     * Attaches collection results to parent entities.
     * No longer needs to remove internal fields as they're filtered at construction
     * time.
     */
    private void attachToParentsWithIndex(
            Map<String, Map<Object, Map<String, Object>>> parentIndexByPath,
            Map<Object, List<Map<String, Object>>> collectionResults,
            CollectionNode node) {
        String parentPath = getParentPath(node.collectionPath());
        Map<Object, Map<String, Object>> parentIndex = parentIndexByPath.get(parentPath);

        if (parentIndex == null) {
            throw new IllegalStateException("Parent index not found for path: " + parentPath);
        }

        for (Map.Entry<Object, List<Map<String, Object>>> entry : collectionResults.entrySet()) {
            Object parentId = entry.getKey();
            Map<String, Object> parent = parentIndex.get(parentId);
            if (parent != null) {
                List<Map<String, Object>> children = entry.getValue();

                int lastDotIndex = node.collectionName().lastIndexOf(".");
                String collectionName = lastDotIndex < 0 ? node.collectionName()
                        : node.collectionName().substring(lastDotIndex + 1);
                parent.put(collectionName, children);
            }
        }
    }

    private Map<Object, Map<String, Object>> buildChildIndex(
            Map<Object, List<Map<String, Object>>> collectionResults,
            List<String> idFields) {
        Map<Object, Map<String, Object>> index = new HashMap<>();

        for (List<Map<String, Object>> children : collectionResults.values()) {
            for (Map<String, Object> child : children) {
                Object childId = extractCompositeKey(child, idFields);
                index.put(childId, child);
            }
        }

        return index;
    }

    private String getParentPath(String collectionPath) {
        if (!collectionPath.contains(".")) {
            return "";
        }
        int lastDot = collectionPath.lastIndexOf('.');
        return collectionPath.substring(0, lastDot);
    }

    private Collection<Map<String, Object>> handleComputedFields(Collection<Map<String, Object>> result,
            QueryContext ctx) {
        if (dtoClass == rootEntityClass)
            return result;
        Map<String, String> entityToDtoMapping = ctx.projectionSpec.entityToDtoMapping();

        for (String entityPath : entityToDtoMapping.keySet()) {
            final String field = entityToDtoMapping.get(entityPath);
            if (ctx.projectionSpec.computedFields().contains(field)) {
                for (var item : result) {
                    final String[] parts = entityPath.split(",");
                    final Object[] params = Arrays.stream(parts).map(item::get).toArray(Object[]::new);
                    try {
                        final var computedValue = ProjectionUtils.computeField(instanceResolver, dtoClass, field,
                                params);
                        item.put(field, computedValue);

                        // CLEANUP: si une dépendance d'un champ calculé n'est pas elle-même projetée
                        // par une projection
                        // directe (champ non calculé) alors la retirer du résultat.
                        for (var part : parts) {
                            boolean isDirectlyProjected = entityToDtoMapping.containsKey(part)
                                    && !ctx.projectionSpec.computedFields().contains(entityToDtoMapping.get(part));
                            if (!isDirectlyProjected) {
                                item.remove(part);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return result;
    }

    void cleanupUnprojectedFields() {
        // si une dépendance d'un champ calculé n'est pas elle même projété alors la
        // retirer du résultat
        // Est-ce que la dépendance est projétée par une projection non calculée ? Si
        // oui garder si non retirer

    }

    // ==================== Composite Key Support ====================

    private Object extractCompositeKey(Tuple tuple, List<String> idFields) {
        if (idFields.size() == 1) {
            try {
                return tuple.get(idFields.getFirst());
            } catch (IllegalArgumentException e) {
                // Fallback to internal prefix for subqueries
                return tuple.get(MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE + idFields.getFirst());
            }
        } else {
            List<Object> values = idFields.stream().map(tuple::get).toList();
            return new CompositeKey(values);
        }
    }

    private Object extractCompositeKey(Map<String, Object> map, List<String> idFields) {
        if (idFields.size() == 1) {
            String idField = idFields.getFirst();

            // Check both public and internal versions
            return map.containsKey(idField) ? map.get(idField)
                    : map.get(MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE + idField);
        } else {
            List<Object> values = idFields.stream().map(map::get).toList();
            return new CompositeKey(values);
        }
    }

    private Object extractParentIdFromTuple(Tuple tuple, int fieldCount) {
        String prefix = MultiQueryExecutionPlan.PREFIX_FOR_INTERNAL_USAGE;
        if (fieldCount == 1) {
            return tuple.get(prefix + "parent_id_0");
        } else {
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < fieldCount; i++) {
                values.add(tuple.get(prefix + "parent_id_" + i));
            }
            return new CompositeKey(values);
        }
    }

    private Predicate buildInPredicate(
            CriteriaBuilder cb,
            Path<?> parentRefPath,
            List<String> parentIdFields,
            List<Object> parentIds) {
        if (parentIdFields.size() == 1) {
            Path<?> parentIdPath = parentRefPath.get(parentIdFields.getFirst());
            return parentIdPath.in(parentIds);
        } else {
            List<Predicate> orPredicates = new ArrayList<>();

            for (Object parentId : parentIds) {
                if (parentId instanceof CompositeKey(List<Object> values)) {
                    List<Predicate> andPredicates = new ArrayList<>();

                    for (int i = 0; i < parentIdFields.size(); i++) {
                        Path<?> fieldPath = parentRefPath.get(parentIdFields.get(i));
                        andPredicates.add(cb.equal(fieldPath, values.get(i)));
                    }

                    orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
                }
            }

            return cb.or(orPredicates.toArray(new Predicate[0]));
        }
    }

    /**
     * Record to represent composite (multi-part) keys for parent-child linkage in
     * batch joins.
     *
     * @param values list of values composing the key
     */
    private record CompositeKey(List<Object> values) {
        public CompositeKey {
            values = List.copyOf(values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CompositeKey(List<Object> values1)))
                return false;
            return Objects.equals(values, values1);
        }

        @Override
        public int hashCode() {
            return Objects.hash(values);
        }
    }

    /**
     * Record encapsulating the results of a batch collection query: maps from
     * parent keys to child lists,
     * and an index mapping child keys to child maps for parent-child attachment at
     * later nesting levels.
     *
     * @param childrenByParent map from parent keys to lists of child DTO maps
     * @param childLookupIndex map from child keys to child DTO maps for
     *                         hierarchical lookup
     */
    private record BatchQueryResult(
            Map<Object, List<Map<String, Object>>> childrenByParent,
            Map<Object, Map<String, Object>> childLookupIndex) {
    }

    /**
     * Execution context encapsulating all state required to build and execute a
     * dynamic CriteriaQuery
     * for a specific entity projection.
     * <p>
     * This record provides a complete snapshot of the query construction
     * environment, enabling
     * stateless predicate resolvers, projection transformers, and recursive
     * collection query builders
     * to operate consistently across complex query trees.
     * </p>
     *
     * @param em                the {@link EntityManager} used for query execution
     *                          and entity management
     * @param cb                the {@link CriteriaBuilder} providing factory
     *                          methods for predicates,
     *                          expressions, and query components
     * @param root              the {@link Root} representing the main entity in the
     *                          current query scope
     * @param query             the {@link CriteriaQuery} being constructed, typed
     *                          to {@link Tuple} for
     *                          flexible multi-select result handling
     * @param predicateResolver the {@link PredicateResolver} responsible for
     *                          translating filter expressions
     *                          into JPA predicates against the current root entity
     * @param projectionSpec    the {@link MultiQueryExecutionPlan.ProjectionSpec}
     *                          defining how entity attributes map to
     *                          DTO fields, including computed properties and nested
     *                          projections
     */
    public record QueryContext(EntityManager em,
            CriteriaBuilder cb,
            Root<?> root,
            CriteriaQuery<Tuple> query,
            PredicateResolver<?> predicateResolver,
            MultiQueryExecutionPlan.ProjectionSpec projectionSpec) {
    }
}
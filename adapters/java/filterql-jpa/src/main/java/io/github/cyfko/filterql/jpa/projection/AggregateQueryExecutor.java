package io.github.cyfko.filterql.jpa.projection;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Executes batch aggregate queries for collection-traversing dependencies.
 * <p>
 * Instead of N+1 queries, this class executes a single batch query that
 * computes aggregates for all parent IDs at once.
 * </p>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public class AggregateQueryExecutor {

    /**
     * Maximum number of IDs in a single IN clause.
     * Oracle has a limit of 1000, other databases vary.
     * 500 is a safe default that works well with most query planners.
     */
    private static final int MAX_IN_CLAUSE_SIZE = 500;

    private final EntityManager em;
    private final Class<?> rootEntityClass;
    private final List<String> rootIdFields;

    /**
     * Creates an executor for single-field ID entities.
     */
    public AggregateQueryExecutor(EntityManager em, Class<?> rootEntityClass, String rootIdField) {
        this(em, rootEntityClass, List.of(rootIdField));
    }

    /**
     * Creates an executor for composite ID entities.
     */
    public AggregateQueryExecutor(EntityManager em, Class<?> rootEntityClass, List<String> rootIdFields) {
        this.em = Objects.requireNonNull(em, "EntityManager cannot be null");
        this.rootEntityClass = Objects.requireNonNull(rootEntityClass, "rootEntityClass cannot be null");
        if (rootIdFields == null || rootIdFields.isEmpty()) {
            throw new IllegalArgumentException("rootIdFields cannot be null or empty");
        }
        this.rootIdFields = List.copyOf(rootIdFields);
    }

    /**
     * Executes a batch aggregate query for a collection path with a reducer.
     * Automatically batches large ID sets to avoid database limits.
     *
     * @param collectionPath the path traversing collections (e.g.,
     *                       "departments.budget")
     * @param reducer        the reducer name (SUM, AVG, COUNT, etc.)
     * @param parentIds      the collection of parent entity IDs to aggregate for
     * @return a Map from parentId to aggregated value
     */
    public Map<Object, Number> executeAggregateQuery(
            String collectionPath,
            String reducer,
            Collection<?> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // For large ID sets, batch the queries
        if (parentIds.size() > MAX_IN_CLAUSE_SIZE) {
            return executeBatchedQuery(collectionPath, reducer, parentIds);
        }

        return executeSingleQuery(collectionPath, reducer, parentIds);
    }

    /**
     * Executes batched queries for large ID sets.
     */
    private Map<Object, Number> executeBatchedQuery(
            String collectionPath,
            String reducer,
            Collection<?> parentIds) {

        Map<Object, Number> results = new HashMap<>();
        List<?> idList = parentIds instanceof List<?> ? (List<?>) parentIds : new ArrayList<>(parentIds);

        for (int i = 0; i < idList.size(); i += MAX_IN_CLAUSE_SIZE) {
            int end = Math.min(i + MAX_IN_CLAUSE_SIZE, idList.size());
            List<?> batch = idList.subList(i, end);
            results.putAll(executeSingleQuery(collectionPath, reducer, batch));
        }

        return results;
    }

    /**
     * Executes a single aggregate query for a batch of IDs.
     * Optimized for the common single-ID case.
     */
    private Map<Object, Number> executeSingleQuery(
            String collectionPath,
            String reducer,
            Collection<?> parentIds) {

        // Parse path: "departments.budget" or "departments.teams.employees.salary"
        String[] segments = collectionPath.split("\\.");
        if (segments.length < 2) {
            throw new IllegalArgumentException(
                    "Collection path must have at least 2 segments: " + collectionPath);
        }

        String aggregateField = segments[segments.length - 1];

        // Build the list of entity classes along the path
        List<Class<?>> entityPath = buildEntityPath(segments);
        Class<?> leafEntityClass = entityPath.get(entityPath.size() - 1);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> leafRoot = query.from(leafEntityClass);

        // Build aggregate expression
        Path<Number> aggregatePath = leafRoot.get(aggregateField);
        Expression<Number> aggregateExpr = applyReducer(cb, aggregatePath, reducer);

        // Optimize for single ID (common case) vs composite ID
        if (rootIdFields.size() == 1) {
            return executeSingleIdQuery(cb, query, leafRoot, entityPath, parentIds, aggregateExpr);
        } else {
            return executeCompositeIdQuery(cb, query, leafRoot, entityPath, parentIds, aggregateExpr);
        }
    }

    /**
     * Optimized path for single ID entities (most common case).
     */
    private Map<Object, Number> executeSingleIdQuery(
            CriteriaBuilder cb,
            CriteriaQuery<Tuple> query,
            Root<?> leafRoot,
            List<Class<?>> entityPath,
            Collection<?> parentIds,
            Expression<Number> aggregateExpr) {

        // Build single path to parent ID
        Path<?> parentIdPath = buildSinglePathToParent(leafRoot, entityPath);

        // Simple SELECT and GROUP BY
        query.multiselect(parentIdPath.alias("parentId"), aggregateExpr.alias("aggregateValue"));
        query.where(parentIdPath.in(parentIds));
        query.groupBy(parentIdPath);

        // Execute and build result map
        List<Tuple> results = em.createQuery(query).getResultList();
        Map<Object, Number> resultMap = new HashMap<>();
        for (Tuple tuple : results) {
            resultMap.put(tuple.get("parentId"), tuple.get("aggregateValue", Number.class));
        }
        return resultMap;
    }

    /**
     * Path for composite ID entities.
     */
    private Map<Object, Number> executeCompositeIdQuery(
            CriteriaBuilder cb,
            CriteriaQuery<Tuple> query,
            Root<?> leafRoot,
            List<Class<?>> entityPath,
            Collection<?> parentIds,
            Expression<Number> aggregateExpr) {

        List<Path<?>> parentIdPaths = buildPathsToParent(leafRoot, entityPath);

        // Build SELECT and GROUP BY for composite ID
        List<Selection<?>> selections = new ArrayList<>(parentIdPaths.size() + 1);
        List<Expression<?>> groupByExprs = new ArrayList<>(parentIdPaths.size());

        for (int i = 0; i < parentIdPaths.size(); i++) {
            Path<?> idPath = parentIdPaths.get(i);
            selections.add(idPath.alias("parentId" + i));
            groupByExprs.add(idPath);
        }
        selections.add(aggregateExpr.alias("aggregateValue"));

        query.multiselect(selections);
        query.where(parentIdPaths.get(0).in(parentIds));
        query.groupBy(groupByExprs);

        // Execute and build result map with composite keys
        List<Tuple> results = em.createQuery(query).getResultList();
        Map<Object, Number> resultMap = new HashMap<>();

        for (Tuple tuple : results) {
            List<Object> compositeKey = new ArrayList<>(parentIdPaths.size());
            for (int i = 0; i < parentIdPaths.size(); i++) {
                compositeKey.add(tuple.get("parentId" + i));
            }
            resultMap.put(compositeKey, tuple.get("aggregateValue", Number.class));
        }
        return resultMap;
    }

    /**
     * Builds single path for simple ID (optimized, no List allocation).
     */
    private Path<?> buildSinglePathToParent(Root<?> leafRoot, List<Class<?>> entityPath) {
        Path<?> currentPath = leafRoot;

        for (int i = entityPath.size() - 1; i > 0; i--) {
            Class<?> childClass = entityPath.get(i);
            Class<?> parentClass = entityPath.get(i - 1);

            String parentRefField = findManyToOneField(childClass, parentClass);
            if (parentRefField == null) {
                throw new IllegalStateException(
                        "No @ManyToOne field found in " + childClass.getSimpleName() +
                                " pointing to " + parentClass.getSimpleName());
            }
            currentPath = currentPath.get(parentRefField);
        }

        return currentPath.get(rootIdFields.get(0));
    }

    /**
     * Builds the list of entity classes along the collection path.
     */
    private List<Class<?>> buildEntityPath(String[] segments) {
        List<Class<?>> path = new ArrayList<>();
        path.add(rootEntityClass);

        Class<?> currentClass = rootEntityClass;
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            try {
                Field field = findField(currentClass, segment);
                Class<?> fieldType = field.getType();

                if (Collection.class.isAssignableFrom(fieldType)) {
                    var genericType = field.getGenericType();
                    if (genericType instanceof java.lang.reflect.ParameterizedType pt) {
                        currentClass = (Class<?>) pt.getActualTypeArguments()[0];
                    }
                } else {
                    currentClass = fieldType;
                }
                path.add(currentClass);
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(
                        "Cannot find field '" + segment + "' in " + currentClass.getName());
            }
        }

        return path;
    }

    /**
     * Builds paths from leaf entity back to root parent ID fields.
     * Returns multiple paths for composite IDs.
     */
    private List<Path<?>> buildPathsToParent(Root<?> leafRoot, List<Class<?>> entityPath) {
        Path<?> currentPath = leafRoot;

        // Navigate backwards through the entity path
        // entityPath = [Company, Department] for "departments.budget"
        // From Department, find the @ManyToOne field pointing to Company
        for (int i = entityPath.size() - 1; i > 0; i--) {
            Class<?> childClass = entityPath.get(i);
            Class<?> parentClass = entityPath.get(i - 1);

            String parentRefField = findManyToOneField(childClass, parentClass);
            if (parentRefField == null) {
                throw new IllegalStateException(
                        "No @ManyToOne field found in " + childClass.getSimpleName() +
                                " pointing to " + parentClass.getSimpleName());
            }
            currentPath = currentPath.get(parentRefField);
        }

        // Get all ID fields of the root parent
        List<Path<?>> idPaths = new ArrayList<>(rootIdFields.size());
        for (String idField : rootIdFields) {
            idPaths.add(currentPath.get(idField));
        }
        return idPaths;
    }

    /**
     * Finds the @ManyToOne field in childClass that references parentClass.
     */
    private String findManyToOneField(Class<?> childClass, Class<?> parentClass) {
        Class<?> current = childClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(ManyToOne.class) &&
                        field.getType().equals(parentClass)) {
                    return field.getName();
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Applies the reducer function.
     */
    @SuppressWarnings("unchecked")
    private Expression<Number> applyReducer(CriteriaBuilder cb, Path<Number> path, String reducer) {
        return switch (reducer.toUpperCase()) {
            case "SUM" -> cb.sum(path);
            case "AVG" -> (Expression<Number>) (Expression<?>) cb.avg(path);
            case "COUNT" -> (Expression<Number>) (Expression<?>) cb.count(path);
            case "COUNT_DISTINCT" -> (Expression<Number>) (Expression<?>) cb.countDistinct(path);
            case "MIN" -> cb.min(path);
            case "MAX" -> cb.max(path);
            default -> throw new IllegalArgumentException("Unsupported reducer: " + reducer);
        };
    }

    /**
     * Finds a field in a class hierarchy.
     */
    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}

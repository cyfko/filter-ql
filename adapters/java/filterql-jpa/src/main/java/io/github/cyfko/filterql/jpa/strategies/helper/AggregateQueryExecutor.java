package io.github.cyfko.filterql.jpa.strategies.helper;

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
 * <p>
 * Uses {@link IdPredicateBuilder} for optimized ID predicate construction,
 * supporting both simple and composite IDs with automatic batching.
 * </p>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public class AggregateQueryExecutor {

    private final EntityManager em;
    private final Class<?> rootEntityClass;
    private final List<String> rootIdFields;
    private final IdPredicateBuilder predicateBuilder;

    /**
     * Creates an executor for single-field ID entities.
     */
    public AggregateQueryExecutor(EntityManager em, Class<?> rootEntityClass, String rootIdField) {
        this(em, rootEntityClass, List.of(rootIdField), IdPredicateBuilder.defaultBuilder());
    }

    /**
     * Creates an executor for composite ID entities.
     */
    public AggregateQueryExecutor(EntityManager em, Class<?> rootEntityClass, List<String> rootIdFields) {
        this(em, rootEntityClass, rootIdFields, IdPredicateBuilder.defaultBuilder());
    }

    /**
     * Creates an executor with a custom predicate builder (for testing or
     * DB-specific optimization).
     */
    public AggregateQueryExecutor(EntityManager em, Class<?> rootEntityClass,
            List<String> rootIdFields, IdPredicateBuilder predicateBuilder) {
        this.em = Objects.requireNonNull(em, "EntityManager cannot be null");
        this.rootEntityClass = Objects.requireNonNull(rootEntityClass, "rootEntityClass cannot be null");
        if (rootIdFields == null || rootIdFields.isEmpty()) {
            throw new IllegalArgumentException("rootIdFields cannot be null or empty");
        }
        this.rootIdFields = List.copyOf(rootIdFields);
        this.predicateBuilder = Objects.requireNonNull(predicateBuilder, "predicateBuilder cannot be null");
    }

    /**
     * Executes a batch aggregate query for a collection path with a reducer.
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

        // Build JPA query
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> leafRoot = query.from(leafEntityClass);

        // Build paths to parent IDs
        List<Path<?>> parentIdPaths = buildPathsToParent(leafRoot, entityPath);

        // Build aggregate expression
        Path<Number> aggregatePath = leafRoot.get(aggregateField);
        Expression<Number> aggregateExpr = applyReducer(cb, aggregatePath, reducer);

        // Build SELECT with ID fields and aggregate
        List<Selection<?>> selections = new ArrayList<>(parentIdPaths.size() + 1);
        for (int i = 0; i < parentIdPaths.size(); i++) {
            selections.add(parentIdPaths.get(i).alias("parentId" + i));
        }
        selections.add(aggregateExpr.alias("aggregateValue"));
        query.multiselect(selections);

        // Build WHERE using IdPredicateBuilder (handles batching + composite keys)
        Predicate wherePredicate = predicateBuilder.buildIdPredicate(cb, parentIdPaths, parentIds);
        query.where(wherePredicate);

        // GROUP BY all ID fields
        List<Expression<?>> groupByExprs = new ArrayList<>(parentIdPaths);
        query.groupBy(groupByExprs);

        // Execute and build result map
        List<Tuple> results = em.createQuery(query).getResultList();
        return buildResultMap(results, parentIdPaths.size());
    }

    /**
     * Builds the result map from query results.
     */
    private Map<Object, Number> buildResultMap(List<Tuple> results, int idFieldCount) {
        Map<Object, Number> resultMap = new HashMap<>();

        for (Tuple tuple : results) {
            Object key;
            if (idFieldCount == 1) {
                // Simple ID
                key = tuple.get("parentId0");
            } else {
                // Composite ID - use List as key
                List<Object> compositeKey = new ArrayList<>(idFieldCount);
                for (int i = 0; i < idFieldCount; i++) {
                    compositeKey.add(tuple.get("parentId" + i));
                }
                key = compositeKey;
            }
            resultMap.put(key, tuple.get("aggregateValue", Number.class));
        }

        return resultMap;
    }

    // ==================== Path Building ====================

    /**
     * Builds paths from leaf entity back to root parent ID fields.
     */
    private List<Path<?>> buildPathsToParent(Root<?> leafRoot, List<Class<?>> entityPath) {
        Path<?> currentPath = leafRoot;

        // Navigate backwards through the entity path
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

    // ==================== Utility Methods ====================

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

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

    private final EntityManager em;
    private final Class<?> rootEntityClass;
    private final String rootIdField;

    public AggregateQueryExecutor(EntityManager em, Class<?> rootEntityClass, String rootIdField) {
        this.em = Objects.requireNonNull(em, "EntityManager cannot be null");
        this.rootEntityClass = Objects.requireNonNull(rootEntityClass, "rootEntityClass cannot be null");
        this.rootIdField = Objects.requireNonNull(rootIdField, "rootIdField cannot be null");
    }

    /**
     * Executes a batch aggregate query for a collection path with a reducer.
     *
     * @param collectionPath the path traversing collections (e.g.,
     *                       "departments.budget")
     * @param reducer        the reducer name (SUM, AVG, COUNT, etc.)
     * @param parentIds      the list of parent entity IDs to aggregate for
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

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> leafRoot = query.from(leafEntityClass);

        // Build path back to parent using actual @ManyToOne field names
        Path<?> parentIdPath = buildPathToParent(leafRoot, entityPath);

        // Build aggregate expression
        Path<Number> aggregatePath = leafRoot.get(aggregateField);
        Expression<Number> aggregateExpr = applyReducer(cb, aggregatePath, reducer);

        // SELECT parent_id, AGGREGATE(field) GROUP BY parent_id WHERE parent_id IN
        // (...)
        query.multiselect(parentIdPath.alias("parentId"), aggregateExpr.alias("aggregateValue"));
        query.where(parentIdPath.in(parentIds));
        query.groupBy(parentIdPath);

        // Execute and build result map
        List<Tuple> results = em.createQuery(query).getResultList();
        Map<Object, Number> resultMap = new HashMap<>();
        for (Tuple tuple : results) {
            Object parentId = tuple.get("parentId");
            Number value = tuple.get("aggregateValue", Number.class);
            resultMap.put(parentId, value);
        }

        return resultMap;
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
     * Builds path from leaf entity back to root parent.
     * Uses @ManyToOne annotations to find the actual parent reference field.
     */
    private Path<?> buildPathToParent(Root<?> leafRoot, List<Class<?>> entityPath) {
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

        // Get the ID field of the root parent
        return currentPath.get(rootIdField);
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

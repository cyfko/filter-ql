package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.projection.ProjectionFieldParser;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.projection.FieldSchema;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import io.github.cyfko.filterql.jpa.projection.RowBuffer;
import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.ProjectionRegistry;
import io.github.cyfko.projection.metamodel.model.PersistenceMetadata;
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
    public final <Context> List<RowBuffer> execute(Context ctx, PredicateResolver<?> pr, QueryExecutionParams params) {

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

}

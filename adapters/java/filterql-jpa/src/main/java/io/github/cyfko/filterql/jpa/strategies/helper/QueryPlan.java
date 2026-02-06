package io.github.cyfko.filterql.jpa.strategies.helper;

import io.github.cyfko.filterql.core.projection.ProjectionFieldParser;
import io.github.cyfko.jpametamodel.PersistenceRegistry;
import io.github.cyfko.jpametamodel.ProjectionRegistry;
import io.github.cyfko.jpametamodel.api.PersistenceMetadata;
import io.github.cyfko.jpametamodel.api.DirectMapping;
import io.github.cyfko.jpametamodel.api.ProjectionMetadata;

import java.util.*;
import java.util.stream.Collectors;

public class QueryPlan {
    public static final String PREFIX_FOR_INTERNAL_USAGE = "_i_";
    public static final String PREFIX_FOR_COMPUTED = "_c_";
    public static final String SUFFIX_PARENT_ID = "pid_";


    private final Class<?> entityClass;
    private final FieldSchema schema;
    private final List<String> idFields;
    private final Map<String, QueryPlan>[] collectionPlans;

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
        private final Map<Integer, Map<String, QueryPlan.Builder>> collectionPlans = new TreeMap<>();

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
                schemaBuilder.addField(idField, idField, FieldSchema.FieldStatus.SQL_ONLY);
            }

            // Add parent ID fields for collection plans (enables parent-child linking)
            if (parentEntityClass != null && parentReferenceField != null) {
                List<String> parentIdFields = PersistenceRegistry.getIdFields(parentEntityClass);
                for (int i = 0; i < parentIdFields.size(); i++) {
                    schemaBuilder.addField(
                            parentReferenceField + "." + parentIdFields.get(i),
                            PREFIX_FOR_INTERNAL_USAGE + SUFFIX_PARENT_ID + i,
                            FieldSchema.FieldStatus.SQL_ONLY);
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

        private void add(Class<?> dtoClass,
                         ProjectionFieldParser.ProjectionField pf,
                         String[] segments,
                         int index) {
            ProjectionMetadata pm = ProjectionRegistry.getMetadataFor(dtoClass);

            if (pf.prefix().isEmpty() || index >= segments.length) { // Add scalar fields
                for (String field : pf.fields()) {
                    String dtoPath = pf.prefix().isEmpty() ? field : pf.prefix() + "." + field;

                    pm.getComputedField(field, true).ifPresentOrElse(
                            computedField -> schemaBuilder.addComputedField(computedField, dtoPath),
                            () -> {

                                DirectMapping dm = pm.getDirectMapping(field, true)
                                        .orElseThrow(() -> new IllegalStateException("Should never reach here"));

                                if (dm.collection().isPresent()) {
                                    // Add all direct fields of this collection (not inner collections)
                                    Set<String> defaultFields = defaultProjection(dm.dtoFieldType());
                                    for (String defaultField : defaultFields) {
                                        var parsed = new ProjectionFieldParser.ProjectionField(dtoPath, List.of(defaultField));
                                        this.add(parsed);
                                    }

                                    return;
                                }

                                String entityPath = ProjectionRegistry.toEntityPath(dtoPath, this.projectionClass, true);
                                schemaBuilder.addField(entityPath, dtoPath, FieldSchema.FieldStatus.SQL);
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
                // Create relative segments for the collection (segments after the collection
                // element)
                String[] relativeSegments = java.util.Arrays.copyOfRange(segments, index + 1, segments.length);
                // Create a new pf with relative prefix for the collection builder
                String relativePrefix = String.join(".", relativeSegments);
                var relativePf = new ProjectionFieldParser.ProjectionField(relativePrefix, pf.fields());
                builder.add(dm.dtoFieldType(), relativePf, relativeSegments, 0);

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

    // ==================== Utility Methods ====================

    /**
     * Return a chosen set of fields projected as default when no specific projected fields are defined for
     * the projection {@code dtoClass}
     * @param dtoClass projection class.
     * @return a {@link Set} of defaut projected fields excluding collections.
     */
    public static Set<String> defaultProjection(Class<?> dtoClass) {
        Set<String> dtoProjection = new HashSet<>();
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
        return dtoProjection;
    }
}
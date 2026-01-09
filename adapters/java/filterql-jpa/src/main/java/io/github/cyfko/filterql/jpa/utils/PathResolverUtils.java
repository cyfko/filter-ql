package io.github.cyfko.filterql.jpa.utils;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import io.github.cyfko.projection.metamodel.PersistenceRegistry;
import io.github.cyfko.projection.metamodel.model.PersistenceMetadata;

import java.util.*;

/**
 * Utility providing path resolution for nested entity properties with compile-time metadata support.
 * <p>
 * This class facilitates navigation through nested entities by splitting a dot notation path
 * (e.g., "fieldA.fieldB.listField.fieldC") into successive joins. It leverages the compile-time
 * metadata registry to avoid reflection and provide better performance and type safety.
 * </p>
 *
 * <h2>Key improvements over reflection-based approach:</h2>
 * <ul>
 *   <li><strong>Performance:</strong> No reflection at runtime - metadata is pre-computed at compile time</li>
 *   <li><strong>Type Safety:</strong> Collection types and element kinds are known from metadata</li>
 *   <li><strong>Bidirectional Detection:</strong> Automatically identifies bidirectional relationships</li>
 *   <li><strong>Order Detection:</strong> Knows if collections are ordered (List) or not (Set)</li>
 *   <li><strong>Robustness:</strong> Compile-time validation ensures entities are properly annotated</li>
 * </ul>
 *
 * <h2>Usage example:</h2>
 * <pre>{@code
 * CriteriaBuilder cb = entityManager.getCriteriaBuilder();
 * CriteriaQuery<MyEntity> query = cb.createQuery(MyEntity.class);
 * Root<MyEntity> root = query.from(MyEntity.class);
 *
 * // Resolve with metadata
 * PathResolutionMetadata meta = PathResolverUtils.resolvePathWithMeta(root, "orders.items.product.name");
 *
 * // Access metadata
 * Path<?> path = meta.finalPath();
 * List<String> collections = meta.collectionSegments(); // ["orders", "items"]
 * boolean hasCollections = meta.hasCollections(); // true
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
public class PathResolverUtils {

    /**
     * Cache of joins already created for a given Root.
     * Key: Root instance, Value: Map<path, PathResolutionMetadata>
     */
    private static final Map<Root<?>, Map<String, PathResolutionMetadata>> ROOT_CACHE = new WeakHashMap<>();

    /**
     * Utility class constructor.
     */
    private PathResolverUtils() {
        throw new UnsupportedOperationException("PathResolverUtils is a utility class and cannot be instantiated");
    }

    /**
     * Clears the join cache. Useful if you want to reuse PathResolverUtils for a new query.
     */
    public static void clearCache() {
        ROOT_CACHE.clear();
    }

    /**
     * Resolves the given path into a JPA {@link Path} from the provided entity root.
     * <p>
     * This is a convenience method that calls {@link #resolvePathWithMeta(Root, String)} and returns only the path.
     * </p>
     *
     * @param <T> the type of the root entity
     * @param root the root of the JPA Criteria query
     * @param path the full property path in dot notation (e.g., "fieldA.fieldB.listField.fieldC")
     * @return a {@link Path} instance corresponding to the resolved path
     * @throws IllegalArgumentException if path is invalid or a segment is not found
     * @throws IllegalStateException if metadata is not available for an entity
     */
    public static <T> Path<?> resolvePath(Root<T> root, String path) {
        return resolvePathWithMeta(root, path).finalPath();
    }

    /**
     * Resolves the given path with metadata support from the compile-time registry.
     * <p>
     * This method leverages the {@link PersistenceRegistry} to avoid reflection and provide
     * rich metadata about the resolved path, including:
     * </p>
     * <ul>
     *   <li>Collection segments (for proper grouping in projections)</li>
     *   <li>Collection types (List, Set, etc.)</li>
     *   <li>Collection element kinds (Scalar, Embeddable, Entity)</li>
     *   <li>Bidirectional relationship information</li>
     * </ul>
     *
     * <h2>Example</h2>
     * <pre>{@code
     * // Path: "orders.items.product.name"
     * PathResolutionMetadata meta = PathResolverUtils.resolvePathWithMeta(root, "orders.items.product.name");
     *
     * // Access resolved path
     * Path<?> path = meta.finalPath(); // JPA Path to product.name
     *
     * // Access collection metadata
     * List<String> collections = meta.collectionSegments(); // ["orders", "items"]
     * CollectionInfo ordersInfo = meta.getCollectionInfo("orders");
     * if (ordersInfo != null) {
     *     System.out.println("Orders is a " + ordersInfo.collectionType()); // LIST
     *     System.out.println("Contains: " + ordersInfo.elementKind()); // ENTITY
     * }
     * }</pre>
     *
     * @param <T> the type of the root entity
     * @param root the root of the JPA Criteria query
     * @param path the full property path in dot notation
     * @return metadata object containing the resolved path and collection information
     * @throws IllegalArgumentException if path is invalid or a segment is not found
     * @throws IllegalStateException if metadata is not available for an entity
     * @since 2.0.0
     */
    public static <T> PathResolutionMetadata resolvePathWithMeta(Root<T> root, String path) {
        if (root == null) {
            throw new IllegalArgumentException("Root cannot be null");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null or blank");
        }

        // Check cache first
        Map<String, PathResolutionMetadata> rootCache = ROOT_CACHE.computeIfAbsent(root, r -> new HashMap<>());
        if (rootCache.containsKey(path)) {
            return rootCache.get(path);
        }

        // Resolve path using metadata
        PathResolutionMetadata metadata = resolvePathInternal(root, path);
        rootCache.put(path, metadata);

        return metadata;
    }

    /**
     * Internal path resolution using metadata registry.
     */
    private static <T> PathResolutionMetadata resolvePathInternal(Root<T> root, String path) {
        String[] segments = path.split("\\.");
        From<?, ?> currentFrom = root;
        Class<?> currentClass = root.getJavaType();

        List<String> allSegments = new ArrayList<>();
        List<String> embeddablePath = new ArrayList<>(); // Accumulate embeddable segments
        Map<String, CollectionInfo> collectionInfoMap = new LinkedHashMap<>();

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            allSegments.add(segment);

            // Get metadata for current entity
            Map<String, PersistenceMetadata> entityMetadata = PersistenceRegistry.getMetadataFor(currentClass);
            if (entityMetadata == null) {
                throw new IllegalStateException(
                        "No metadata found for entity: " + currentClass.getName() +
                                ". Ensure the annotation processor has been run."
                );
            }

            // Get field metadata
            PersistenceMetadata fieldMetadata = entityMetadata.get(segment);
            if (fieldMetadata == null) {
                throw new IllegalArgumentException(
                        String.format("Field '%s' not found in entity %s", segment, currentClass.getSimpleName())
                );
            }

            // Handle collections
            if (fieldMetadata.isCollection()) {
                // Extract collection info from metadata
                CollectionInfo collectionInfo = extractCollectionInfo(fieldMetadata);
                collectionInfoMap.put(segment, collectionInfo);

                // Perform join if not the last segment
                if (i < segments.length - 1) {
                    currentFrom = joinOnce(currentFrom, segment);
                    embeddablePath.clear(); // Reset embeddable path after join

                    // Get next class from metadata
                    currentClass = fieldMetadata.relatedType();
                }
            }
            // Handle regular fields and embeddables
            else if (i < segments.length - 1) {
                // Not a collection but not the last segment - must be a relationship or embeddable
                Class<?> relatedClass = fieldMetadata.relatedType();
                if (PersistenceRegistry.isEntityRegistered(relatedClass)) {
                    // It's an ENTITY → perform JOIN and reset embeddable path
                    currentFrom = joinOnce(currentFrom, segment);
                    embeddablePath.clear(); // Important: clear after join
                    currentClass = relatedClass;
                } else if (PersistenceRegistry.isEmbeddableRegistered(relatedClass)) {
                    // It's an EMBEDDABLE → NO JOIN, just accumulate in path
                    // JPA Criteria API handles embeddables via .get() chaining
                    embeddablePath.add(segment);
                    currentClass = relatedClass;
                } else {
                    throw new IllegalArgumentException(
                            String.format("Cannot navigate through non-relationship field '%s' in %s",
                                    segment, currentClass.getSimpleName())
                    );
                }
            }
        }

        // Get final path - chain through embeddable segments if any
        String lastSegment = segments[segments.length - 1];
        Path<?> finalPath = currentFrom;

        // Navigate through accumulated embeddable segments
        for (String embeddableSegment : embeddablePath) {
            finalPath = finalPath.get(embeddableSegment);
        }

        // Add the final segment
        finalPath = finalPath.get(lastSegment);

        return new PathResolutionMetadata(
                finalPath,
                List.copyOf(allSegments),
                List.copyOf(collectionInfoMap.keySet()),
                Map.copyOf(collectionInfoMap)
        );
    }

    /**
     * Builds the final path by chaining .get() calls.
     */
    private static Path<?> buildPath(From<?, ?> from, List<String> embeddableSegments, String finalSegment) {
        Path<?> path = from;

        // Chain through embeddable segments
        for (String segment : embeddableSegments) {
            path = path.get(segment);
        }

        // Add final segment
        return path.get(finalSegment);
    }

    /**
     * Extracts collection information from field metadata.
     */
    private static CollectionInfo extractCollectionInfo(PersistenceMetadata fieldMetadata) {
        return fieldMetadata.collection()
                .map(cm -> new CollectionInfo(
                        cm.collectionType(),
                        cm.kind(),
                        cm.mappedBy().orElse(null),
                        cm.orderBy().orElse(null)
                ))
                .orElseThrow(() -> new IllegalStateException("Collection metadata not available"));
    }

    /**
     * Join the attribute only once per From, avoids duplicate joins.
     */
    private static From<?, ?> joinOnce(From<?, ?> from, String attribute) {
        return from.getJoins().stream()
                .filter(j -> j.getAttribute().getName().equals(attribute))
                .findFirst()
                .map(j -> (From<?, ?>) j)
                .orElseGet(() -> from.join(attribute, JoinType.LEFT));
    }

    /**
     * Metadata about a resolved JPA path, including rich collection information.
     * <p>
     * This record extends the basic path resolution with compile-time metadata about collections,
     * enabling advanced features like:
     * </p>
     * <ul>
     *   <li>Proper grouping in projections</li>
     *   <li>Bidirectional relationship detection</li>
     *   <li>Collection type awareness (List vs Set)</li>
     *   <li>Order by clause handling</li>
     * </ul>
     *
     * @param finalPath the resolved JPA path for use in selections
     * @param allSegments all path segments in order
     * @param collectionSegments list of segment names that are collections
     * @param collectionInfos detailed information about each collection segment
     * @since 2.0.0
     */
    public record PathResolutionMetadata(
            Path<?> finalPath,
            List<String> allSegments,
            List<String> collectionSegments,
            Map<String, CollectionInfo> collectionInfos
    ) {
        public PathResolutionMetadata {
            Objects.requireNonNull(finalPath, "finalPath cannot be null");
            Objects.requireNonNull(allSegments, "allSegments cannot be null");
            Objects.requireNonNull(collectionSegments, "collectionSegments cannot be null");
            Objects.requireNonNull(collectionInfos, "collectionInfos cannot be null");
        }

        /**
         * Checks if the path contains any collections.
         */
        public boolean hasCollections() {
            return !collectionSegments.isEmpty();
        }

        /**
         * Gets collection information for a specific segment.
         *
         * @param segment the segment name
         * @return collection info if the segment is a collection, null otherwise
         */
        public CollectionInfo getCollectionInfo(String segment) {
            return collectionInfos.get(segment);
        }

        /**
         * Checks if a specific segment is a collection.
         */
        public boolean isCollection(String segment) {
            return collectionSegments.contains(segment);
        }

        /**
         * Checks if any collection in the path is bidirectional.
         */
        public boolean hasBidirectionalCollections() {
            return collectionInfos.values().stream()
                    .anyMatch(CollectionInfo::isBidirectional);
        }

        /**
         * Checks if any collection in the path is ordered (List).
         */
        public boolean hasOrderedCollections() {
            return collectionInfos.values().stream()
                    .anyMatch(CollectionInfo::isOrdered);
        }
    }

    /**
     * Detailed information about a collection field extracted from compile-time metadata.
     *
     * @param collectionType the type of collection (LIST, SET, MAP, etc.)
     * @param elementKind the kind of elements (SCALAR, EMBEDDABLE, ENTITY)
     * @param mappedBy the mappedBy attribute if bidirectional, null otherwise
     * @param orderBy the orderBy clause if specified, null otherwise
     * @since 2.0.0
     */
    public record CollectionInfo(
            io.github.cyfko.projection.metamodel.model.CollectionType collectionType,
            io.github.cyfko.projection.metamodel.model.CollectionKind elementKind,
            String mappedBy,
            String orderBy
    ) {
        /**
         * Checks if this is a bidirectional relationship.
         */
        public boolean isBidirectional() {
            return mappedBy != null && !mappedBy.isBlank();
        }

        /**
         * Checks if this collection is ordered (List type).
         */
        public boolean isOrdered() {
            return collectionType == io.github.cyfko.projection.metamodel.model.CollectionType.LIST;
        }

        /**
         * Checks if this collection contains entities.
         */
        public boolean isEntityCollection() {
            return elementKind == io.github.cyfko.projection.metamodel.model.CollectionKind.ENTITY;
        }

        /**
         * Checks if this collection contains scalars.
         */
        public boolean isScalarCollection() {
            return elementKind == io.github.cyfko.projection.metamodel.model.CollectionKind.SCALAR;
        }

        /**
         * Checks if this collection contains embeddables.
         */
        public boolean isEmbeddableCollection() {
            return elementKind == io.github.cyfko.projection.metamodel.model.CollectionKind.EMBEDDABLE;
        }
    }
}


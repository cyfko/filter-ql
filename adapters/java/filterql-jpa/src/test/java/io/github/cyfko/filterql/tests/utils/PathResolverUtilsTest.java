package io.github.cyfko.filterql.tests.utils;

import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.filterql.tests.entities.relationship.ChildEntity;
import io.github.cyfko.filterql.tests.entities.ecommerce.Order;
import io.github.cyfko.filterql.tests.entities.relationship.ParentEntity;
import io.github.cyfko.filterql.tests.entities.ecommerce.User;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PathResolverUtils using real JPA entities.
 * These tests require the annotation processor to have run on the test entities.
 */
class PathResolverUtilsTest {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private CriteriaBuilder cb;

    @BeforeAll
    static void init() {
        emf = Persistence.createEntityManagerFactory("testPU");
    }

    @AfterAll
    static void close() {
        if (emf != null) emf.close();
    }

    @BeforeEach
    void setup() {
        em = emf.createEntityManager();
        cb = em.getCriteriaBuilder();
        PathResolverUtils.clearCache();
    }

    @AfterEach
    void teardown() {
        if (em != null) em.close();
    }

    // ==================== Simple Path Tests ====================

    @Test
    void testSimpleField() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "id");

        assertNotNull(meta.finalPath());
        assertFalse(meta.hasCollections());
        assertEquals(List.of("id"), meta.allSegments());
        assertTrue(meta.collectionSegments().isEmpty());
    }

    @Test
    void testSimpleFieldWithConvenienceMethod() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        Path<?> path = PathResolverUtils.resolvePath(root, "name");

        assertNotNull(path);
        assertNotNull(path.getModel());
    }

    // ==================== Nested Path Tests ====================

    @Test
    void testNestedField() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "user.name");

        assertNotNull(meta.finalPath());
        assertFalse(meta.hasCollections());
        assertEquals(List.of("user", "name"), meta.allSegments());
    }

    @Test
    void testDeeplyNestedField() {
        Root<ChildEntity> root = cb.createQuery(ChildEntity.class).from(ChildEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "entity.user.email");

        assertNotNull(meta.finalPath());
        assertFalse(meta.hasCollections());
        assertEquals(List.of("entity", "user", "email"), meta.allSegments());
    }

    // ==================== Embeddable Tests ====================

    @Test
    void testEmbeddableField() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "address");

        assertNotNull(meta.finalPath());
        assertFalse(meta.hasCollections());
        assertEquals(List.of("address"), meta.allSegments());
    }

    @Test
    void testEmbeddableFieldNavigation() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        // ✅ Navigation through embeddable avec PathResolverUtils
        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "address.city");

        assertNotNull(meta.finalPath());
        assertFalse(meta.hasCollections());
        assertEquals(List.of("address", "city"), meta.allSegments());
    }

    @Test
    void testEmbeddableWithMultipleFields() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        // Test différents champs de l'embeddable
        PathResolverUtils.PathResolutionMetadata metaCity =
                PathResolverUtils.resolvePathWithMeta(root, "address.city");
        PathResolverUtils.PathResolutionMetadata metaStreet =
                PathResolverUtils.resolvePathWithMeta(root, "address.street");
        PathResolverUtils.PathResolutionMetadata metaZip =
                PathResolverUtils.resolvePathWithMeta(root, "address.zipCode");

        assertNotNull(metaCity.finalPath());
        assertNotNull(metaStreet.finalPath());
        assertNotNull(metaZip.finalPath());

        // Pas de collections dans ces paths
        assertFalse(metaCity.hasCollections());
        assertFalse(metaStreet.hasCollections());
        assertFalse(metaZip.hasCollections());
    }

    @Test
    void testComplexPathWithEmbeddableAndCollections() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        // Supposons que TestChild a aussi un embeddable
        // children (collection) → entity (relation) → address (embeddable) → city (scalar)
        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "children.entity.address.city");

        assertNotNull(meta.finalPath());
        assertTrue(meta.hasCollections());
        assertEquals(List.of("children", "entity", "address", "city"), meta.allSegments());
        assertEquals(List.of("children"), meta.collectionSegments());
    }

    // ==================== Collection Tests ====================

    @Test
    void testSimpleCollection() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "children.name");

        assertNotNull(meta.finalPath());
        assertTrue(meta.hasCollections());
        assertEquals(List.of("children", "name"), meta.allSegments());
        assertEquals(List.of("children"), meta.collectionSegments());
    }

    @Test
    void testCollectionMetadata() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "children");

        assertTrue(meta.hasCollections());

        PathResolverUtils.CollectionInfo info = meta.getCollectionInfo("children");
        assertNotNull(info);
        assertTrue(info.isEntityCollection());
        assertTrue(info.isOrdered()); // List
        assertTrue(info.isBidirectional()); // mappedBy = "entity"
        assertEquals("entity", info.mappedBy());
    }

    @Test
    void testElementCollection() {
        Root<User> root = cb.createQuery(User.class).from(User.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "tags");

        assertTrue(meta.hasCollections());

        PathResolverUtils.CollectionInfo info = meta.getCollectionInfo("tags");
        assertNotNull(info);
        assertTrue(info.isScalarCollection()); // Set<String>
        assertFalse(info.isOrdered()); // Set, not List
    }

    @Test
    void testDeepNestedCollections() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "children.user.name");

        assertNotNull(meta.finalPath());
        assertTrue(meta.hasCollections());
        assertEquals(List.of("children", "user", "name"), meta.allSegments());
        assertEquals(List.of("children"), meta.collectionSegments());
    }

    @Test
    void testMultipleCollectionsInPath() {
        Root<User> root = cb.createQuery(User.class).from(User.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "orders.items.product.name");

        assertNotNull(meta.finalPath());
        assertTrue(meta.hasCollections());
        assertEquals(List.of("orders", "items", "product", "name"), meta.allSegments());
        assertEquals(List.of("orders", "items"), meta.collectionSegments());

        // Check both collections
        assertTrue(meta.isCollection("orders"));
        assertTrue(meta.isCollection("items"));
        assertFalse(meta.isCollection("product"));
    }

    // ==================== Collection Metadata Details ====================

    @Test
    void testBidirectionalCollectionDetection() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "children");

        assertTrue(meta.hasBidirectionalCollections());

        PathResolverUtils.CollectionInfo info = meta.getCollectionInfo("children");
        assertTrue(info.isBidirectional());
        assertEquals("entity", info.mappedBy());
    }

    @Test
    void testOrderedCollectionDetection() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "children");

        assertTrue(meta.hasOrderedCollections());

        PathResolverUtils.CollectionInfo info = meta.getCollectionInfo("children");
        assertTrue(info.isOrdered());
    }

    @Test
    void testOrderByClause() {
        Root<Order> root = cb.createQuery(Order.class).from(Order.class);

        PathResolverUtils.PathResolutionMetadata meta =
                PathResolverUtils.resolvePathWithMeta(root, "items");

        PathResolverUtils.CollectionInfo info = meta.getCollectionInfo("items");
        assertNotNull(info.orderBy());
        assertEquals("quantity DESC", info.orderBy());
    }

    // ==================== Invalid Path Tests ====================

    @Test
    void testNullRoot() {
        assertThrows(IllegalArgumentException.class,
                () -> PathResolverUtils.resolvePath(null, "name"));
    }

    @Test
    void testNullPath() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        assertThrows(IllegalArgumentException.class,
                () -> PathResolverUtils.resolvePath(root, null));
    }

    @Test
    void testBlankPath() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        assertThrows(IllegalArgumentException.class,
                () -> PathResolverUtils.resolvePath(root, ""));
    }

    @Test
    void testNonExistentField() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PathResolverUtils.resolvePath(root, "nonExistent"));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void testNonExistentNestedField() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        assertThrows(IllegalArgumentException.class,
                () -> PathResolverUtils.resolvePath(root, "user.nonExistent"));
    }

    @Test
    void testInvalidNestedPath() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        // Cannot navigate through scalar field
        assertThrows(IllegalArgumentException.class,
                () -> PathResolverUtils.resolvePath(root, "name.something"));
    }

    // ==================== Cache Tests ====================

    @Test
    void testSamePathReusesCache() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta1 =
                PathResolverUtils.resolvePathWithMeta(root, "children.user.name");
        PathResolverUtils.PathResolutionMetadata meta2 =
                PathResolverUtils.resolvePathWithMeta(root, "children.user.name");

        // Should return same instance from cache
        assertSame(meta1, meta2);
        assertSame(meta1.finalPath(), meta2.finalPath());
    }

    @Test
    void testDifferentPathsUseSameJoins() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta1 =
                PathResolverUtils.resolvePathWithMeta(root, "children.user.name");
        PathResolverUtils.PathResolutionMetadata meta2 =
                PathResolverUtils.resolvePathWithMeta(root, "children.user.email");

        // Different final paths but shared segments
        assertNotSame(meta1.finalPath(), meta2.finalPath());
        assertEquals(meta1.collectionSegments(), meta2.collectionSegments());

        // Should reuse the same joins (verify through join count)
        int joinCount = root.getJoins().size();
        assertTrue(joinCount > 0, "Should have created joins");
    }

    @Test
    void testCacheClearWorks() {
        Root<ParentEntity> root = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta1 =
                PathResolverUtils.resolvePathWithMeta(root, "user.name");

        PathResolverUtils.clearCache();

        PathResolverUtils.PathResolutionMetadata meta2 =
                PathResolverUtils.resolvePathWithMeta(root, "user.name");

        // After clear, creates new instance (though functionally equivalent)
        assertNotSame(meta1, meta2);
        assertEquals(meta1.allSegments(), meta2.allSegments());
    }

    @Test
    void testMultipleRootsHaveSeparateCaches() {
        Root<ParentEntity> root1 = cb.createQuery(ParentEntity.class).from(ParentEntity.class);
        Root<ParentEntity> root2 = cb.createQuery(ParentEntity.class).from(ParentEntity.class);

        PathResolverUtils.PathResolutionMetadata meta1 =
                PathResolverUtils.resolvePathWithMeta(root1, "user.name");
        PathResolverUtils.PathResolutionMetadata meta2 =
                PathResolverUtils.resolvePathWithMeta(root2, "user.name");

        // Different roots should not share cache entries
        assertNotSame(meta1, meta2);
    }

    // ==================== Real Query Tests ====================

    @Test
    void testPathWorksInRealQuery() {
        CriteriaQuery<ParentEntity> query = cb.createQuery(ParentEntity.class);
        Root<ParentEntity> root = query.from(ParentEntity.class);

        Path<?> namePath = PathResolverUtils.resolvePath(root, "user.name");

        query.select(root).where(cb.equal(namePath, "John"));

        // Should compile without errors
        assertDoesNotThrow(() -> em.createQuery(query));
    }

    @Test
    void testCollectionPathWorksInRealQuery() {
        CriteriaQuery<ParentEntity> query = cb.createQuery(ParentEntity.class);
        Root<ParentEntity> root = query.from(ParentEntity.class);

        Path<?> childNamePath = PathResolverUtils.resolvePath(root, "children.name");

        query.select(root).where(cb.equal(childNamePath, "Child1"));

        // Should compile without errors
        assertDoesNotThrow(() -> em.createQuery(query));
    }
}
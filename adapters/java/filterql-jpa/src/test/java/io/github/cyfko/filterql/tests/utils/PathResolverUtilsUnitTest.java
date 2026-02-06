package io.github.cyfko.filterql.tests.utils;

import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import io.github.cyfko.jpametamodel.api.CollectionKind;
import io.github.cyfko.jpametamodel.api.CollectionType;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PathResolverUtils that don't require EntityManager or database.
 */
class PathResolverUtilsUnitTest {

    @Test
    void testClearCacheDoesNotThrow() {
        assertDoesNotThrow(PathResolverUtils::clearCache);
    }

    @Test
    void testCannotInstantiateUtilityClass() throws Exception {
        java.lang.reflect.Constructor<?> constructor =
                PathResolverUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Should have thrown UnsupportedOperationException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof UnsupportedOperationException);
            assertTrue(cause.getMessage().contains("utility class"));
        }
    }

    // ==================== CollectionInfo Tests ====================

    @Test
    void testCollectionInfoBidirectional() {
        PathResolverUtils.CollectionInfo info = new PathResolverUtils.CollectionInfo(
                CollectionType.LIST,
                CollectionKind.ENTITY,
                "parent",
                null
        );

        assertTrue(info.isBidirectional());
        assertEquals("parent", info.mappedBy());
    }

    @Test
    void testCollectionInfoNotBidirectional() {
        PathResolverUtils.CollectionInfo info = new PathResolverUtils.CollectionInfo(
                CollectionType.SET,
                CollectionKind.ENTITY,
                null,
                null
        );

        assertFalse(info.isBidirectional());
        assertNull(info.mappedBy());
    }

    @Test
    void testCollectionInfoOrdered() {
        PathResolverUtils.CollectionInfo info = new PathResolverUtils.CollectionInfo(
                CollectionType.LIST,
                CollectionKind.ENTITY,
                null,
                "name ASC"
        );

        assertTrue(info.isOrdered());
        assertEquals("name ASC", info.orderBy());
    }

    @Test
    void testCollectionInfoNotOrdered() {
        PathResolverUtils.CollectionInfo info = new PathResolverUtils.CollectionInfo(
                CollectionType.SET,
                CollectionKind.SCALAR,
                null,
                null
        );

        assertFalse(info.isOrdered());
    }

    @Test
    void testCollectionInfoEntityCollection() {
        PathResolverUtils.CollectionInfo info = new PathResolverUtils.CollectionInfo(
                CollectionType.LIST,
                CollectionKind.ENTITY,
                null,
                null
        );

        assertTrue(info.isEntityCollection());
        assertFalse(info.isScalarCollection());
        assertFalse(info.isEmbeddableCollection());
    }

    @Test
    void testCollectionInfoScalarCollection() {
        PathResolverUtils.CollectionInfo info = new PathResolverUtils.CollectionInfo(
                CollectionType.SET,
                CollectionKind.SCALAR,
                null,
                null
        );

        assertTrue(info.isScalarCollection());
        assertFalse(info.isEntityCollection());
        assertFalse(info.isEmbeddableCollection());
    }

    @Test
    void testCollectionInfoEmbeddableCollection() {
        PathResolverUtils.CollectionInfo info = new PathResolverUtils.CollectionInfo(
                CollectionType.LIST,
                CollectionKind.EMBEDDABLE,
                null,
                null
        );

        assertTrue(info.isEmbeddableCollection());
        assertFalse(info.isEntityCollection());
        assertFalse(info.isScalarCollection());
    }

    // ==================== Record Structure Tests ====================

    @Test
    void testPathResolutionMetadataStructure() {
        // Verify the record has the expected structure
        java.lang.reflect.RecordComponent[] components =
                PathResolverUtils.PathResolutionMetadata.class.getRecordComponents();

        assertEquals(4, components.length);
        assertEquals("finalPath", components[0].getName());
        assertEquals("allSegments", components[1].getName());
        assertEquals("collectionSegments", components[2].getName());
        assertEquals("collectionInfos", components[3].getName());
    }

    @Test
    void testCollectionInfoStructure() {
        java.lang.reflect.RecordComponent[] components =
                PathResolverUtils.CollectionInfo.class.getRecordComponents();

        assertEquals(4, components.length);
        assertEquals("collectionType", components[0].getName());
        assertEquals("elementKind", components[1].getName());
        assertEquals("mappedBy", components[2].getName());
        assertEquals("orderBy", components[3].getName());
    }
}
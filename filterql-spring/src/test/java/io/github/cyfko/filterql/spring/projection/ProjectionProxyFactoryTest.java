package io.github.cyfko.filterql.spring.projection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProjectionProxyFactory}.
 */
class ProjectionProxyFactoryTest {

    // ===================== Test Interfaces =====================

    interface UserProjection {
        String getFirstName();

        String getLastName();

        Integer getAge();

        Boolean isActive();

        Boolean hasPermission();
    }

    interface EmptyProjection {
    }

    // ===================== Tests =====================

    @Nested
    @DisplayName("Proxy creation")
    class ProxyCreation {

        @Test
        @DisplayName("should create a proxy implementing the projection interface")
        void shouldCreateProxy() {
            Map<String, Object> data = Map.of("firstName", "Frank", "lastName", "KOSSI");
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);

            assertNotNull(proxy);
            assertInstanceOf(UserProjection.class, proxy);
            assertInstanceOf(ProjectionProxy.class, proxy);
        }

        @Test
        @DisplayName("should reject non-interface types")
        void shouldRejectNonInterface() {
            assertThrows(IllegalArgumentException.class,
                    () -> ProjectionProxyFactory.create(String.class, Map.of()));
        }

        @Test
        @DisplayName("should reject null arguments")
        void shouldRejectNullArguments() {
            assertThrows(NullPointerException.class,
                    () -> ProjectionProxyFactory.create(null, Map.of()));
            assertThrows(NullPointerException.class,
                    () -> ProjectionProxyFactory.create(UserProjection.class, null));
        }
    }

    @Nested
    @DisplayName("Getter resolution")
    class GetterResolution {

        @Test
        @DisplayName("getXxx() should return projected value")
        void getXxxShouldReturnValue() {
            Map<String, Object> data = Map.of("firstName", "Frank", "lastName", "KOSSI", "age", 30);
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);

            assertEquals("Frank", proxy.getFirstName());
            assertEquals("KOSSI", proxy.getLastName());
            assertEquals(30, proxy.getAge());
        }

        @Test
        @DisplayName("isXxx() should resolve to field 'xxx'")
        void isXxxShouldResolveCorrectly() {
            Map<String, Object> data = Map.of("active", true);
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);

            assertEquals(true, proxy.isActive());
        }

        @Test
        @DisplayName("hasXxx() should resolve to field 'xxx'")
        void hasXxxShouldResolveCorrectly() {
            Map<String, Object> data = Map.of("permission", false);
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);

            assertEquals(false, proxy.hasPermission());
        }

        @Test
        @DisplayName("should return null when field is projected but value is null")
        void shouldReturnNullForProjectedNullValue() {
            Map<String, Object> data = new HashMap<>();
            data.put("firstName", null);
            data.put("lastName", "KOSSI");
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);

            assertNull(proxy.getFirstName()); // null projected value → return null
            assertEquals("KOSSI", proxy.getLastName());
        }

        @Test
        @DisplayName("should throw FieldNotProjectedException for non-projected field")
        void shouldThrowForNonProjectedField() {
            Map<String, Object> data = Map.of("firstName", "Frank");
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);

            FieldNotProjectedException ex = assertThrows(
                    FieldNotProjectedException.class,
                    proxy::getLastName);
            assertEquals("lastName", ex.getFieldName());
            assertEquals("UserProjection", ex.getProjectionType());
        }
    }

    @Nested
    @DisplayName("Object methods")
    class ObjectMethods {

        @Test
        @DisplayName("toString() should include type and projected fields")
        void toStringShouldBeReadable() {
            Map<String, Object> data = Map.of("firstName", "Frank");
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);

            String str = proxy.toString();
            assertTrue(str.contains("UserProjection"));
            assertTrue(str.contains("firstName"));
        }

        @Test
        @DisplayName("equals() should compare interface and data")
        void equalsShouldCompareByInterfaceAndData() {
            Map<String, Object> data1 = Map.of("firstName", "Frank");
            Map<String, Object> data2 = Map.of("firstName", "Frank");
            Map<String, Object> data3 = Map.of("firstName", "Other");

            UserProjection proxy1 = ProjectionProxyFactory.create(UserProjection.class, data1);
            UserProjection proxy2 = ProjectionProxyFactory.create(UserProjection.class, data2);
            UserProjection proxy3 = ProjectionProxyFactory.create(UserProjection.class, data3);

            assertEquals(proxy1, proxy2);
            assertNotEquals(proxy1, proxy3);
        }

        @Test
        @DisplayName("hashCode() should be consistent with equals")
        void hashCodeShouldBeConsistent() {
            Map<String, Object> data1 = Map.of("firstName", "Frank");
            Map<String, Object> data2 = Map.of("firstName", "Frank");

            UserProjection proxy1 = ProjectionProxyFactory.create(UserProjection.class, data1);
            UserProjection proxy2 = ProjectionProxyFactory.create(UserProjection.class, data2);

            assertEquals(proxy1.hashCode(), proxy2.hashCode());
        }
    }

    @Nested
    @DisplayName("isProjectionProxy detection")
    class ProxyDetection {

        @Test
        @DisplayName("should detect projection proxies")
        void shouldDetectProxy() {
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, Map.of("firstName", "F"));
            assertTrue(ProjectionProxyFactory.isProjectionProxy(proxy));
        }

        @Test
        @DisplayName("should not detect regular objects as proxies")
        void shouldNotDetectRegularObjects() {
            assertFalse(ProjectionProxyFactory.isProjectionProxy("hello"));
            assertFalse(ProjectionProxyFactory.isProjectionProxy(null));
        }
    }

    @Nested
    @DisplayName("ProjectionProxy marker interface")
    class MarkerInterface {

        @Test
        @DisplayName("_getProjectedData() should return the underlying map")
        void shouldReturnProjectedData() {
            Map<String, Object> data = Map.of("firstName", "Frank");
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, data);
            ProjectionProxy pp = (ProjectionProxy) proxy;

            assertEquals(data, pp._getProjectedData());
        }

        @Test
        @DisplayName("_getProjectionInterface() should return the interface class")
        void shouldReturnProjectionInterface() {
            UserProjection proxy = ProjectionProxyFactory.create(UserProjection.class, Map.of("firstName", "F"));
            ProjectionProxy pp = (ProjectionProxy) proxy;

            assertEquals(UserProjection.class, pp._getProjectionInterface());
        }
    }

    @Nested
    @DisplayName("Empty projection (no getters)")
    class EmptyProjectionTest {

        @Test
        @DisplayName("should create proxy for empty interface")
        void shouldCreateEmptyProxy() {
            EmptyProjection proxy = ProjectionProxyFactory.create(EmptyProjection.class, Map.of());
            assertNotNull(proxy);
            assertInstanceOf(EmptyProjection.class, proxy);
        }
    }
}

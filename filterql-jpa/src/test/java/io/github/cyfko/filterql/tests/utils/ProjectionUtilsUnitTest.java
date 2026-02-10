package io.github.cyfko.filterql.tests.utils;

import io.github.cyfko.filterql.jpa.exception.InstanceResolutionException;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.filterql.jpa.utils.ProjectionUtils;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProjectionUtils helper methods.
 * <p>
 * Tests invocation strategy, computed field delegation, and error handling
 * for projection-related operations.
 * </p>
 */
@DisplayName("ProjectionUtils Unit Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectionUtilsUnitTest {

    @Nested
    @DisplayName("Method Invocation")
    class MethodInvocationTests {

        @Test
        @Order(1)
        @DisplayName("Should invoke static method when resolver returns null")
        void shouldInvokeStaticMethodWhenResolverReturnsNull() {

            // When
            Object result = ProjectionUtils.invoke(
                    InstanceResolver.noBean(),
                    TestMethods.class,
                    "",
                    "add",
                    2, 3
            );

            // Then
            assertEquals(5, result);
        }

        @Test
        @Order(2)
        @DisplayName("Should invoke instance method when resolver returns bean")
        void shouldInvokeInstanceMethodWhenResolverReturnsBeen() {
            // Given
            InstanceResolver resolver = new InstanceResolver() {
                @Override
                public <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException {
                    if (clazz == TestMethods.class) {
                        return (T) new TestMethods();
                    }
                    return null;
                }
            };

            // When
            Object result = ProjectionUtils.invoke(
                    resolver,
                    TestMethods.class,
                    "",
                    "instanceAdd",
                    2, 3
            );

            // Then
            assertEquals(5, result);
        }

        @Test
        @Order(3)
        @DisplayName("Should handle method with no arguments")
        void shouldHandleMethodWithNoArguments() {
            // Given
            InstanceResolver resolver = new InstanceResolver() {
                @Override
                public <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException {
                    return null;
                }
            };

            // When
            Object result = ProjectionUtils.invoke(
                    resolver,
                    TestMethods.class,
                    "",
                    "getString"
            );

            // Then
            assertEquals("test", result);
        }

        @Disabled
        @Test
        @Order(4)
        @DisplayName("Should handle method with varargs")
        void shouldHandleMethodWithVarargs() {
            // When
            Object result = ProjectionUtils.invoke(
                    InstanceResolver.noBean(),
                    TestMethods.class,
                    "",
                    "concat",
                    "Hello", "World"
            );

            // Then
            assertEquals("HelloWorld", result);
        }

        @Test
        @Order(5)
        @DisplayName("Should throw when method not found")
        void shouldThrowWhenMethodNotFound() {
            // Given
            InstanceResolver resolver = new InstanceResolver() {
                @Override
                public <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException {
                    return null;
                }
            };

            // Then
            assertThrows(UnsupportedOperationException.class, () ->
                    ProjectionUtils.invoke(
                            resolver,
                            TestMethods.class,
                            "",
                            "nonExistentMethod"
                    )
            );
        }

        @Test
        @Order(6)
        @DisplayName("Should throw NullPointerException for null resolver")
        void shouldThrowForNullResolver() {
            assertThrows(NullPointerException.class, () ->
                    ProjectionUtils.invoke(
                            null,
                            TestMethods.class,
                            "",
                            "add",
                            1, 2
                    )
            );
        }

        @Test
        @Order(7)
        @DisplayName("Should throw NullPointerException for null class")
        void shouldThrowForNullClass() {
            InstanceResolver resolver = new InstanceResolver() {
                @Override
                public <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException {
                    return null;
                }
            };

            assertThrows(NullPointerException.class, () ->
                    ProjectionUtils.invoke(
                            resolver,
                            null,
                            "",
                            "method"
                    )
            );
        }

        @Test
        @Order(8)
        @DisplayName("Should throw NullPointerException for null method name")
        void shouldThrowForNullMethodName() {
            InstanceResolver resolver = new InstanceResolver() {
                @Override
                public <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException {
                    return null;
                }
            };

            assertThrows(NullPointerException.class, () ->
                    ProjectionUtils.invoke(
                            resolver,
                            TestMethods.class,
                            "",
                            null
                    )
            );
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @Order(20)
        @DisplayName("Should handle method invocation exceptions")
        void shouldHandleInvocationException() {
            // Given
            InstanceResolver resolver = new InstanceResolver() {
                @Override
                public <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException {
                    return null;
                }
            };

            // Then
            assertThrows(RuntimeException.class, () ->
                    ProjectionUtils.invoke(
                            resolver,
                            TestMethods.class,
                            "",
                            "throwException"
                    )
            );
        }

        @Test
        @Order(21)
        @DisplayName("Should handle IllegalAccessException")
        void shouldHandleIllegalAccessException() {
            // Given
            InstanceResolver resolver = new InstanceResolver() {
                @Override
                public <T> T resolve(Class<T> clazz, String beanName) throws InstanceResolutionException {
                    return null;
                }
            };

            // Then - Private method should cause exception
            assertThrows(Exception.class, () ->
                    ProjectionUtils.invoke(
                            resolver,
                            TestMethods.class,
                            "",
                            "privateMethod"
                    )
            );
        }
    }

    // Test helper class with various method signatures
    public static class TestMethods {
        public static int add(int a, int b) {
            return a + b;
        }

        public int instanceAdd(int a, int b) {
            return a + b;
        }

        public static String getString() {
            return "test";
        }

        public static String concat(String... args) {
            StringBuilder sb = new StringBuilder();
            for (String arg : args) {
                sb.append(arg);
            }
            return sb.toString();
        }

        public static void throwException() {
            throw new IllegalStateException("Test exception");
        }

        private static void privateMethod() {
            // Private method
        }
    }
}

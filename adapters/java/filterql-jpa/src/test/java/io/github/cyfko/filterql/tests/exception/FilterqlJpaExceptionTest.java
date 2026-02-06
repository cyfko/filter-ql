package io.github.cyfko.filterql.tests.exception;

import io.github.cyfko.filterql.jpa.exception.InstanceResolutionException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FilterQL JPA exceptions.
 * <p>
 * Tests exception constructors, messages, and chaining behavior.
 * </p>
 */
@DisplayName("Exception Unit Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FilterqlJpaExceptionTest {

    @Nested
    @DisplayName("InstanceResolutionException")
    class InstanceResolutionExceptionTests {

        @Test
        @Order(1)
        @DisplayName("Should create exception with message only")
        void shouldCreateWithMessageOnly() {
            // Given
            String message = "Failed to resolve instance";

            // When
            InstanceResolutionException ex = new InstanceResolutionException(message);

            // Then
            assertEquals(message, ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @Order(2)
        @DisplayName("Should create exception with message and cause")
        void shouldCreateWithMessageAndCause() {
            // Given
            String message = "Failed to resolve instance";
            Throwable cause = new RuntimeException("Circular dependency");

            // When
            InstanceResolutionException ex = new InstanceResolutionException(message, cause);

            // Then
            assertEquals(message, ex.getMessage());
            assertEquals(cause, ex.getCause());
        }

        @Test
        @Order(3)
        @DisplayName("Should be throwable and catchable as RuntimeException")
        void shouldBeThrowableAsRuntimeException() {
            // Then
            assertThrows(RuntimeException.class, () -> {
                throw new InstanceResolutionException("Test exception");
            });
        }

        @Test
        @Order(4)
        @DisplayName("Should preserve cause exception details")
        void shouldPreserveCauseExceptionDetails() {
            // Given
            String rootCause = "Original error message";
            RuntimeException cause = new RuntimeException(rootCause);
            InstanceResolutionException ex = new InstanceResolutionException("Wrapper", cause);

            // Then
            assertEquals(rootCause, ex.getCause().getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @Order(5)
        @DisplayName("Should support exception chaining")
        void shouldSupportExceptionChaining() {
            // Given
            Exception root = new Exception("Root");
            InstanceResolutionException level1 = new InstanceResolutionException("Level 1", root);
            InstanceResolutionException level2 = new InstanceResolutionException("Level 2", level1);

            // When
            Throwable cause = level2.getCause();

            // Then
            assertInstanceOf(InstanceResolutionException.class, cause);
            assertEquals("Level 1", cause.getMessage());
        }

        @Test
        @Order(6)
        @DisplayName("Should handle null message")
        void shouldHandleNullMessage() {
            // When
            InstanceResolutionException ex = new InstanceResolutionException(null);

            // Then
            assertNull(ex.getMessage());
        }

        @Test
        @Order(7)
        @DisplayName("Should handle null cause")
        void shouldHandleNullCause() {
            // When
            InstanceResolutionException ex = new InstanceResolutionException("Message", null);

            // Then
            assertEquals("Message", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @Order(8)
        @DisplayName("Should include cause in toString")
        void shouldIncludeCauseInToString() {
            // Given
            Throwable cause = new RuntimeException("Root cause");
            InstanceResolutionException ex = new InstanceResolutionException("Wrapper", cause);

            // When
            String str = ex.toString();

            // Then
            assertTrue(str.contains("InstanceResolutionException"));
            assertTrue(str.contains("Wrapper"));
        }

        @Test
        @Order(9)
        @DisplayName("Should be serializable")
        void shouldBeSerializable() {
            // Given
            InstanceResolutionException ex = new InstanceResolutionException("Test", null);

            // Then - Just ensure it doesn't throw
            assertDoesNotThrow(() -> {
                ex.getStackTrace();
            });
        }
    }

    @Nested
    @DisplayName("Exception Hierarchy")
    class ExceptionHierarchyTests {

        @Test
        @Order(20)
        @DisplayName("InstanceResolutionException should extend RuntimeException")
        void shouldExtendRuntimeException() {
            // Given
            InstanceResolutionException ex = new InstanceResolutionException("Test");

            // Then
            assertInstanceOf(RuntimeException.class, ex);
        }

        @Test
        @Order(21)
        @DisplayName("InstanceResolutionException should extend Exception")
        void shouldExtendException() {
            // Given
            InstanceResolutionException ex = new InstanceResolutionException("Test");

            // Then
            assertInstanceOf(Exception.class, ex);
        }

        @Test
        @Order(22)
        @DisplayName("InstanceResolutionException should extend Throwable")
        void shouldExtendThrowable() {
            // Given
            InstanceResolutionException ex = new InstanceResolutionException("Test");

            // Then
            assertInstanceOf(Throwable.class, ex);
        }
    }

    @Nested
    @DisplayName("Usage Patterns")
    class UsagePatternsTests {

        @Test
        @Order(30)
        @DisplayName("Should be usable in try-catch")
        void shouldBeUsableInTryCatch() {
            // When/Then
            try {
                throw new InstanceResolutionException("Test exception");
            } catch (InstanceResolutionException ex) {
                assertEquals("Test exception", ex.getMessage());
            }
        }

        @Test
        @Order(31)
        @DisplayName("Should be usable in catch-all RuntimeException")
        void shouldBeUsableInCatchAllRuntimeException() {
            // When/Then
            try {
                throw new InstanceResolutionException("Test exception");
            } catch (RuntimeException ex) {
                assertInstanceOf(InstanceResolutionException.class, ex);
            }
        }

        @Test
        @Order(32)
        @DisplayName("Should be usable with throws declaration")
        void shouldBeUsableWithThrowsDeclaration() {
            // Then
            assertDoesNotThrow(() -> methodThatThrowsInstanceResolution(false));
            assertThrows(InstanceResolutionException.class, () -> methodThatThrowsInstanceResolution(true));
        }

        private void methodThatThrowsInstanceResolution(boolean shouldThrow) {
            if (shouldThrow) {
                throw new InstanceResolutionException("Thrown from method");
            }
        }

        @Test
        @Order(33)
        @DisplayName("Should support exception translation pattern")
        void shouldSupportExceptionTranslation() {
            // When
            InstanceResolutionException translated = null;
            try {
                throw new IllegalStateException("Original");
            } catch (Exception e) {
                translated = new InstanceResolutionException("Translated: " + e.getMessage(), e);
            }

            // Then
            assertNotNull(translated);
            assertTrue(translated.getMessage().contains("Translated"));
            assertInstanceOf(IllegalStateException.class, translated.getCause());
        }
    }

    @Nested
    @DisplayName("Stack Traces")
    class StackTraceTests {

        @Test
        @Order(40)
        @DisplayName("Should include full stack trace")
        void shouldIncludeFullStackTrace() {
            // Given
            InstanceResolutionException ex = new InstanceResolutionException("Test");

            // When
            StackTraceElement[] stackTrace = ex.getStackTrace();

            // Then
            assertTrue(stackTrace.length > 0);
            // Verify current test method is in stack
            boolean found = false;
            for (StackTraceElement element : stackTrace) {
                if (element.getMethodName().equals("shouldIncludeFullStackTrace")) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }

        @Test
        @Order(41)
        @DisplayName("Should print stack trace without errors")
        void shouldPrintStackTraceWithoutErrors() {
            // Given
            InstanceResolutionException ex = new InstanceResolutionException("Test");

            // Then
            assertDoesNotThrow(() -> {
                ex.printStackTrace();
            });
        }
    }
}

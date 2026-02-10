package io.github.cyfko.filterql.core.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class DSLSyntaxExceptionTest {

    @Test
    @DisplayName("Should create DSLSyntaxException with message")
    void shouldCreateDSLSyntaxExceptionWithMessage() {
        // Given
        String message = "Invalid DSL syntax";

        // When
        DSLSyntaxException exception = new DSLSyntaxException(message);

        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create DSLSyntaxException with message and cause")
    void shouldCreateDSLSyntaxExceptionWithMessageAndCause() {
        // Given
        String message = "Invalid DSL syntax";
        Throwable cause = new IllegalArgumentException("Root cause");

        // When
        DSLSyntaxException exception = new DSLSyntaxException(message, cause);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should handle null message")
    void shouldHandleNullMessage() {
        // When
        DSLSyntaxException exception = new DSLSyntaxException(null);

        // Then
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should handle null cause")
    void shouldHandleNullCause() {
        // Given
        String message = "Test message";

        // When
        DSLSyntaxException exception = new DSLSyntaxException(message, null);

        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should be instance of Exception")
    void shouldBeInstanceOfException() {
        // Given
        DSLSyntaxException exception = new DSLSyntaxException("test");

        // Then
        assertInstanceOf(Exception.class, exception);
        assertInstanceOf(Throwable.class, exception);
    }
}


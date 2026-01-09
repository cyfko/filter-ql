package io.github.cyfko.filterql.core.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class FilterValidationExceptionTest {

    @Test
    @DisplayName("Should create FilterValidationException with message")
    void shouldCreateFilterValidationExceptionWithMessage() {
        // Given
        String message = "Filter validation failed";

        // When
        FilterValidationException exception = new FilterValidationException(message);

        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should create FilterValidationException with message and cause")
    void shouldCreateFilterValidationExceptionWithMessageAndCause() {
        // Given
        String message = "Filter validation failed";
        Throwable cause = new IllegalArgumentException("Invalid property");

        // When
        FilterValidationException exception = new FilterValidationException(message, cause);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Should handle null message")
    void shouldHandleNullMessage() {
        // When
        FilterValidationException exception = new FilterValidationException(null);

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
        FilterValidationException exception = new FilterValidationException(message, null);

        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Should be instance of Exception")
    void shouldBeInstanceOfException() {
        // Given
        FilterValidationException exception = new FilterValidationException("test");

        // Then
        assertInstanceOf(Exception.class, exception);
        assertInstanceOf(Throwable.class, exception);
    }
}


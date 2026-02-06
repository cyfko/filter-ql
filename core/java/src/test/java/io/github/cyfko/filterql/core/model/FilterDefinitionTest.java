package io.github.cyfko.filterql.core.model;

import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.validation.DefinedPropertyReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FilterDefinition record.
 * 
 * <p>
 * Note: Custom operator validation is now handled via PredicateResolverMapping
 * in the JPA adapter, not via OperatorProviderRegistry.
 * </p>
 */
class FilterDefinitionTest {

    // ============================================================================
    // Standard Constructor Tests
    // ============================================================================

    @Test
    @DisplayName("Should create FilterDefinition with valid parameters")
    void shouldCreateFilterDefinitionWithValidParameters() {
        // Given
        DefinedPropertyReference ref = DefinedPropertyReference.USER_NAME;
        Op operator = Op.EQ;
        Object value = "test";

        // When
        FilterDefinition<DefinedPropertyReference> filterDefinition = new FilterDefinition<>(ref, operator, value);

        // Then
        assertEquals(ref, filterDefinition.ref());
        assertEquals(operator, filterDefinition.operator());
        assertEquals(value, filterDefinition.value());
    }

    @Test
    @DisplayName("Should create FilterDefinition with null value")
    void shouldCreateFilterDefinitionWithNullValue() {
        // Given
        DefinedPropertyReference ref = DefinedPropertyReference.USER_NAME;
        Op operator = Op.IS_NULL;
        Object value = null;

        // When
        FilterDefinition<DefinedPropertyReference> filterDefinition = new FilterDefinition<>(ref, operator, value);

        // Then
        assertEquals(ref, filterDefinition.ref());
        assertEquals(operator, filterDefinition.operator());
        assertNull(filterDefinition.value());
    }

    @Test
    @DisplayName("Should create FilterDefinition with different value types")
    void shouldCreateFilterDefinitionWithDifferentValueTypes() {
        // Test with String
        FilterDefinition<DefinedPropertyReference> stringFilter = new FilterDefinition<>(DefinedPropertyReference.USER_NAME, Op.MATCHES, "pattern%");
        assertEquals("pattern%", stringFilter.value());

        // Test with Integer
        FilterDefinition<DefinedPropertyReference> intFilter = new FilterDefinition<>(DefinedPropertyReference.USER_AGE, Op.GT, 42);
        assertEquals(42, intFilter.value());

        // Test with Boolean
        FilterDefinition<DefinedPropertyReference> booleanFilter = new FilterDefinition<>(DefinedPropertyReference.USER_STATUS, Op.EQ, true);
        assertEquals(true, booleanFilter.value());
    }

    @Test
    @DisplayName("Should handle toString method")
    void shouldHandleToStringMethod() {
        // Given
        FilterDefinition<DefinedPropertyReference> filterDefinition = new FilterDefinition<>(
            DefinedPropertyReference.USER_NAME,
            Op.EQ,
            "test"
        );

        // When
        String result = filterDefinition.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("FilterDefinition"));
    }

    // ============================================================================
    // String Operator Constructor Tests
    // ============================================================================

    @Test
    @DisplayName("Should create FilterDefinition using string constructor for standard operator")
    void shouldCreateFilterDefinitionUsingStringConstructorForStandardOperator() {
        // Given & When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "EQ",
                "test"
            );

        // Then
        assertEquals(Op.EQ, definition.operator(), "Should resolve to standard EQ operator");
        assertEquals("test", definition.value());
        assertEquals(DefinedPropertyReference.USER_NAME, definition.ref());
    }

    @Test
    @DisplayName("Should create FilterDefinition using string constructor for custom operator")
    void shouldCreateFilterDefinitionUsingStringConstructorForCustomOperator() {
        // Custom operators are now handled via PredicateResolverMapping in JPA adapter
        // Here we just test that custom operator codes are accepted at construction time
        
        // Given & When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",  // Custom operator code
                "Smith"
            );

        // Then
        assertEquals(Op.CUSTOM, definition.operator(), "Should resolve to CUSTOM operator");
        assertEquals("SOUNDEX", definition.op(), "op() should return SOUNDEX");
        assertEquals("Smith", definition.value());
        assertEquals(DefinedPropertyReference.USER_NAME, definition.ref());
        assertTrue(definition.isCustomOperator(), "Should be identified as custom operator");
    }

    @Test
    @DisplayName("Should handle all standard operators via string constructor")
    void shouldHandleAllStandardOperatorsViaStringConstructor() {
        // Test various standard operators
        FilterDefinition<DefinedPropertyReference> eqDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_NAME, "EQ", "value");
        assertEquals(Op.EQ, eqDef.operator());

        FilterDefinition<DefinedPropertyReference> gtDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_AGE, "GT", 25);
        assertEquals(Op.GT, gtDef.operator());

        FilterDefinition<DefinedPropertyReference> matchesDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_NAME, "MATCHES", "pattern%");
        assertEquals(Op.MATCHES, matchesDef.operator());

        FilterDefinition<DefinedPropertyReference> inDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_STATUS, "IN", new String[]{"A", "B"});
        assertEquals(Op.IN, inDef.operator());
    }

    @Test
    @DisplayName("Should throw exception when string operator is null")
    void shouldThrowExceptionWhenStringOperatorIsNull() {
        // When & Then
        FilterDefinitionException exception = assertThrows(
            FilterDefinitionException.class,
            () -> new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                (String) null,
                "value"
            ),
            "Should throw exception for null operator string"
        );

        assertTrue(exception.getMessage().contains("operator cannot be null nor blank"),
            "Exception should mention null or blank operator");
    }

    @Test
    @DisplayName("Should allow creating FilterDefinition with custom operator (lazy validation)")
    void shouldAllowCreatingFilterDefinitionWithCustomOperator() {
        // With the new architecture, custom operators are validated at execution time
        // by PredicateResolverMapping in the JPA adapter, not at construction time
        
        // When
        FilterDefinition<DefinedPropertyReference> definition = new FilterDefinition<>(
            DefinedPropertyReference.USER_NAME,
            "UNKNOWN_CUSTOM_OP",
            "value"
        );

        // Then - construction succeeds
        assertNotNull(definition);
        assertEquals("UNKNOWN_CUSTOM_OP", definition.op());
        assertTrue(definition.isCustomOperator());
    }

    @Test
    @DisplayName("Should handle case-insensitive operator lookup in string constructor")
    void shouldHandleCaseInsensitiveOperatorLookupInStringConstructor() {
        // Lowercase should be normalized to uppercase
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(DefinedPropertyReference.USER_NAME, "eQ", "value");
        assertEquals(Op.EQ, definition.operator());
    }

    // ============================================================================
    // Custom Operator Tests
    // ============================================================================

    @Test
    @DisplayName("Should identify custom operators correctly")
    void shouldIdentifyCustomOperatorsCorrectly() {
        // Standard operator
        FilterDefinition<DefinedPropertyReference> standardDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_NAME, Op.EQ, "value");
        assertFalse(standardDef.isCustomOperator());

        // Custom operator
        FilterDefinition<DefinedPropertyReference> customDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_NAME, "SOUNDEX", "Smith");
        assertTrue(customDef.isCustomOperator());
        assertEquals(Op.CUSTOM, customDef.operator());
    }

    @Test
    @DisplayName("Should create FilterDefinition with custom operator code")
    void shouldCreateFilterDefinitionWithCustomOperatorCode() {
        // When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "GEO_DISTANCE",
                "48.8566,2.3522,10"
            );

        // Then
        assertEquals(Op.CUSTOM, definition.operator());
        assertEquals("GEO_DISTANCE", definition.op());
        assertEquals("48.8566,2.3522,10", definition.value());
    }

    @Test
    @DisplayName("Should throw exception when CUSTOM operator has blank op")
    void shouldThrowExceptionWhenCustomOperatorHasBlankRegistryKey() {
        // When & Then
        FilterDefinitionException exception = assertThrows(
            FilterDefinitionException.class,
            () -> new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "   ",
                "value"
            ),
            "Should throw exception for blank op"
        );

        assertTrue(exception.getMessage().contains("operator cannot be null nor blank"),
            "Exception should mention op requirement");
    }

    @Test
    @DisplayName("Should allow exact enum name op for standard operators")
    void shouldAllowExactEnumNameOpForStandardOperators() {
        // When - All standard operators should work
        FilterDefinition<DefinedPropertyReference> eqDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_NAME, Op.EQ, "value");
        FilterDefinition<DefinedPropertyReference> gtDef =
            new FilterDefinition<>(DefinedPropertyReference.USER_AGE, Op.GT, 25);

        // Then
        assertEquals(eqDef.op(), Op.EQ.name());
        assertEquals(gtDef.op(), Op.GT.name());
        assertEquals(Op.EQ, eqDef.operator());
        assertEquals(Op.GT, gtDef.operator());
    }

    // ============================================================================
    // Validation Tests
    // ============================================================================

    @Test
    @DisplayName("Should throw exception when ref is null")
    void shouldThrowExceptionWhenRefIsNull() {
        // When & Then
        FilterDefinitionException exception = assertThrows(
            FilterDefinitionException.class,
            () -> new FilterDefinition<>(null, Op.EQ, "value"),
            "Should throw exception for null ref"
        );

        assertTrue(exception.getMessage().contains("ref cannot be null"),
            "Exception should mention ref");
    }

    @Test
    @DisplayName("Should throw exception when operator is null")
    void shouldThrowExceptionWhenOperatorIsNull() {
        // When & Then
        FilterDefinitionException exception = assertThrows(
            FilterDefinitionException.class,
            () -> new FilterDefinition<>(DefinedPropertyReference.USER_NAME, (Op) null, "value"),
            "Should throw exception for null operator"
        );

        assertTrue(exception.getMessage().contains("operator is required"),
            "Exception should mention operator");
    }

    @Test
    @DisplayName("Should throw exception when IS_NULL has non-null value")
    void shouldThrowExceptionWhenIsNullHasNonNullValue() {
        // When & Then
        FilterDefinitionException exception = assertThrows(
            FilterDefinitionException.class,
            () -> new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                Op.IS_NULL,
                "non-null value"
            ),
            "Should throw exception when IS_NULL operator has non-null value"
        );

        assertTrue(exception.getMessage().contains("value must be null"),
            "Exception should mention value must be null for IS_NULL");
    }

    @Test
    @DisplayName("Should allow null value for IS_NULL operator")
    void shouldAllowNullValueForIsNullOperator() {
        // When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                Op.IS_NULL,
                null
            );

        // Then
        assertEquals(Op.IS_NULL, definition.operator());
        assertNull(definition.value());
    }
}

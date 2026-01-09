package io.github.cyfko.filterql.core.model;

import io.github.cyfko.filterql.core.spi.CustomOperatorProvider;
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.DefinedPropertyReference;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

class FilterDefinitionTest {

    private CustomOperatorProvider testProvider;

    @BeforeEach
    void setUp() {
        // Register a test custom operator provider for testing
        testProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("SOUNDEX", "GEO_DISTANCE");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };
        OperatorProviderRegistry.unregister(testProvider.supportedOperators());
    }

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
    // String Operator Constructor Tests (Alternative Constructor)
    // ============================================================================

    @Test
    @DisplayName("Should create FilterDefinition using string constructor for standard operator")
    void shouldCreateFilterDefinitionUsingStringConstructorForStandardOperator() {
        // Given & When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "EQ",  // String operator code
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
        // Given
        OperatorProviderRegistry.register(testProvider);

        // Given & When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",  // Custom operator code (registered in setUp)
                "Smith"
            );

        // Then
        assertEquals(Op.CUSTOM, definition.operator(), "Should resolve to CUSTOM operator");
        assertEquals("SOUNDEX", definition.op(), "RegistryKey should be set to SOUNDEX");
        assertEquals("Smith", definition.value());
        assertEquals(DefinedPropertyReference.USER_NAME, definition.ref());
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
                (String) null,  // Null operator string
                "value"
            ),
            "Should throw exception for null operator string"
        );

        assertTrue(exception.getMessage().contains("operator cannot be null nor blank"),
            "Exception should mention null or blank operator");
    }

    @Test
    @DisplayName("Should allow creating FilterDefinition with unregistered custom operator (lazy validation)")
    void shouldAllowCreatingFilterDefinitionWithUnregisteredCustomOperator() {
        // With lazy validation, FilterDefinition construction succeeds
        // Validation happens later during FilterContext.toCondition()
        
        // When
        FilterDefinition<DefinedPropertyReference> definition = new FilterDefinition<>(
            DefinedPropertyReference.USER_NAME,
            "UNKNOWN_CUSTOM_OP",  // Not registered - OK at construction time
            "value"
        );

        // Then - construction succeeds
        assertNotNull(definition);
        assertEquals("UNKNOWN_CUSTOM_OP", definition.op());
        assertTrue(definition.isCustomOperator());
        
        // Note: Validation will happen later when FilterContext.toCondition() is called
    }

    @Test
    @DisplayName("Should handle case-insensitive operator lookup in string constructor")
    void shouldHandleCaseInsensitiveOperatorLookupInStringConstructor() {
        // Uppercase should succeed
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(DefinedPropertyReference.USER_NAME, "eQ", "value");
        assertEquals(Op.EQ, definition.operator());
    }

    // ============================================================================
    // CUSTOM Operator and RegistryKey Validation Tests
    // ============================================================================

    @Test
    @DisplayName("Should create FilterDefinition with CUSTOM operator and op")
    void shouldCreateFilterDefinitionWithCustomOperatorAndRegistryKey() {
        // Given
        OperatorProviderRegistry.register(testProvider);

        // When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",
                "Smith"
            );

        // Then
        assertEquals(Op.CUSTOM, definition.operator());
        assertEquals("SOUNDEX", definition.op());
        assertEquals("Smith", definition.value());
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
            "Should throw exception for blank op with CUSTOM operator"
        );

        assertTrue(exception.getMessage().contains("operator cannot be null nor blank"),
            "Exception should mention op requirement");
    }

    @Test
    @DisplayName("Should allow creating FilterDefinition with non-existent registry key (lazy validation)")
    void shouldAllowCreatingFilterDefinitionWithNonExistentRegistryKey() {
        // With lazy validation, construction succeeds even if operator not registered
        // Validation happens later during FilterContext.toCondition()
        
        // When
        FilterDefinition<DefinedPropertyReference> definition = new FilterDefinition<>(
            DefinedPropertyReference.USER_NAME,
            "NONEXISTENT_OP",  // Not in registry - OK at construction
            "value"
        );

        // Then
        assertNotNull(definition);
        assertEquals("NONEXISTENT_OP", definition.op());
        assertTrue(definition.isCustomOperator());
    }

    @Test
    @DisplayName("Should allow exact enum name op for standard operators")
    void shouldAllowNullRegistryKeyForStandardOperators() {
        // When - All standard operators should work with null op
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

    @Test
    @DisplayName("Should validate multiple custom operators from same provider")
    void shouldValidateMultipleCustomOperatorsFromSameProvider() {
        // Given
        OperatorProviderRegistry.register(testProvider);

        // When - Both SOUNDEX and GEO_DISTANCE are registered by testProvider
        FilterDefinition<DefinedPropertyReference> soundexDef =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",
                "Smith"
            );

        FilterDefinition<DefinedPropertyReference> geoDef =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "GEO_DISTANCE",
                "48.8566,2.3522,10"
            );

        // Then
        assertEquals(Op.CUSTOM, soundexDef.operator());
        assertEquals("SOUNDEX", soundexDef.op());

        assertEquals(Op.CUSTOM, geoDef.operator());
        assertEquals("GEO_DISTANCE", geoDef.op());
    }

    // ============================================================================
    // Existing Validation Tests
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
                "non-null value"  // Should be null
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

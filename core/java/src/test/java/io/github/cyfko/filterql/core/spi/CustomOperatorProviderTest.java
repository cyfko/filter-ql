package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.DefinedPropertyReference;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for CustomOperatorProvider.
 * Tests custom operator implementation, integration with FilterDefinition,
 * and end-to-end workflow with OperatorProviderRegistry.
 *
 * @author FilterQL Test Suite
 * @since 4.0.0
 */
@DisplayName("CustomOperatorProvider Tests")
class CustomOperatorProviderTest {

    private CustomOperatorProvider soundexProvider;
    private CustomOperatorProvider distanceProvider;
    private CustomOperatorProvider complexProvider;

    @BeforeEach
    void setUp() {
        // SOUNDEX operator - phonetic matching
        soundexProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("SOUNDEX");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> {
                    // Simulate SOUNDEX SQL function: SOUNDEX(field) = SOUNDEX(value)
                    String value = (String) definition.value();
                    return cb.conjunction(); // Simplified for testing
                };
            }
        };

        // GEO_DISTANCE operator - geographical distance filtering
        distanceProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("GEO_DISTANCE");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> {
                    // Simulate: distance(lat, lon, value.lat, value.lon) < threshold
                    return cb.conjunction();
                };
            }
        };

        // Provider with multiple complex operators
        complexProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("FUZZY_MATCH", "REGEX", "JSON_CONTAINS");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> {
                    // Different logic based on operator
                    return cb.conjunction();
                };
            }
        };

        OperatorProviderRegistry.unregister(soundexProvider.supportedOperators());
        OperatorProviderRegistry.unregister(distanceProvider.supportedOperators());
        OperatorProviderRegistry.unregister(complexProvider.supportedOperators());
    }

    // ============================================================================
    // Basic Contract Tests
    // ============================================================================

    @Test
    @DisplayName("Should implement supportedOperators contract")
    void shouldImplementSupportedOperatorsContract() {
        // When
        Set<String> operators = soundexProvider.supportedOperators();

        // Then
        assertNotNull(operators, "supportedOperators should never return null");
        assertFalse(operators.isEmpty(), "Should support at least one operator");
        assertTrue(operators.contains("SOUNDEX"), "Should declare SOUNDEX operator");
    }

    @Test
    @DisplayName("Should implement toResolver contract")
    void shouldImplementToResolverContract() {
        // Given - Register provider first
        OperatorProviderRegistry.register(soundexProvider);

        // Given
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME, 
                "SOUNDEX",
                "Smith"
            );

        // When
        PredicateResolver<?> resolver = soundexProvider.toResolver(definition);

        // Then
        assertNotNull(resolver, "toResolver should never return null");
    }

    @Test
    @DisplayName("Should support multiple operators in single provider")
    void shouldSupportMultipleOperatorsInSingleProvider() {
        // When
        Set<String> operators = complexProvider.supportedOperators();

        // Then
        assertEquals(3, operators.size(), "Should support 3 operators");
        assertTrue(operators.contains("FUZZY_MATCH"));
        assertTrue(operators.contains("REGEX"));
        assertTrue(operators.contains("JSON_CONTAINS"));
    }

    // ============================================================================
    // Integration with FilterDefinition Tests
    // ============================================================================

    @Test
    @DisplayName("Should create FilterDefinition with CUSTOM operator and op")
    void shouldCreateFilterDefinitionWithCustomOperator() {
        // Given - Register provider first
        OperatorProviderRegistry.register(soundexProvider);

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
    @DisplayName("Should create FilterDefinition using string constructor for custom operator")
    void shouldCreateFilterDefinitionUsingStringConstructor() {
        // Given
        OperatorProviderRegistry.register(soundexProvider);

        // When
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",  // String operator code
                "Smith"
            );

        // Then
        assertEquals(Op.CUSTOM, definition.operator());
        assertEquals("SOUNDEX", definition.op());
    }

    @Test
    @DisplayName("Should throw exception when CUSTOM operator has no op")
    void shouldThrowExceptionWhenCustomOperatorHasNoRegistryKey() {
        // When & Then
        FilterDefinitionException exception = assertThrows(
            FilterDefinitionException.class,
            () -> new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                (String) null,
                "value"
            ),
            "Should throw exception when op is null for CUSTOM operator"
        );

        assertTrue(exception.getMessage().contains("operator cannot be null nor blank"),
            "Exception should mention op requirement");
    }

    @Test
    @DisplayName("Should throw exception when op is blank")
    void shouldThrowExceptionWhenRegistryKeyIsBlank() {
        // When & Then
        assertThrows(
            FilterDefinitionException.class,
            () -> new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "   ",
                "value"
            ),
            "Should throw exception when op is blank"
        );
    }

    @Test
    @DisplayName("Should allow creating FilterDefinition with non-existent registry key (lazy validation)")
    void shouldAllowCreatingFilterDefinitionWithNonExistentRegistryKey() {
        // Lazy validation: construction succeeds, validation at usage time
        
        // When
        FilterDefinition<DefinedPropertyReference> definition = new FilterDefinition<>(
            DefinedPropertyReference.USER_NAME,
            "NONEXISTENT_OP",
            "value"
        );

        // Then - construction succeeds
        assertNotNull(definition);
        assertEquals("NONEXISTENT_OP", definition.op());
        assertTrue(definition.isCustomOperator());
        
        // Note: Exception would be thrown during FilterContext.toCondition() call
    }

    // ============================================================================
    // PredicateResolver Execution Tests
    // ============================================================================

    @Test
    @DisplayName("Should generate predicate from custom operator")
    void shouldGeneratePredicateFromCustomOperator() {
        // Given
        OperatorProviderRegistry.register(soundexProvider);

        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",
                "Smith"
            );

        // Mock JPA components
        @SuppressWarnings("unchecked")
        Root<Object> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        CriteriaQuery<Object> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate mockPredicate = mock(Predicate.class);

        when(cb.conjunction()).thenReturn(mockPredicate);

        // When
        PredicateResolver<?> resolver = soundexProvider.toResolver(definition);

        Predicate predicate = ((PredicateResolver<Object>) resolver).resolve(root, query, cb);

        // Then
        assertNotNull(predicate, "Should generate predicate");
        verify(cb).conjunction(); // Verify interaction with CriteriaBuilder
    }

    @Test
    @DisplayName("Should handle different value types in custom operator")
    void shouldHandleDifferentValueTypesInCustomOperator() {
        // Given
        OperatorProviderRegistry.register(distanceProvider);

        // Create definition with complex value object
        class GeoPoint {
            double lat;
            double lon;
            double maxDistance;

            GeoPoint(double lat, double lon, double maxDistance) {
                this.lat = lat;
                this.lon = lon;
                this.maxDistance = maxDistance;
            }
        }

        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "GEO_DISTANCE",
                new GeoPoint(48.8566, 2.3522, 10.0)  // Paris coordinates, 10km radius
            );

        // When
        PredicateResolver<?> resolver = distanceProvider.toResolver(definition);

        // Then
        assertNotNull(resolver, "Should handle complex value types");
        assertTrue(definition.value() instanceof GeoPoint, "Value should preserve type");
    }

    // ============================================================================
    // End-to-End Workflow Tests
    // ============================================================================

    @Test
    @DisplayName("Should complete full workflow: register → create definition → resolve")
    void shouldCompleteFullWorkflow() {
        // Step 1: Register custom operator
        OperatorProviderRegistry.register(soundexProvider);

        // Step 2: Verify registration
        var provider = OperatorProviderRegistry.getProvider("SOUNDEX");
        assertTrue(provider.isPresent(), "Provider should be registered");

        // Step 3: Create FilterDefinition
        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",
                "Smith"
            );

        // Step 4: Resolve to PredicateResolver
        PredicateResolver<?> resolver = provider.get().toResolver(definition);
        assertNotNull(resolver, "Should create resolver");

        // Step 5: Execute resolver
        @SuppressWarnings("unchecked")
        Root<Object> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        CriteriaQuery<Object> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        @SuppressWarnings("unchecked") Predicate predicate = ((PredicateResolver<Object>) resolver).resolve(root, query, cb);
        assertNotNull(predicate, "Should generate predicate");
    }

    @Test
    @DisplayName("Should handle multiple custom operators in sequence")
    void shouldHandleMultipleCustomOperatorsInSequence() {
        // Given
        OperatorProviderRegistry.register(soundexProvider);
        OperatorProviderRegistry.register(distanceProvider);

        // When - Create definitions for both
        FilterDefinition<DefinedPropertyReference> def1 =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",
                "Smith"
            );

        FilterDefinition<DefinedPropertyReference> def2 =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "GEO_DISTANCE",
                "48.8566,2.3522,10"
            );

        // Then
        assertEquals(Op.CUSTOM, def1.operator());
        assertEquals("SOUNDEX", def1.op());

        assertEquals(Op.CUSTOM, def2.operator());
        assertEquals("GEO_DISTANCE", def2.op());
    }

    // ============================================================================
    // Error Handling Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle provider that returns null resolver")
    void shouldHandleProviderThatReturnsNullResolver() {
        // Given
        CustomOperatorProvider nullResolverProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("NULL_OP");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return null;  // Bad implementation
            }
        };

        OperatorProviderRegistry.register(nullResolverProvider);

        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "NULL_OP",
                "value"
            );

        // When
        PredicateResolver<?> resolver = nullResolverProvider.toResolver(definition);

        // Then
        assertNull(resolver, "Should return null (bad practice but allowed by contract)");
    }

    @Test
    @DisplayName("Should handle provider that throws exception in toResolver")
    void shouldHandleProviderThatThrowsExceptionInToResolver() {
        // Given
        CustomOperatorProvider throwingProvider = new CustomOperatorProvider() {
            @Override
            public Set<String> supportedOperators() {
                return Set.of("THROWING_OP");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                throw new IllegalArgumentException("Simulated error");
            }
        };

        OperatorProviderRegistry.register(throwingProvider);

        FilterDefinition<DefinedPropertyReference> definition =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "THROWING_OP",
                "value"
            );

        // When & Then
        assertThrows(
            IllegalArgumentException.class,
            () -> throwingProvider.toResolver(definition),
            "Should propagate exception from provider"
        );
    }

    // ============================================================================
    // Best Practices Tests
    // ============================================================================

    @Test
    @DisplayName("Should return immutable set of supported operators")
    void shouldReturnImmutableSetOfSupportedOperators() {
        // When
        Set<String> operators = soundexProvider.supportedOperators();

        // Then
        assertThrows(
            UnsupportedOperationException.class,
            () -> operators.add("NEW_OP"),
            "Supported operators set should be immutable"
        );
    }

    @Test
    @DisplayName("Should maintain stateless provider")
    void shouldMaintainStatelessProvider() {
        // Given
        OperatorProviderRegistry.register(soundexProvider);

        FilterDefinition<DefinedPropertyReference> def1 =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",
                "Smith"
            );

        FilterDefinition<DefinedPropertyReference> def2 =
            new FilterDefinition<>(
                DefinedPropertyReference.USER_NAME,
                "SOUNDEX",
                "Johnson"
            );

        // When
        PredicateResolver<?> resolver1 = soundexProvider.toResolver(def1);
        PredicateResolver<?> resolver2 = soundexProvider.toResolver(def2);

        // Then
        assertNotNull(resolver1);
        assertNotNull(resolver2);
        assertNotSame(resolver1, resolver2, "Each call should produce independent resolver");
    }

    @Test
    @DisplayName("Should document operator semantics clearly")
    void shouldDocumentOperatorSemanticsClearly() {
        // Given - Provider with well-documented operators
        CustomOperatorProvider documentedProvider = new CustomOperatorProvider() {
            /**
             * Supports the following operators:
             * - SOUNDEX: Phonetic matching using SOUNDEX algorithm
             * - LEVENSHTEIN: Edit distance matching with configurable threshold
             */
            @Override
            public Set<String> supportedOperators() {
                return Set.of("SOUNDEX", "LEVENSHTEIN");
            }

            @Override
            public <P extends Enum<P> & PropertyReference>
            PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
                return (root, query, cb) -> cb.conjunction();
            }
        };

        // When
        Set<String> operators = documentedProvider.supportedOperators();

        // Then
        assertTrue(operators.contains("SOUNDEX"), "Should include documented operators");
        assertTrue(operators.contains("LEVENSHTEIN"), "Should include documented operators");
    }
}

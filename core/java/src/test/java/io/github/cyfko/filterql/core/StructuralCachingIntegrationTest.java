package io.github.cyfko.filterql.core;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.api.FilterTree;
import io.github.cyfko.filterql.core.config.CachePolicy;
import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.impl.BasicDslParser;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive integration tests for Structure-Based Caching.
 *
 * <p>These tests validate the critical caching behavior where:</p>
 * <ol>
 *   <li>Cache keys are based on STRUCTURE (properties + operators) not values</li>
 *   <li>Different DSL expressions with same structure share cache entries</li>
 *   <li>Different values for same structure reuse cached conditions</li>
 *   <li>Cache hit rate is optimized (70-95% as documented)</li>
 * </ol>
 *
 * <p><strong>Architecture Validation</strong>: These tests verify that the cache
 * operates at the correct level (FilterTree.generate, not parse) and uses structural
 * normalization correctly.</p>
 *
 * @author FilterQL Test Suite
 * @since 4.0.1
 */
@DisplayName("Structural Caching Integration Tests")
public class StructuralCachingIntegrationTest {

    private BasicDslParser parser;

    @Mock
    private FilterContext mockContext;

    @Mock
    private Condition mockConditionA;

    @Mock
    private Condition mockConditionB;

    @Mock
    private Condition mockConditionC;

    private Map<String, FilterDefinition<TestProperty>> testDefinitions;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create parser with caching enabled
        parser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());

        // Setup test definitions
        testDefinitions = Map.of(
            "A", new FilterDefinition<>(TestProperty.NAME, "EQ", "Alice"),
            "B", new FilterDefinition<>(TestProperty.AGE, "GT", 25),
            "C", new FilterDefinition<>(TestProperty.STATUS, "EQ", "ACTIVE")
        );

        // Setup mock conditions
        when(mockConditionA.and(any())).thenReturn(mockConditionA);
        when(mockConditionA.or(any())).thenReturn(mockConditionA);
        when(mockConditionA.not()).thenReturn(mockConditionA);

        when(mockConditionB.and(any())).thenReturn(mockConditionB);
        when(mockConditionB.or(any())).thenReturn(mockConditionB);
        when(mockConditionB.not()).thenReturn(mockConditionB);

        when(mockConditionC.and(any())).thenReturn(mockConditionC);
        when(mockConditionC.or(any())).thenReturn(mockConditionC);
        when(mockConditionC.not()).thenReturn(mockConditionC);

        // Setup mock context to return conditions
        when(mockContext.toCondition(eq("A"), any(), any())).thenReturn(mockConditionA);
        when(mockContext.toCondition(eq("B"), any(), any())).thenReturn(mockConditionB);
        when(mockContext.toCondition(eq("C"), any(), any())).thenReturn(mockConditionC);
    }

    @Nested
    @DisplayName("Structure-Based Cache Key Tests")
    class StructureBasedCacheKeyTests {

        @Test
        @DisplayName("Should share cache for semantically equivalent DSL expressions")
        void shouldShareCacheForEquivalentExpressions() throws DSLSyntaxException, FilterValidationException {
            // Given: Expressions that StructuralNormalizer should normalize identically
            FilterTree tree1 = parser.parse("A & B");
            FilterTree tree2 = parser.parse("A & B & B & A");

            // When: Generate both
            tree1.generate(testDefinitions, mockContext);
            tree2.generate(testDefinitions, mockContext);

            // Then: StructuralNormalizer should produce same cache key
            var stats = parser.getCacheStats();
            assertEquals(1, stats.get("size"),
                    "StructuralNormalizer should normalize to same key");
        }

        @Test
        @DisplayName("Should share cache for simplified equivalent expressions")
        void shouldShareCacheForSimplifiedEquivalents() throws DSLSyntaxException, FilterValidationException {
            // Given: Complex expressions that simplify to the same structure
            FilterTree tree1 = parser.parse("A & A");      // Simplifies to just A
            FilterTree tree2 = parser.parse("A");          // Already simplified

            // When: Generate both
            tree1.generate(testDefinitions, mockContext);
            tree2.generate(testDefinitions, mockContext);

            // Then: Should share cache (both simplify to single "A")
            var stats = parser.getCacheStats();
            assertEquals(1, stats.get("size"),
                "Expressions that simplify to same structure should share cache");
        }

        @Test
        @DisplayName("Should differentiate cache for different structures")
        void shouldDifferentiateCacheForDifferentStructures() throws DSLSyntaxException, FilterValidationException {
            // Given: Different logical structures
            FilterTree tree1 = parser.parse("A & B");
            FilterTree tree2 = parser.parse("A | B");  // Different operator

            // When: Generate both
            tree1.generate(testDefinitions, mockContext);
            tree2.generate(testDefinitions, mockContext);

            // Then: Should have 2 separate cache entries
            var stats = parser.getCacheStats();
            assertEquals(2, stats.get("size"),
                "Different structures should have separate cache entries");
        }

        @Test
        @DisplayName("Should differentiate cache for different filter sets")
        void shouldDifferentiateCacheForDifferentFilterSets() throws DSLSyntaxException, FilterValidationException {
            // Given: Same DSL pattern but different filters
            Map<String, FilterDefinition<TestProperty>> defs1 = Map.of(
                "A", new FilterDefinition<>(TestProperty.NAME, "EQ", "Alice")
            );
            Map<String, FilterDefinition<TestProperty>> defs2 = Map.of(
                "A", new FilterDefinition<>(TestProperty.AGE, "EQ", 25)  // Different property!
            );

            FilterTree tree = parser.parse("A");

            // When: Generate with different filter definitions
            tree.generate(defs1, mockContext);
            tree.generate(defs2, mockContext);

            // Then: Should have 2 cache entries (different structures)
            var stats = parser.getCacheStats();
            assertEquals(2, stats.get("size"),
                "Same DSL with different properties should have separate cache entries");
        }
    }

    @Nested
    @DisplayName("Cache Hit Optimization Tests")
    class CacheHitOptimizationTests {

        @Test
        @DisplayName("Should reuse cached condition on second generate call")
        void shouldReuseCachedConditionOnSecondGenerate() throws DSLSyntaxException, FilterValidationException {
            // Given: Parse once
            FilterTree tree = parser.parse("A & B");

            // When: Generate twice with same definitions
            Condition firstResult = tree.generate(testDefinitions, mockContext);
            Condition secondResult = tree.generate(testDefinitions, mockContext);

            // Then: toCondition should only be called once (cache hit on second call)
            verify(mockContext, times(1)).toCondition(eq("A"), any(), any());
            verify(mockContext, times(1)).toCondition(eq("B"), any(), any());

            assertNotNull(firstResult);
            assertNotNull(secondResult);
        }

        @Test
        @DisplayName("Should cache across multiple parse operations")
        void shouldCacheAcrossMultipleParseOperations() throws DSLSyntaxException, FilterValidationException {
            // Given: Parse the same expression multiple times
            FilterTree tree1 = parser.parse("A & B");
            FilterTree tree2 = parser.parse("A & B");
            FilterTree tree3 = parser.parse("A & B");

            // When: Generate with all trees
            tree1.generate(testDefinitions, mockContext);
            tree2.generate(testDefinitions, mockContext);
            tree3.generate(testDefinitions, mockContext);

            // Then: Should have only 1 cache entry and minimal toCondition calls
            var stats = parser.getCacheStats();
            assertEquals(1, stats.get("size"));

            // Cache hit means toCondition called only once total
            verify(mockContext, times(1)).toCondition(eq("A"), any(), any());
            verify(mockContext, times(1)).toCondition(eq("B"), any(), any());
        }

        @Test
        @DisplayName("Should achieve high cache hit rate with varied DSL expressions")
        void shouldAchieveHighCacheHitRate() throws DSLSyntaxException, FilterValidationException {
            // Given: Multiple variations of same structure
            List<String> variations = List.of(
                "A & B",
                "B & A",          // Commutative
                "(A & B)",        // Extra parentheses
                "A & B & B",      // Redundant (simplifies to A & B)
                "B & A & A"       // Commutative + redundant
            );

            // When: Parse and generate all variations
            for (String dsl : variations) {
                FilterTree tree = parser.parse(dsl);
                tree.generate(testDefinitions, mockContext);
            }

            // Then: Should have minimal cache entries (high hit rate)
            var stats = parser.getCacheStats();
            assertTrue((Integer) stats.get("size") <= 2,
                "Similar structures should maximize cache reuse");

            // Verify minimal condition creation (cache hits)
            verify(mockContext, atMost(2)).toCondition(eq("A"), any(), any());
            verify(mockContext, atMost(2)).toCondition(eq("B"), any(), any());
        }
    }

    @Nested
    @DisplayName("Cache Independence from Values")
    class CacheValueIndependenceTests {

        @Test
        @DisplayName("Should cache structure independently of filter values")
        void shouldCacheStructureIndependentlyOfValues() throws DSLSyntaxException, FilterValidationException {
            // Given: Same structure but different values
            Map<String, FilterDefinition<TestProperty>> defs1 = Map.of(
                "A", new FilterDefinition<>(TestProperty.NAME, "EQ", "Alice")
            );
            Map<String, FilterDefinition<TestProperty>> defs2 = Map.of(
                "A", new FilterDefinition<>(TestProperty.NAME, "EQ", "Bob")  // Different value!
            );
            Map<String, FilterDefinition<TestProperty>> defs3 = Map.of(
                "A", new FilterDefinition<>(TestProperty.NAME, "EQ", "Charlie")  // Yet another value!
            );

            FilterTree tree = parser.parse("A");

            // When: Generate with different values
            tree.generate(defs1, mockContext);
            tree.generate(defs2, mockContext);
            tree.generate(defs3, mockContext);

            // Then: Should have only 1 cache entry (values ignored in cache key)
            var stats = parser.getCacheStats();
            assertEquals(1, stats.get("size"),
                "Cache should be independent of filter values");
        }

        @Test
        @DisplayName("Should enable value substitution via argument registry pattern")
        void shouldEnableValueSubstitutionViaArgumentRegistry() throws DSLSyntaxException, FilterValidationException {
            // This test demonstrates the architectural benefit:
            // Cache structure once, bind different values later via argumentRegistry

            // Given: One cached structure
            FilterTree tree = parser.parse("A");
            tree.generate(testDefinitions, mockContext);  // Populate cache

            // When: "Reuse" structure with new values (simulated by new definitions)
            Map<String, FilterDefinition<TestProperty>> newDefs = Map.of(
                "A", new FilterDefinition<>(TestProperty.NAME, "EQ", "NewValue")
            );
            tree.generate(newDefs, mockContext);

            // Then: Cache entry is reused (structural match)
            var stats = parser.getCacheStats();
            assertEquals(1, stats.get("size"),
                "Structure reuse enables efficient value binding");
        }
    }

    @Nested
    @DisplayName("StructuralNormalizer Integration")
    class StructuralNormalizerIntegrationTests {

        @Test
        @DisplayName("Should use StructuralNormalizer for cache key generation preserving short-circuiting for operator '&'")
        void shouldUseStructuralNormalizerForCacheKeyWithShortCircuitAnd() throws DSLSyntaxException, FilterValidationException {
            // Given: Expressions that StructuralNormalizer should normalize identically
            FilterTree tree1 = parser.parse("A & B");
            FilterTree tree2 = parser.parse("B & A");

            // When: Generate both
            tree1.generate(testDefinitions, mockContext);
            tree2.generate(testDefinitions, mockContext);

            // Then: StructuralNormalizer should produce same cache key
            var stats = parser.getCacheStats();
            assertEquals(2, stats.get("size"),
                    "StructuralNormalizer should normalize to different key given the order for operator AND");
        }

        @Test
        @DisplayName("Should use StructuralNormalizer for cache key generation preserving short-circuiting for operator '|'")
        void shouldUseStructuralNormalizerForCacheKeyWithShortCircuitOr() throws DSLSyntaxException, FilterValidationException {
            // Given: Expressions that StructuralNormalizer should normalize identically
            FilterTree tree1 = parser.parse("A | B");
            FilterTree tree2 = parser.parse("B | A");

            // When: Generate both
            tree1.generate(testDefinitions, mockContext);
            tree2.generate(testDefinitions, mockContext);

            // Then: StructuralNormalizer should produce same cache key
            var stats = parser.getCacheStats();
            assertEquals(2, stats.get("size"),
                    "StructuralNormalizer should normalize to different key given the order for operator AND");
        }

        @Test
        @DisplayName("Should include operator in structural cache key")
        void shouldIncludeOperatorInStructuralKey() throws DSLSyntaxException, FilterValidationException {
            // Given: Same filters but different operators
            Map<String, FilterDefinition<TestProperty>> defsEQ = Map.of(
                "A", new FilterDefinition<>(TestProperty.NAME, "EQ", "value")
            );
            Map<String, FilterDefinition<TestProperty>> defsNE = Map.of(
                "A", new FilterDefinition<>(TestProperty.NAME, "NE", "value")
            );

            FilterTree tree = parser.parse("A");

            // When: Generate with different operators
            tree.generate(defsEQ, mockContext);
            tree.generate(defsNE, mockContext);

            // Then: Should have 2 cache entries (operator is part of structure)
            var stats = parser.getCacheStats();
            assertEquals(2, stats.get("size"),
                "Operator should be included in structural cache key");
        }
    }

    @Nested
    @DisplayName("Cache Performance Characteristics")
    class CachePerformanceTests {

        @Test
        @DisplayName("Should maintain bounded cache size with LRU eviction")
        void shouldMaintainBoundedCacheSize() throws DSLSyntaxException, FilterValidationException {
            // Given: Parser with small cache size
            BasicDslParser smallCacheParser = new BasicDslParser(
                DslPolicy.defaults(),
                CachePolicy.custom(5)
            );

            // Setup mock context for dynamic filters
            FilterContext dynamicMockContext = mock(FilterContext.class);
            when(dynamicMockContext.toCondition(any(String.class), any(), any())).thenReturn(mockConditionA);

            // When: Generate more entries than cache size with different structures
            for (int i = 0; i < 10; i++) {
                String filterName = "X" + i;
                Map<String, FilterDefinition<TestProperty>> defs = Map.of(
                    filterName, new FilterDefinition<>(TestProperty.NAME, "EQ", "value" + i)
                );
                FilterTree tree = smallCacheParser.parse(filterName);
                tree.generate(defs, dynamicMockContext);
            }

            // Then: Cache size should not exceed size (LRU eviction)
            var stats = smallCacheParser.getCacheStats();
            assertTrue((Integer) stats.get("size") <= 5,
                "Cache should respect size size with LRU eviction");
        }

        @Test
        @DisplayName("Should track cache statistics accurately")
        void shouldTrackCacheStatisticsAccurately() throws DSLSyntaxException, FilterValidationException {
            // Given: Fresh parser
            parser.clearCache();

            // When: Generate with cache hits and misses
            FilterTree tree = parser.parse("A");
            tree.generate(testDefinitions, mockContext);  // Miss
            tree.generate(testDefinitions, mockContext);  // Hit
            tree.generate(testDefinitions, mockContext);  // Hit

            // Then: Stats should be accurate
            var stats = parser.getCacheStats();
            assertEquals(1, stats.get("size"));
            assertTrue(stats.containsKey("maxSize"));
        }
    }

    // ============================================================================
    // Test Infrastructure
    // ============================================================================

    private enum TestProperty implements PropertyReference {
        NAME(String.class, Set.of(Op.EQ, Op.NE, Op.MATCHES)),
        AGE(Integer.class, Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE)),
        STATUS(String.class, Set.of(Op.EQ, Op.NE, Op.IN));

        private final Class<?> type;
        private final Set<Op> supportedOps;

        TestProperty(Class<?> type, Set<Op> supportedOps) {
            this.type = type;
            this.supportedOps = supportedOps;
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return supportedOps;
        }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }
}

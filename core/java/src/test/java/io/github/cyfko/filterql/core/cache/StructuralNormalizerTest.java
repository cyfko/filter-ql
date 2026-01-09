package io.github.cyfko.filterql.core.cache;

import io.github.cyfko.filterql.core.config.DslReservedSymbol;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StructuralNormalizer}.
 */
@DisplayName("StructuralNormalizer Tests")
class StructuralNormalizerTest {

    private StructuralNormalizer normalizer;

    enum TestPropertyRef implements PropertyReference {
        STATUS(String.class, Set.of(Op.EQ, Op.NE, Op.IN)),
        TIER(String.class, Set.of(Op.EQ, Op.NE)),
        ACTIVE(Boolean.class, Set.of(Op.EQ)),
        AGE(Integer.class, Set.of(Op.EQ, Op.GT, Op.LT));

        private final Class<?> type;
        private final Set<Op> ops;

        TestPropertyRef(Class<?> type, Set<Op> ops) {
            this.type = type;
            this.ops = ops;
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return ops;
        }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        normalizer = new StructuralNormalizer();
    }

    @Test
    @DisplayName("Should normalize example from specification")
    void testSpecificationExample() {
        // Given: the example from the class documentation
        Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
        filters.put("filtre1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE"));
        filters.put("filtre2", new FilterDefinition<>(TestPropertyRef.TIER, Op.EQ, "PREMIUM"));
        filters.put("filtre3", new FilterDefinition<>(TestPropertyRef.ACTIVE, Op.EQ, true));
        filters.put("filtre4", new FilterDefinition<>(TestPropertyRef.STATUS, Op.NE, "BANNED"));

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "(((filtre1)) & filtre2 | (!filtre3 & filtre4) & filtre1)",
            null,
            null
        );

        // When
        String normalized = normalizer.normalize(request);

        // Then
        assertTrue(normalized.contains("STATUS:EQ"));
        assertTrue(normalized.contains("TIER:EQ"));
        assertTrue(normalized.contains("ACTIVE:EQ"));
        assertTrue(normalized.contains("STATUS:NE"));
    }

    @Test
    @DisplayName("Should produce same key for same structure with different values")
    void testValueIndependence() {
        // Given: same structure, different values
        Map<String, FilterDefinition<TestPropertyRef>> filters1 = Map.of(
            "nameFilter", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        Map<String, FilterDefinition<TestPropertyRef>> filters2 = Map.of(
            "nameFilter", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "INACTIVE")
        );

        FilterRequest<TestPropertyRef> request1 = new FilterRequest<>(filters1, "nameFilter",null, null);
        FilterRequest<TestPropertyRef> request2 = new FilterRequest<>(filters2, "nameFilter",null, null);

        // When
        String key1 = normalizer.normalize(request1);
        String key2 = normalizer.normalize(request2);

        // Then: should be identical (values ignored)
        assertEquals(key1, key2);
    }

    @Test
    @DisplayName("Should produce same key for same structure with different filter names")
    void testNameIndependence() {
        // Given: same structure, different names
        Map<String, FilterDefinition<TestPropertyRef>> filters1 = Map.of(
            "userFilter", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        Map<String, FilterDefinition<TestPropertyRef>> filters2 = Map.of(
            "statusSearch", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        FilterRequest<TestPropertyRef> request1 = new FilterRequest<>(filters1, "userFilter",null, null);
        FilterRequest<TestPropertyRef> request2 = new FilterRequest<>(filters2, "statusSearch",null, null);

        // When
        String key1 = normalizer.normalize(request1);
        String key2 = normalizer.normalize(request2);

        // Then: should be identical (names ignored)
        assertEquals(key1, key2);
    }

    @Test
    @DisplayName("Should produce different keys for different structures")
    void testStructuralDifference() {
        // Given: different structures
        Map<String, FilterDefinition<TestPropertyRef>> filters1 = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        Map<String, FilterDefinition<TestPropertyRef>> filters2 = Map.of(
            "f2", new FilterDefinition<>(TestPropertyRef.TIER, Op.EQ, "PREMIUM")
        );

        FilterRequest<TestPropertyRef> request1 = new FilterRequest<>(filters1, "f1",null, null);
        FilterRequest<TestPropertyRef> request2 = new FilterRequest<>(filters2, "f2",null, null);

        // When
        String key1 = normalizer.normalize(request1);
        String key2 = normalizer.normalize(request2);

        // Then: should be different (different property reference)
        assertNotEquals(key1, key2);
    }

    @Test
    @DisplayName("Should handle complex DSL expressions")
    void testComplexDSL() {
        // Given: complex DSL that won't fully simplify away
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "a", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE"),
            "b", new FilterDefinition<>(TestPropertyRef.TIER, Op.EQ, "PREMIUM"),
            "c", new FilterDefinition<>(TestPropertyRef.ACTIVE, Op.EQ, true)
        );

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "(a & b) | !c",  // Simpler expression that retains structure
            null,
            null
        );

        // When
        String normalized = normalizer.normalize(request);

        // Then: should normalize structure and preserve operators
        // (a & b) | !c -> STATUS:EQ TIER:EQ & ACTIVE:EQ ! |

        assertTrue(normalized.contains("STATUS:EQ"));
        assertTrue(normalized.contains("TIER:EQ"));
        assertTrue(normalized.contains("ACTIVE:EQ"));
    }

    @Test
    @DisplayName("Should deduplicate property:operator pairs")
    void testDeduplication() {
        // Given: filter used multiple times
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE"),
            "f2", new FilterDefinition<>(TestPropertyRef.TIER, Op.EQ, "PREMIUM")
        );

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "f1 & f2 & f1 & f1",  // f1 appears 3 times
            null,
            null
        );

        // When
        String normalized = normalizer.normalize(request);

        // Then: STATUS:EQ should appear only once in PropertyOps
        int statusEqCount = normalized.split("STATUS:EQ", -1).length - 1;
        assertEquals(1, statusEqCount, "STATUS:EQ should be deduplicated");
    }

    @Test
    @DisplayName("Should reject null request")
    void testNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(null));
    }

    @Test
    @DisplayName("Should normalize whitespace consistently")
    void testWhitespaceNormalization() {
        // Given: same structure with different whitespace
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE"),
            "f2", new FilterDefinition<>(TestPropertyRef.TIER, Op.EQ, "PREMIUM")
        );

        FilterRequest<TestPropertyRef> request1 = new FilterRequest<>(filters, "f1&f2", null, null);
        FilterRequest<TestPropertyRef> request2 = new FilterRequest<>(filters, "f1   &   f2",null, null);
        FilterRequest<TestPropertyRef> request3 = new FilterRequest<>(filters, "f1 & f2",null, null);

        // When
        String key1 = normalizer.normalize(request1);
        String key2 = normalizer.normalize(request2);
        String key3 = normalizer.normalize(request3);

        // Then: should all produce same normalized structure
        assertEquals(key1, key2);
        assertEquals(key1, key3);
    }

    // ========== Boolean Simplification Tests ==========

    @Test
    @DisplayName("Should apply idempotence in structure: f1 & f1 → f1")
    void testBooleanSimplificationIdempotenceAnd() {
        // Given: same filter ANDed with itself
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "f1 & f1",  // Idempotent expression
            null,
        null
        );

        // When
        String normalized = normalizer.normalize(request);

        // Then: structure should be simplified to STATUS:EQ (not STATUS:EQ STATUS:EQ &)

        assertEquals("STATUS:EQ", normalized, "f1 & f1 should simplify to STATUS:EQ");
    }

    @Test
    @DisplayName("Should apply idempotence in structure: f1 | f1 → f1")
    void testBooleanSimplificationIdempotenceOr() {
        // Given: same filter ORed with itself
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "f1 | f1",  // Idempotent expression
            null,
            null
        );

        // When
        String normalized = normalizer.normalize(request);

        assertEquals("STATUS:EQ", normalized, "f1 | f1 should simplify to f1");
    }

    @Test
    @DisplayName("Should apply cancellation in structure: f1 & !f1 → ⊥")
    void testBooleanSimplificationCancellationAnd() {
        // Given: filter ANDed with its negation
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "f1 & !f1",  // Contradiction
            null,
            null
        );

        // When
        String normalized = normalizer.normalize(request);

        // Then: structure should contain false symbol ⊥
        assertEquals(DslReservedSymbol.FALSE, normalized, "f1 & !f1 should simplify to ⊥ (false)");
    }

    @Test
    @DisplayName("Should apply cancellation in structure: f1 | !f1 → ⊤")
    void testBooleanSimplificationCancellationOr() {
        // Given: filter ORed with its negation
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE")
        );

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "f1 | !f1",  // Tautology
            null,
            null
        );

        // When
        String normalized = normalizer.normalize(request);

        // Then: structure should contain true symbol ⊤
        assertEquals(DslReservedSymbol.TRUE, normalized, "f1 | !f1 should simplify to ⊤ (true)");
    }

    @Test
    @DisplayName("Should apply multiple simplification rules together")
    void testBooleanSimplificationComplex() {
        // Given: complex expression with idempotence and redundant parentheses
        Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
        filters.put("f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE"));
        filters.put("f2", new FilterDefinition<>(TestPropertyRef.TIER, Op.EQ, "PREMIUM"));

        FilterRequest<TestPropertyRef> request = new FilterRequest<>(
            filters,
            "(((f1 & f1)) & (f2 & f2))",  // Multiple parentheses + idempotence
            null,
            null
        );

        // When
        String normalized = normalizer.normalize(request);

        // Then: should be heavily simplified
        // (((f1 & f1)) & (f2 & f2))
        // -> (((f1)) & (f2))    [simplify]
        // -> ((f1) & f2)        [remove parenthesis]
        // -> (f1 & f2)          [remove parentheses]
        // -> f1 f2 &            [normalized to "STATUS:EQ ACTIVE:EQ &"]

        assertEquals("STATUS:EQ TIER:EQ &", normalized);
    }

    @Test
    @DisplayName("Should verify simplified structure still maintains logical equivalence")
    void testBooleanSimplificationEquivalence() {
        // Given: expression before and after should be logically equivalent
        Map<String, FilterDefinition<TestPropertyRef>> filters = Map.of(
            "f1", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE"),
            "f2", new FilterDefinition<>(TestPropertyRef.TIER, Op.EQ, "PREMIUM")
        );

        // Both expressions are logically equivalent: f1 & f2
        FilterRequest<TestPropertyRef> request1 = new FilterRequest<>(filters, "f1 & f2", null, null);
        FilterRequest<TestPropertyRef> request2 = new FilterRequest<>(filters, "(f1 & f1) & (f2 & f2)", null, null);

        // When
        String key1 = normalizer.normalize(request1);
        String key2 = normalizer.normalize(request2);

        // Then: should produce identical keys (logically equivalent)
        assertEquals(key1, key2, "Logically equivalent expressions should produce same normalized key");
    }
}

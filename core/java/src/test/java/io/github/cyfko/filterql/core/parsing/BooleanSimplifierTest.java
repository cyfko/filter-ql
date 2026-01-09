package io.github.cyfko.filterql.core.parsing;

import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.config.PatternConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link BooleanSimplifier}.
 * <p>
 * Verifies that boolean algebra simplification rules are correctly applied:
 * </p>
 * <ul>
 *   <li>Idempotence: A & A → A, A | A → A</li>
 *   <li>Cancellation: A & !A → ⊥, A | !A → ⊤</li>
 *   <li>Unnecessary parentheses removal</li>
 *   <li>Identity and annihilation with constants</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
@DisplayName("BooleanSimplifier Tests")
class BooleanSimplifierTest {

    // ========== Idempotence Tests ==========

    @Test
    @DisplayName("Should simplify F&F to F (AND idempotence)")
    void testIdempotenceAnd() {
        assertEquals("F", BooleanSimplifier.simplify("F&F"));
    }

    @Test
    @DisplayName("Should simplify F|F to F (OR idempotence)")
    void testIdempotenceOr() {
        assertEquals("F", BooleanSimplifier.simplify("F|F"));
    }

    @Test
    @DisplayName("Should simplify F&F&F to F (multiple AND idempotence)")
    void testIdempotenceAndMultiple() {
        assertEquals("F", BooleanSimplifier.simplify("F&F&F"));
    }

    @Test
    @DisplayName("Should simplify F|F|F to F (multiple OR idempotence)")
    void testIdempotenceOrMultiple() {
        assertEquals("F", BooleanSimplifier.simplify("F|F|F"));
    }

    @Test
    @DisplayName("Should simplify F&F&F&F&F to F (many AND idempotence)")
    void testIdempotenceAndMany() {
        assertEquals("F", BooleanSimplifier.simplify("F&F&F&F&F"));
    }

    // ========== Parentheses Removal Tests ==========

    @Test
    @DisplayName("Should simplify ((F)) to F (double parentheses)")
    void testRemoveDoubleParentheses() {
        assertEquals("F", BooleanSimplifier.simplify("((F))"));
    }

    @Test
    @DisplayName("Should simplify (((F))) to F (triple parentheses)")
    void testRemoveTripleParentheses() {
        assertEquals("F", BooleanSimplifier.simplify("(((F)))"));
    }

    @Test
    @DisplayName("Should simplify (F) to F (single parentheses)")
    void testRemoveSingleParentheses() {
        assertEquals("F", BooleanSimplifier.simplify("(F)"));
    }

    @Test
    @DisplayName("Should simplify (((F&G))) to F&G (nested with operation)")
    void testRemoveParenthesesWithOperation() {
        assertEquals("F G &", BooleanSimplifier.simplify("(((F&G)))"));
    }

    @Test
    @DisplayName("Should simplify ((F&F)) to F (parentheses + idempotence)")
    void testRemoveParenthesesWithIdempotence() {
        assertEquals("F", BooleanSimplifier.simplify("((F&F))"));
    }

    // ========== Cancellation Tests ==========

    @Test
    @DisplayName("Should simplify F&!F to ⊥ (AND cancellation)")
    void testCancellationAnd() {
        assertEquals("⊥", BooleanSimplifier.simplify("F&!F"));
    }

    @Test
    @DisplayName("Should simplify !F&F to ⊥ (AND cancellation symmetric)")
    void testCancellationAndSymmetric() {
        assertEquals("⊥", BooleanSimplifier.simplify("!F&F"));
    }

    @Test
    @DisplayName("Should simplify F|!F to ⊤ (OR cancellation)")
    void testCancellationOr() {
        assertEquals("⊤", BooleanSimplifier.simplify("F|!F"));
    }

    @Test
    @DisplayName("Should simplify !F|F to ⊤ (OR cancellation symmetric)")
    void testCancellationOrSymmetric() {
        assertEquals("⊤", BooleanSimplifier.simplify("!F|F"));
    }

    @Test
    @DisplayName("Should simplify (F&!F) to ⊥ (AND cancellation with parentheses)")
    void testCancellationAndWithParentheses() {
        assertEquals("⊥", BooleanSimplifier.simplify("(F&!F)"));
    }

    @Test
    @DisplayName("Should simplify (F|!F) to ⊤ (OR cancellation with parentheses)")
    void testCancellationOrWithParentheses() {
        assertEquals("⊤", BooleanSimplifier.simplify("(F|!F)"));
    }

    // ========== Complex Expression Tests ==========

    @Test
    @DisplayName("Should simplify (F&!F)|G to G (cancellation + identity)")
    void testComplexCancellationWithOr() {
        // F&!F → ⊥, then ⊥|G → G
        assertEquals("G", BooleanSimplifier.simplify("(F&!F)|G"));
    }

    @Test
    @DisplayName("Should simplify (F|!F)&G to G (cancellation + identity)")
    void testComplexCancellationWithAnd() {
        // F|!F → ⊤, then ⊤&G → G
        assertEquals("G", BooleanSimplifier.simplify("(F|!F)&G"));
    }

    @Test
    @DisplayName("Should simplify ((F&F)|(G&G)) to F|G (multiple idempotence)")
    void testComplexIdempotence() {
        assertEquals("F G |", BooleanSimplifier.simplify("((F&F)|(G&G))"));
    }

    @Test
    @DisplayName("Should simplify (((F&F))&((G&G))) to F&G (nested idempotence)")
    void testComplexNestedIdempotence() {
        assertEquals("F G &", BooleanSimplifier.simplify("(((F&F))&((G&G)))"));
    }

    @Test
    @DisplayName("Should simplify complex nested expression with all rules")
    void testComplexAllRules() {
        // (((F&F)) & G) | (H&!H)
        // → ((F & G) | ⊥)
        // → (F & G) | ⊥
        // → F&G
        assertEquals("F G &", BooleanSimplifier.simplify("(((F&F))&G)|(H&!H)"));
    }

    @Test
    @DisplayName("Should handle expression from documentation example")
    void testDocumentationExample() {
        // From StructuralNormalizer example: (((filtre1)) & filtre2 | (!filtre3 & filtre4) & filtre1)
        // After normalization: (((F))&F|((!F&F)&F))
        // Simplified: ((F&F)|((!F&F)&F)) → (F|(!F&F)&F)
        String input = "(((F))&F|((!F&F)&F))";
        String simplified = BooleanSimplifier.simplify(input);

        // Verify it doesn't contain triple parentheses anymore
        assertFalse(simplified.contains("((("));

        // Verify it's been simplified in some way
        assertTrue(simplified.length() < input.length());
    }

    // ========== Edge Cases Tests ==========

    @Test
    @DisplayName("Should preserve single variable F")
    void testSingleVariable() {
        assertEquals("F", BooleanSimplifier.simplify("F"));
    }

    @Test
    @DisplayName("Should preserve negated variable !F")
    void testNegatedVariable() {
        assertEquals("F !", BooleanSimplifier.simplify("!F"));
    }

    @Test
    @DisplayName("Should preserve F&G (no simplification possible)")
    void testNoSimplificationNeeded() {
        assertEquals("F G &", BooleanSimplifier.simplify("F&G"));
    }

    @Test
    @DisplayName("Should preserve F|G (no simplification possible)")
    void testNoSimplificationNeededOr() {
        assertEquals("F G |", BooleanSimplifier.simplify("F|G"));
    }

    // ========== Fixed-Point Iteration Tests ==========

    @Test
    @DisplayName("Should apply rules repeatedly until fixed point")
    void testFixedPointIteration() {
        // Start: ((((F&F))&F))
        // Iter 1: (((F&F)))
        // Iter 2: ((F))
        // Iter 3: F
        assertEquals("F", BooleanSimplifier.simplify("((((F&F))&F))"));
    }

    @Test
    @DisplayName("Should handle deeply nested expression with multiple rule applications")
    void testDeeplyNestedExpression() {
        // (((((F&F)&(G&G)))&((H|H))))
        // Should simplify to: F&G&H
        String simplified = BooleanSimplifier.simplify("(((((F&F)&(G&G)))&((H|H))))");
        assertEquals("F G & H &", simplified);
    }

    // ========== Utility Methods Tests ==========

//    @Test
//    @DisplayName("containsConstants should return true for ⊥")
//    @Disabled
//    void testContainsConstantsFalse() {
//        assertTrue(BooleanSimplifier.containsConstants("F&⊥"));
//    }
//
//    @Test
//    @DisplayName("containsConstants should return true for ⊤")
//    void testContainsConstantsTrue() {
//        assertTrue(BooleanSimplifier.containsConstants("F|⊤"));
//    }
//
//    @Test
//    @DisplayName("containsConstants should return false for expression without constants")
//    void testContainsConstantsNone() {
//        assertFalse(BooleanSimplifier.containsConstants("F&G|H"));
//    }
//
//    @Test
//    @DisplayName("containsConstants should return false for null")
//    void testContainsConstantsNull() {
//        assertFalse(BooleanSimplifier.containsConstants(null));
//    }
//
//    @Test
//    @DisplayName("getFalseSymbol should return ⊥")
//    void testGetFalseSymbol() {
//        assertEquals("⊥", BooleanSimplifier.getFalseSymbol());
//    }
//
//    @Test
//    @DisplayName("getTrueSymbol should return ⊤")
//    void testGetTrueSymbol() {
//        assertEquals("⊤", BooleanSimplifier.getTrueSymbol());
//    }

    // ========== Parameterized Tests ==========

    @ParameterizedTest
    @CsvSource({
        "F&F, F",
        "F|F, F",
        "F&F&F, F",
        "F|F|F, F",
        "(F), F",
        "((F)), F",
        "(((F))), F",
        "F&!F, ⊥",
        "!F&F, ⊥",
        "F|!F, ⊤",
        "!F|F, ⊤",
    })
    @DisplayName("Should correctly simplify various boolean expressions")
    void testVariousSimplifications(String input, String expected) {
        assertEquals(expected, BooleanSimplifier.simplify(input));
    }

    @Test
    @DisplayName("Should simplify real user expression with original filter names")
    void testRealUserComplexExpression() {
        // Given: expression from user's combineWith
        // (((filtre1))&filtre2|(!filtre3&filtre4)&filtre1)
        String input = "(((filtre1))&filtre2|(!filtre3&filtre4)&filtre1)";

        // When: simplify with original names
        String result = BooleanSimplifier.simplify(input);

        // Then: should preserve filter distinctions and simplify structure
        // After simplification: (filtre1&filtre2) | (!filtre3&filtre4&filtre1)
        // After alphabetical sorting: !filtre3&filtre1&filtre4 | filtre1&filtre2
        assertEquals("filtre1 filtre2 & filtre3 ! filtre4 & filtre1 & |", result);
    }

    @Test
    @DisplayName("Should simplify real user expression with original filter names")
    void testRealUserComplexExpressionThatSimplify() {
        // Given: expression from user's combineWith
        // (((filtre1))&filtre2|(!filtre3&filtre4)&filtre1)
        String input = "(((filtre1))&filtre2&(filtre3&filtre4)&filtre1)";

        // When: simplify with original names
        String result = BooleanSimplifier.simplify(input);

        // Then: should preserve filter distinctions and simplify structure
        assertEquals("filtre1 filtre2 & filtre3 filtre4 & & filtre1 &", result);
    }

    @Test
    @DisplayName("Should simplify complex expression that evaluates to true")
    void testHeavyExpression() {
        // Given: expression
        String input = "(!(!A & (B | C)) & ((A & B) | (!A & D))) | ((E & E) | (!E & (F | !F))) & (⊤ | (G & ⊥))\n";

        // When: simplifying
        String result = BooleanSimplifier.simplify(input, DslPolicy.builder()
                .identifierPattern(PatternConfig.SIMPLE_PATTERN_WITH_RESERVED)
                .build());

        // Then: should produce the token of an always true expression
        assertEquals("⊤", result);
    }
}

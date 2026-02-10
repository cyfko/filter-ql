package io.github.cyfko.filterql.core.impl;

import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DSL Parser complexity limits (DoS protection).
 * <p>
 * Validates that {@link BasicDslParser} properly enforces {@link DslPolicy}
 * limits to prevent Denial of Service attacks through overly complex expressions.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
@DisplayName("DSLParser Complexity Limits Tests")
class BasicDslParserComplexityLimitsTest {

    // ==================== Expression Length Tests ====================

    @Test
    @DisplayName("Should reject expression exceeding maxExpressionLength")
    void testExpressionLengthExceeded() {
        // Given: strict config with 1000 char size
        DslPolicy strictConfig = DslPolicy.strict();
        BasicDslParser parser = new BasicDslParser(strictConfig);

        // When: expression is 1100 characters (exceeds size)
        String longExpression = "a".repeat(1100);

        // Then: should throw with clear message
        DSLSyntaxException exception = assertThrows(
            DSLSyntaxException.class,
            () -> parser.parse(longExpression)
        );

        assertTrue(exception.getMessage().contains("Expression too long"));
        assertTrue(exception.getMessage().contains("1100 characters"));
        assertTrue(exception.getMessage().contains("max: 1000"));
    }

    @Test
    @DisplayName("Should accept expression within maxExpressionLength")
    void testExpressionLengthWithinLimit() {
        // Given: strict config with 1000 char size
        DslPolicy strictConfig = DslPolicy.strict();
        BasicDslParser parser = new BasicDslParser(strictConfig);

        // When: expression is 500 characters (within size)
        String expression = "filter1 & filter2";

        // Then: should not throw
        assertDoesNotThrow(() -> parser.parse(expression));
    }

    @Test
    @DisplayName("Should accept expression at exact maxExpressionLength")
    void testExpressionLengthAtLimit() {
        // Given: custom config with 20 char size
        DslPolicy customConfig = DslPolicy.builder()
                .maxExpressionLength(20)
                .build();
        BasicDslParser parser = new BasicDslParser(customConfig);

        // When: expression is exactly 20 characters
        String expression = "a & b | c & d | e      & e ".substring(0, 20); // "a & b | c & d | e "

        // Then: should not throw (trimmed to 18, within size)
        assertDoesNotThrow(() -> parser.parse(expression.trim()));
    }

    // ==================== Token Count Tests ====================

    @Test
    @DisplayName("Should accept expression within maxTokens")
    void testTokenCountWithinLimit() {
        // Given: default config with 200 tokens max
        BasicDslParser parser = new BasicDslParser();

        // When: expression has 10 tokens (5 filters + 4 operators + 1 parenthesis pair)
        String expression = "(f1 & f2) | (f3 & f4)";
        // Tokens: ( f1 & f2 ) | ( f3 & f4 ) = 11 tokens

        // Then: should not throw
        assertDoesNotThrow(() -> parser.parse(expression));
    }

    // ==================== Nesting Depth Tests ====================

    @Test
    @DisplayName("Should accept expression within maxDepth")
    void testNestingDepthWithinLimit() {
        // Given: default config with 50 depth size
        BasicDslParser parser = new BasicDslParser();

        // When: create nested expression (10 levels, well within size)
        StringBuilder expression = new StringBuilder();
        expression.append("(".repeat(10));
        expression.append("a");
        expression.append(")".repeat(10));

        // Then: should not throw
        assertDoesNotThrow(() -> parser.parse(expression.toString()));
    }

    // ==================== Configuration Tests ====================

    @Test
    @DisplayName("Should use default config when constructed without parameters")
    void testDefaultConfiguration() {
        // Given
        BasicDslParser parser = new BasicDslParser();

        // When: expression within default limits (50 depth, 200 tokens, 5000 chars)
        StringBuilder expression = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            expression.append("(");
        }
        expression.append("a");
        for (int i = 0; i < 40; i++) {
            expression.append(")");
        }

        // Then: should not throw (within default depth of 50)
        assertDoesNotThrow(() -> parser.parse(expression.toString()));
    }

    @Test
    @Disabled
    @DisplayName("Should respect strict configuration limits")
    void testStrictConfiguration() {
        // Given: strict config (20 depth, 50 tokens, 1000 chars)
        BasicDslParser parser = new BasicDslParser(DslPolicy.strict());

        // When: expression exceeds strict limits but within default limits
        StringBuilder expression = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            expression.append("(");
        }
        expression.append("a");
        for (int i = 0; i < 25; i++) {
            expression.append(")");
        }

        // Then: should throw (exceeds strict depth of 20)
        assertThrows(DSLSyntaxException.class, () -> parser.parse(expression.toString()));
    }

    @Test
    @DisplayName("Should respect relaxed configuration limits")
    void testRelaxedConfiguration() {
        // Given: relaxed config (100 depth, 500 tokens, 10000 chars)
        BasicDslParser parser = new BasicDslParser(DslPolicy.relaxed());

        // When: expression within relaxed limits
        StringBuilder expression = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            expression.append("(");
        }
        expression.append("a");
        for (int i = 0; i < 80; i++) {
            expression.append(")");
        }

        // Then: should not throw (within relaxed depth of 100)
        assertDoesNotThrow(() -> parser.parse(expression.toString()));
    }

    @Test
    @DisplayName("Should reject null configuration")
    void testNullConfiguration() {
        // When/Then: should throw IllegalArgumentException
        IllegalArgumentException exception1 = assertThrows(
            IllegalArgumentException.class,
            () -> new BasicDslParser(null)
        );

        IllegalArgumentException exception2 = assertThrows(
                IllegalArgumentException.class,
                () -> new BasicDslParser(null)
        );

        IllegalArgumentException exception3 = assertThrows(
                IllegalArgumentException.class,
                () -> new BasicDslParser(DslPolicy.defaults(),null)
        );

        assertEquals("DSL policy is required", exception1.getMessage());
        assertEquals("DSL policy is required", exception2.getMessage());
        assertEquals("Cache policy is required", exception3.getMessage());
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle empty expression (caught before size checks)")
    void testEmptyExpression() {
        BasicDslParser parser = new BasicDslParser();

        // Empty expression is caught before complexity checks
        assertThrows(DSLSyntaxException.class, () -> parser.parse(""));
        assertThrows(DSLSyntaxException.class, () -> parser.parse("   "));
    }

    @Test
    @DisplayName("Should handle shorthand syntax without triggering limits")
    void testShorthandSyntaxBypassesLimits() {
        // Given: very strict config
        DslPolicy veryStrict = DslPolicy.builder()
                .maxExpressionLength(10)
                .build();
        BasicDslParser parser = new BasicDslParser(veryStrict);

        // When: use shorthand syntax (only 3 chars, 0 tokens parsed)
        // Then: should not throw (shorthand bypasses tokenization)
        assertDoesNotThrow(() -> parser.parse("AND"));
        assertDoesNotThrow(() -> parser.parse("OR"));
        assertDoesNotThrow(() -> parser.parse("NOT"));
    }

    @Test
    @DisplayName("Should enforce limits on complex real-world expressions")
    void testRealWorldComplexExpression() {
        // Given: default config
        BasicDslParser parser = new BasicDslParser();

        // When: realistic complex filter expression
        String realWorldExpression =
            "((status & activeUsers) | (pendingApproval & !suspended)) & " +
            "((createdAfter & createdBefore) | featured) & " +
            "(country | region | city) & " +
            "!(deleted | archived)";
        // Tokens: identifiers(15) + operators(17) + parentheses(18) = 50 tokens
        // Max depth: 2

        // Then: should not throw (within limits)
        assertDoesNotThrow(() -> parser.parse(realWorldExpression));
    }

    @Test
    @Disabled
    @DisplayName("Should provide specific error messages for each size type")
    void testErrorMessageClarity() {
        DslPolicy customConfig = DslPolicy.builder()
                .maxExpressionLength(50)
                .build();
        BasicDslParser parser = new BasicDslParser(customConfig);

        // Test expression length error
        String longExpression = "a".repeat(100);
        DSLSyntaxException lengthException = assertThrows(
            DSLSyntaxException.class,
            () -> parser.parse(longExpression)
        );
        assertTrue(lengthException.getMessage().contains("too long"));
    }
}

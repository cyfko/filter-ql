package io.github.cyfko.filterql.core.parsing;

import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link FastPostfixConverter}.
 * <p>
 * Tests the fast infix-to-postfix conversion with minimal validation (DoS protection only).
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.1
 */
@DisplayName("FastPostfixConverter Tests")
class FastPostfixConverterTest {

    @Nested
    @DisplayName("Valid Expressions - Conversion Tests")
    class ValidConversionTests {

        @Test
        @DisplayName("Simple identifier")
        void testSimpleIdentifier() {
            List<String> result = FastPostfixConverter.toPostfix("a", DslPolicy.strict());
            assertEquals(List.of("a"), result);
        }

        @Test
        @DisplayName("Simple AND expression")
        void testSimpleAnd() {
            List<String> result = FastPostfixConverter.toPostfix("a & b", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }

        @Test
        @DisplayName("Simple OR expression")
        void testSimpleOr() {
            List<String> result = FastPostfixConverter.toPostfix("a | b", DslPolicy.strict());
            assertEquals(List.of("a", "b", "|"), result);
        }

        @Test
        @DisplayName("Simple NOT expression")
        void testSimpleNot() {
            List<String> result = FastPostfixConverter.toPostfix("!a", DslPolicy.strict());
            assertEquals(List.of("a", "!"), result);
        }

        @Test
        @DisplayName("Expression with parentheses")
        void testWithParentheses() {
            List<String> result = FastPostfixConverter.toPostfix("(a & b)", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }

        @Test
        @DisplayName("Complex expression with precedence: a | b & c")
        void testPrecedence() {
            // a | b & c should be: a | (b & c) -> a b c & |
            List<String> result = FastPostfixConverter.toPostfix("a | b & c", DslPolicy.strict());
            assertEquals(List.of("a", "b", "c", "&", "|"), result);
        }

        @Test
        @DisplayName("Complex expression with parentheses: (a | b) & c")
        void testParenthesesOverridePrecedence() {
            List<String> result = FastPostfixConverter.toPostfix("(a | b) & c", DslPolicy.strict());
            assertEquals(List.of("a", "b", "|", "c", "&"), result);
        }

        @Test
        @DisplayName("Expression with NOT and parentheses: !(a & b)")
        void testNotWithParentheses() {
            List<String> result = FastPostfixConverter.toPostfix("!(a & b)", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&", "!"), result);
        }

        @Test
        @DisplayName("Complex expression: !(a & b) | c")
        void testComplexExpression() {
            List<String> result = FastPostfixConverter.toPostfix("!(a & b) | c", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&", "!", "c", "|"), result);
        }

        @Test
        @DisplayName("Left associativity for AND: a & b & c")
        void testLeftAssociativityAnd() {
            // a & b & c should be: (a & b) & c -> a b & c &
            List<String> result = FastPostfixConverter.toPostfix("a & b & c", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&", "c", "&"), result);
        }

        @Test
        @DisplayName("Left associativity for OR: a | b | c")
        void testLeftAssociativityOr() {
            // a | b | c should be: (a | b) | c -> a b | c |
            List<String> result = FastPostfixConverter.toPostfix("a | b | c", DslPolicy.strict());
            assertEquals(List.of("a", "b", "|", "c", "|"), result);
        }

        @Test
        @DisplayName("Right associativity for NOT: !!a")
        void testRightAssociativityNot() {
            List<String> result = FastPostfixConverter.toPostfix("!!a", DslPolicy.strict());
            assertEquals(List.of("a"), result);
        }

        @Test
        @DisplayName("Multiple nested parentheses: ((a & b))")
        void testNestedParentheses() {
            List<String> result = FastPostfixConverter.toPostfix("((a & b))", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }

        @Test
        @DisplayName("Expression with whitespace: a   &   b")
        void testWithWhitespace() {
            List<String> result = FastPostfixConverter.toPostfix("a   &   b", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }

        @ParameterizedTest
        @DisplayName("Valid identifiers (no validation in FastPostfixConverter)")
        @ValueSource(strings = {
            "a", "abc", "test123", "_test", "test_", "camelCase", "snake_case",
            "UPPERCASE", "123", "123abc", "a-b"  // FastPostfixConverter doesn't validate identifiers
        })
        void testValidIdentifiers(String identifier) {
            List<String> result = FastPostfixConverter.toPostfix(identifier, DslPolicy.strict());
            assertEquals(List.of(identifier), result);
        }

        @Test
        @DisplayName("Complex real-world expression")
        void testComplexRealWorld() {
            // (active & !deleted) | (premium & verified)
            List<String> result = FastPostfixConverter.toPostfix(
                "(active & !deleted) | (premium & verified)",
                DslPolicy.strict()
            );
            assertEquals(
                List.of("active", "deleted", "!", "&", "premium", "verified", "&", "|"),
                result
            );
        }
    }

    @Nested
    @DisplayName("DoS Protection Tests")
    class DosProtectionTests {

        @Test
        @DisplayName("Null expression should throw exception")
        void testNullExpression() {
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> FastPostfixConverter.toPostfix(null, DslPolicy.strict())
            );
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Empty expression should throw exception")
        void testEmptyExpression() {
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> FastPostfixConverter.toPostfix("", DslPolicy.strict())
            );
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Blank expression should throw exception")
        void testBlankExpression() {
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> FastPostfixConverter.toPostfix("   ", DslPolicy.strict())
            );
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Expression exceeding max length should throw exception")
        void testExpressionTooLong() {
            String longExpression = "a".repeat(1001);
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> FastPostfixConverter.toPostfix(longExpression, DslPolicy.strict())
            );
            assertTrue(exception.getMessage().contains("too long"));
            assertTrue(exception.getMessage().contains("1001"));
            assertTrue(exception.getMessage().contains("STRICT_POLICY"));
        }

        @Test
        @DisplayName("Expression at max length should succeed")
        void testExpressionAtMaxLength() {
            String maxLengthExpression = "a".repeat(1000);
            assertDoesNotThrow(() -> FastPostfixConverter.toPostfix(maxLengthExpression, DslPolicy.strict()));
        }

        @Test
        @DisplayName("Expression just under max length should succeed")
        void testExpressionJustUnderMaxLength() {
            String expression = "a".repeat(999);
            assertDoesNotThrow(() -> FastPostfixConverter.toPostfix(expression, DslPolicy.builder().maxExpressionLength(999).build()));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Malformed Expressions")
    class EdgeCasesTests {

        @Test
        @DisplayName("Unmatched left parenthesis should throw exception")
        void testUnmatchedLeftParen() {
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> FastPostfixConverter.toPostfix("(a & b", DslPolicy.strict())
            );
            assertTrue(exception.getMessage().contains("Mismatched parentheses"));
            assertTrue(exception.getMessage().contains("unmatched '('"));
        }

        @Test
        @DisplayName("Unmatched right parenthesis should throw exception")
        void testUnmatchedRightParen() {
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> FastPostfixConverter.toPostfix("a & b)", DslPolicy.strict())
            );
            assertTrue(exception.getMessage().contains("Mismatched parentheses"));
            assertTrue(exception.getMessage().contains("unmatched ')'"));
        }

        @Test
        @DisplayName("Multiple unmatched left parentheses")
        void testMultipleUnmatchedLeftParen() {
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> FastPostfixConverter.toPostfix("((a & b", DslPolicy.strict())
            );
            assertTrue(exception.getMessage().contains("Mismatched parentheses"));
        }

        @Test
        @DisplayName("Expression ending with operator")
        void testEndingWithOperator() {
            DSLSyntaxException exception = assertThrows(DSLSyntaxException.class, () -> FastPostfixConverter.toPostfix("a !", DslPolicy.strict()));
            // FastPostfixConverter just converts - validation happens later
            assertTrue(exception.getMessage().contains("Malformed expression: ends with '!'"));
        }

        @Test
        @DisplayName("Expression starting with binary operator")
        void testStartingWithBinaryOperator() {
            DSLSyntaxException exception = assertThrows(DSLSyntaxException.class, () -> FastPostfixConverter.toPostfix("& a", DslPolicy.strict()));
            assertTrue(exception.getMessage().contains("Malformed expression: starts with '&'"));
        }

        @Test
        @DisplayName("Empty parentheses")
        void testEmptyParentheses() {
            // FastPostfixConverter doesn't validate this
            List<String> result = FastPostfixConverter.toPostfix("()", DslPolicy.strict());
            // Should produce empty result
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Special characters in identifiers (not validated)")
        void testSpecialCharactersInIdentifiers() {
            // FastPostfixConverter doesn't validate identifier syntax
            List<String> result = FastPostfixConverter.toPostfix("test-case", DslPolicy.strict());
            // Hyphen is not an operator, so it becomes part of identifier
            assertEquals(List.of("test-case"), result);
        }
    }

    @Nested
    @DisplayName("Performance and Optimization Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Large valid expression should complete quickly")
        void testLargeValidExpression() {
            // Create a large but valid expression: a | b | c | d ... | z (26 identifiers)
            StringBuilder expr = new StringBuilder("a");
            for (char c = 'b'; c <= 'z'; c++) {
                expr.append(" | ").append(c);
            }

            long start = System.nanoTime();
            List<String> result = FastPostfixConverter.toPostfix(expr.toString(), DslPolicy.defaults());
            long duration = System.nanoTime() - start;

            assertNotNull(result);
            assertEquals(51, result.size()); // 26 identifiers + 25 OR operators

            // Should complete in less than 10ms (very generous for fast converter)
            assertTrue(duration < 10_000_000, "Conversion took too long: " + duration + "ns");
        }

        @Test
        @DisplayName("Deeply nested parentheses should not cause stack overflow")
        void testDeeplyNestedParentheses() {
            // Create deeply nested expression: ((((a))))
            StringBuilder expr = new StringBuilder();
            int depth = 50;
            for (int i = 0; i < depth; i++) {
                expr.append("(");
            }
            expr.append("a");
            for (int i = 0; i < depth; i++) {
                expr.append(")");
            }

            List<String> result = FastPostfixConverter.toPostfix(expr.toString(), DslPolicy.defaults());
            assertEquals(List.of("a"), result);
        }

        @Test
        @DisplayName("Expression with many consecutive NOTs")
        void testManyConsecutiveNots() {
            // !!!!!!a
            StringBuilder expr = new StringBuilder();
            int notCount = 20;
            for (int i = 0; i < notCount; i++) {
                expr.append("!");
            }
            expr.append("a");
            String expression = expr.toString();
            DslPolicy dslPolicy = DslPolicy.defaults();

            List<String> result = FastPostfixConverter.toPostfix(expression, dslPolicy);
            assertEquals(1, result.size()); // a + 20 NOTs
            assertEquals("a", result.getFirst());
        }

        @Test
        @DisplayName("Large OR expression should simplify and complete quickly")
        @Disabled
        void testLargeOrExpressionThatSimplify() {
            StringBuilder expr = new StringBuilder();
            for (int i = 1; i < 2_000; i++) {
                expr.append("a").append("|");
            }
            expr.append("a");
            String expression = expr.toString();
            DslPolicy dslPolicy = DslPolicy.defaults();

            long start = System.nanoTime();
            List<String> result = FastPostfixConverter.toPostfix(expression, dslPolicy);
            long duration = System.nanoTime() - start;

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("a", result.getFirst());
            assertTrue(duration < 2_200_000, "Build took too long: " + duration / 1_000_000.0 + " ms");
        }

        @Test
        @DisplayName("Large AND expression should simplify and complete quickly")
        @Disabled
        void testLargeAndExpressionThatSimplify() {

            // Build a large redundant AND expression: a & a & a & ... & a
            StringBuilder expr = new StringBuilder();
            for (int i = 1; i < 2_000; i++) {
                expr.append("a").append("&");
            }
            expr.append("a");
            String expression = expr.toString();
            DslPolicy dslPolicy = DslPolicy.defaults();

            long start = System.nanoTime();
            List<String> result = FastPostfixConverter.toPostfix(expression, dslPolicy);
            long duration = System.nanoTime() - start;

            assertNotNull(result, "Postfix result should not be null");
            assertEquals(1, result.size(), "Expected expression to reduce to a single token");
            assertEquals("a", result.getFirst(), "Expected reduced token to be 'a'");

            // 4ms is generous — on most JVMs this should take < 1ms
            assertTrue(duration < 2_500_000, "Build took too long: " + duration / 1_000_000.0 + " ms");
            System.out.printf("Simplified %d-token AND expression in %.3f ms%n",
                    2_000, duration / 1_000_000.0);
        }

        @Test
        @DisplayName("Large NOT expression should simplify and complete quickly")
        @Disabled
        void testLargeNotExpressionThatSimplify() {

            // Create a large expression: !!!!!! ... !a (1 identifiers, 4_999 NOTs)
            StringBuilder expr = new StringBuilder();
            for (int i = 1; i < 4_999; i++) {
                expr.append("!");
            }

            expr.append("a");
            String expression = expr.toString();
            DslPolicy dslPolicy = DslPolicy.defaults();

            long start = System.nanoTime();
            List<String> result = FastPostfixConverter.toPostfix(expression, dslPolicy);
            long duration = System.nanoTime() - start;

            assertNotNull(result);
            // Should complete in less than 1ms (very generous)
            assertEquals(1, result.size());
            assertEquals("a", result.getFirst());
            assertTrue(duration < 2_000_000, "Build took too long: " + duration/1_000_000 + "ms");
        }
    }

    @Nested
    @DisplayName("Operator Precedence and Associativity Tests")
    class PrecedenceTests {

        @ParameterizedTest
        @DisplayName("Precedence: NOT > AND > OR")
        @CsvSource({
            "'!a & b',       'a,!,b,&'",
            "'a & !b',       'a,b,!,&'",
            "'!a | b',       'a,!,b,|'",
            "'a | !b',       'a,b,!,|'",
            "'a & b | c',    'a,b,&,c,|'",
            "'a | b & c',    'a,b,c,&,|'",
            "'!a & b | c',   'a,!,b,&,c,|'",
            "'a & !b | c',   'a,b,!,&,c,|'",
            "'a | b & !c',   'a,b,c,!,&,|'"
        })
        void testOperatorPrecedence(String expression, String expectedPostfix) {
            List<String> result = FastPostfixConverter.toPostfix(expression, DslPolicy.strict());
            List<String> expected = List.of(expectedPostfix.split(","));
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Associativity: a & b & c & d (left-to-right)")
        void testAndAssociativity() {
            List<String> result = FastPostfixConverter.toPostfix("a & b & c & d", DslPolicy.strict());
            // ((a & b) & c) & d -> a b & c & d &
            assertEquals(List.of("a", "b", "&", "c", "&", "d", "&"), result);
        }

        @Test
        @DisplayName("Associativity: a | b | c | d (left-to-right)")
        void testOrAssociativity() {
            List<String> result = FastPostfixConverter.toPostfix("a | b | c | d", DslPolicy.strict());
            // ((a | b) | c) | d -> a b | c | d |
            assertEquals(List.of("a", "b", "|", "c", "|", "d", "|"), result);
        }

        @Test
        @DisplayName("Mixed associativity: a & b | c & d")
        void testMixedAssociativity() {
            List<String> result = FastPostfixConverter.toPostfix("a & b | c & d", DslPolicy.strict());
            // (a & b) | (c & d) -> a b & c d & |
            assertEquals(List.of("a", "b", "&", "c", "d", "&", "|"), result);
        }
    }

    @Nested
    @DisplayName("Whitespace Handling Tests")
    class WhitespaceTests {

        @Test
        @DisplayName("No whitespace: a&b")
        void testNoWhitespace() {
            List<String> result = FastPostfixConverter.toPostfix("a&b", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }

        @Test
        @DisplayName("Tabs and newlines: 'a\\t&\\nb'")
        void testTabsAndNewlines() {
            List<String> result = FastPostfixConverter.toPostfix("a\t&\nb", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }

        @Test
        @DisplayName("Leading and trailing whitespace")
        void testLeadingTrailingWhitespace() {
            List<String> result = FastPostfixConverter.toPostfix("  a & b  ", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }

        @Test
        @DisplayName("Multiple spaces between tokens")
        void testMultipleSpaces() {
            List<String> result = FastPostfixConverter.toPostfix("a     &     b", DslPolicy.strict());
            assertEquals(List.of("a", "b", "&"), result);
        }
    }
}

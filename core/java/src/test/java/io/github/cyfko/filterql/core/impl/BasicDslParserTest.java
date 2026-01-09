package io.github.cyfko.filterql.core.impl;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.api.FilterTree;
import io.github.cyfko.filterql.core.config.CachePolicy;
import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for optimized DSLParser (v4.0.1+).
 * <p>
 * Tests the three-phase parsing system:
 * </p>
 * <ol>
 *   <li>FastPostfixConverter - Fast conversion with DoS protection</li>
 *   <li>BooleanSimplifier - Validation and simplification</li>
 *   <li>PostfixConditionBuilder - Lazy Condition construction</li>
 * </ol>
 *
 * @author Frank KOSSI
 * @since 4.0.1
 */
public class BasicDslParserTest {
    enum TestRef implements PropertyReference {
        PROP_A,
        PROP_B,
        PROP_C;

        @Override
        public Class<?> getType() {
            return String.class;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return Set.of(Op.EQ, Op.NE, Op.GT, Op.LT);
        }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }

    private BasicDslParser parser;

    @Mock
    private FilterContext mockFilterContext;

    @Mock
    private FilterDefinition<TestRef> mockDefinitionA, mockDefinitionB, mockDefinitionC;

    private Map<String, FilterDefinition<TestRef>> mockedDefinitions;

    @Mock
    private Condition mockConditionA;

    @Mock
    private Condition mockConditionB;

    @Mock
    private Condition mockConditionC;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new BasicDslParser();

        // Configure FilterDefinition mocks to return non-null ref() and op()
        when(mockDefinitionA.ref()).thenReturn(TestRef.PROP_A);
        when(mockDefinitionA.op()).thenReturn("EQ");
        when(mockDefinitionB.ref()).thenReturn(TestRef.PROP_B);
        when(mockDefinitionB.op()).thenReturn("EQ");
        when(mockDefinitionC.ref()).thenReturn(TestRef.PROP_C);
        when(mockDefinitionC.op()).thenReturn("EQ");

        // Setup mocked definitions associations
        mockedDefinitions = Map.of(
                "A", mockDefinitionA,
                "B", mockDefinitionB,
                "C", mockDefinitionC
        );

        // Configuration des mocks pour les conditions
        when(mockFilterContext.toCondition(eq("A"), any(), any())).thenReturn(mockConditionA);
        when(mockFilterContext.toCondition(eq("B"), any(), any())).thenReturn(mockConditionB);
        when(mockFilterContext.toCondition(eq("C"), any(), any())).thenReturn(mockConditionC);

        // Configuration des méthodes chainées
        when(mockConditionA.not()).thenReturn(mockConditionA);
        when(mockConditionA.and(any())).thenReturn(mockConditionA);
        when(mockConditionA.or(any())).thenReturn(mockConditionA);
        when(mockConditionB.not()).thenReturn(mockConditionB);
        when(mockConditionB.and(any())).thenReturn(mockConditionB);
        when(mockConditionB.or(any())).thenReturn(mockConditionB);
        when(mockConditionC.not()).thenReturn(mockConditionC);
        when(mockConditionC.and(any())).thenReturn(mockConditionC);
        when(mockConditionC.or(any())).thenReturn(mockConditionC);
    }

    @Nested
    @DisplayName("Valid Expressions - Integration Tests")
    class ValidExpressions {

        @Test
        @DisplayName("Simple identifier")
        void testSingleIdentifier() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("A");
            assertNotNull(tree);

            Condition result = tree.generate(mockedDefinitions, mockFilterContext);
            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
        }

        @Test
        @DisplayName("Simple AND expression")
        void testSimpleAnd() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("A & B");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockConditionA).and(mockConditionB);
        }

        @Test
        @DisplayName("Simple OR expression")
        void testSimpleOr() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("A | B");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockConditionA).or(mockConditionB);
        }

        @Test
        @DisplayName("Simple NOT expression")
        void testSimpleNot() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("!A");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockConditionA).not();
        }

        @Test
        @DisplayName("Expression with parentheses")
        void testSimpleParentheses() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("(A)");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
        }

        @Test
        @DisplayName("Complex expression with precedence: A | B & C")
        void testComplexWithPrecedence() throws DSLSyntaxException, FilterValidationException {
            // A | B & C should be interpreted as A | (B & C)
            FilterTree tree = parser.parse("A | B & C");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockFilterContext).toCondition(eq("C"),any(),any());
        }

        @Test
        @DisplayName("Complex expression with parentheses: !(A & B) | C")
        void testComplexParentheses() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("!(A & B) | C");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockFilterContext).toCondition(eq("C"),any(),any());
        }

        @Test
        @DisplayName("Left associativity for AND: A & B & C")
        void testLeftAssociativityAnd() throws DSLSyntaxException, FilterValidationException {
            // A & B & C should be (A & B) & C
            FilterTree tree = parser.parse("A & B & C");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockFilterContext).toCondition(eq("C"),any(),any());
        }

        @Test
        @DisplayName("Left associativity for OR: A | B | C")
        void testLeftAssociativityOr() throws DSLSyntaxException, FilterValidationException {
            // A | B | C should be (A | B) | C
            FilterTree tree = parser.parse("A | B | C");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockFilterContext).toCondition(eq("C"),any(),any());
        }

        @Test
        @DisplayName("Double negation: !!A")
        void testDoubleNegation() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("!!A");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            // After simplification, !!A becomes A, so not() should not be called
            verify(mockConditionA, never()).not();
        }

        @Test
        @DisplayName("Expression with multiple whitespaces")
        void testMultipleWhitespaces() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("  A   &   B  ");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
        }

        @Test
        @DisplayName("Nested parentheses: ((A & B) | (C))")
        void testNestedParentheses() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("((A & B) | (C))");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockFilterContext).toCondition(eq("C"),any(),any());
        }

        @Test
        @DisplayName("Boolean simplification: A & A -> A")
        void testBooleanSimplificationIdempotence() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("A & A");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            // After simplification, A & A becomes A, so and() should not be called
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockConditionA, never()).and(any());
        }
    }

    @Nested
    @DisplayName("Invalid Expressions - Error Tests")
    class InvalidExpressions {

        @ParameterizedTest
        @DisplayName("Null or empty expressions")
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        void testNullOrEmptyExpressions(String expression) {
            DSLSyntaxException exception = assertThrows(
                    DSLSyntaxException.class,
                    () -> parser.parse(expression)
            );
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Null expression")
        void testNullExpression() {
            DSLSyntaxException exception = assertThrows(
                    DSLSyntaxException.class,
                    () -> parser.parse(null)
            );
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @ParameterizedTest
        @DisplayName("Invalid identifiers are accepted by default parser (lazy validation)")
        @ValueSource(strings = {
                "123abc",      // Starts with digit - OK with default policy (validateIdentifiers=false)
                "test-case",   // Contains hyphen - OK with default policy
        })
        void testInvalidIdentifiers(String expression) {
            // Default parser (validateIdentifiers=false) accepts any identifier syntax
            // Validation happens later during PostfixConditionBuilder.build()
            assertDoesNotThrow(() -> parser.parse(expression));
        }

        @Test
        @DisplayName("Invalid identifier with strict policy")
        void testIdentifierStartingWithDigitStrict() {
            // Strict policy enables identifier validation
            BasicDslParser strictParser = new BasicDslParser(DslPolicy.strict());
            
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> strictParser.parse("123abc")
            );
            assertTrue(exception.getMessage().contains("Invalid identifier"));
        }

        @Test
        @DisplayName("Unmatched left parenthesis")
        void testUnclosedLeftParenthesis() {
            DSLSyntaxException exception = assertThrows(
                    DSLSyntaxException.class,
                    () -> parser.parse("(A & B")
            );
            assertTrue(exception.getMessage().contains("Mismatched parentheses"));
            assertTrue(exception.getMessage().contains("unmatched '('"));
        }

        @Test
        @DisplayName("Unmatched right parenthesis")
        void testUnmatchedRightParenthesis() {
            DSLSyntaxException exception = assertThrows(
                    DSLSyntaxException.class,
                    () -> parser.parse("A & B)")
            );
            assertTrue(exception.getMessage().contains("Mismatched parentheses"));
            assertTrue(exception.getMessage().contains("unmatched ')'"));
        }

        @Test
        @DisplayName("Expression ending with operator")
        void testEndingWithOperator() {
            DSLSyntaxException exception = assertThrows(
                    DSLSyntaxException.class,
                    () -> parser.parse("A &")
            );
            assertTrue(exception.getMessage().contains("Malformed") ||
                      exception.getMessage().contains("cannot end"));
        }

        @Test
        @DisplayName("Expression starting with binary operator")
        void testStartingWithBinaryOperator() {
            DSLSyntaxException exception = assertThrows(
                    DSLSyntaxException.class,
                    () -> parser.parse("& A")
            );
            assertTrue(exception.getMessage().contains("Malformed") ||
                      exception.getMessage().contains("without operand"));
        }

        @Test
        @DisplayName("Identifier starting with digit accepted with default parser")
        void testIdentifierStartingWithDigit() {
            // With lazy validation (default), identifiers starting with digits are accepted at parse time
            // Validation only occurs with strict mode (see testIdentifierStartingWithDigitStrict)
            assertDoesNotThrow(() -> parser.parse("123invalid"));
        }
    }

    @Nested
    @DisplayName("FilterTree toString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString returns postfix representation")
        void testToString() throws DSLSyntaxException {
            FilterTree tree = parser.parse("A & B");
            String treeString = tree.toString();
            assertNotNull(treeString);
            assertTrue(treeString.contains("PostfixFilterTree"));
            assertTrue(treeString.contains("A"));
            assertTrue(treeString.contains("B"));
        }
    }

    @Nested
    @DisplayName("DoS Protection Tests")
    class DosProtectionTests {

        @Test
        @DisplayName("Expression exceeding max length")
        void testExpressionTooLong() {
            BasicDslParser strictParser = new BasicDslParser(DslPolicy.strict(), CachePolicy.strict());
            String longExpression = "A".repeat(1001);

            DSLSyntaxException exception = assertThrows(
                    DSLSyntaxException.class,
                    () -> strictParser.parse(longExpression)
            );
            assertTrue(exception.getMessage().contains("too long"));
        }
    }

    @Nested
    @DisplayName("Shorthand Syntax Tests")
    class ShorthandSyntaxTests {

        @Test
        @DisplayName("AND shorthand combines all filters with AND")
        void testAndShorthand() throws DSLSyntaxException, FilterValidationException {

            FilterTree tree = parser.parse("AND");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            // Verify all conditions were retrieved
            verify(mockFilterContext, times(3)).toCondition(any(), any(), any());
        }

        @Test
        @DisplayName("OR shorthand combines all filters with OR")
        void testOrShorthand() throws DSLSyntaxException, FilterValidationException {

            FilterTree tree = parser.parse("OR");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext, times(3)).toCondition(any(), any(), any());
        }

        @Test
        @DisplayName("NOT shorthand combines all with AND then negates")
        void testNotShorthand() throws DSLSyntaxException, FilterValidationException {

            FilterTree tree = parser.parse("NOT");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext, times(3)).toCondition(any(),any(), any());
        }

        @Test
        @DisplayName("Shorthand is case-insensitive")
        void testShorthandCaseInsensitive() throws DSLSyntaxException {

            assertDoesNotThrow(() -> parser.parse("and").generate(mockedDefinitions, mockFilterContext));
            assertDoesNotThrow(() -> parser.parse("or").generate(mockedDefinitions, mockFilterContext));
            assertDoesNotThrow(() -> parser.parse("not").generate(mockedDefinitions, mockFilterContext));
            assertDoesNotThrow(() -> parser.parse("AnD").generate(mockedDefinitions, mockFilterContext));
            assertDoesNotThrow(() -> parser.parse("Or").generate(mockedDefinitions, mockFilterContext));
            assertDoesNotThrow(() -> parser.parse("nOt").generate(mockedDefinitions, mockFilterContext));
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Default configuration")
        void testDefaultConfiguration() {
            BasicDslParser defaultParser = new BasicDslParser();
            assertNotNull(defaultParser);
            assertDoesNotThrow(() -> defaultParser.parse("A & B"));
        }

        @Test
        @DisplayName("Strict configuration")
        void testStrictConfiguration() {
            BasicDslParser strictParser = new BasicDslParser(DslPolicy.strict(), CachePolicy.strict());
            assertNotNull(strictParser);
            assertDoesNotThrow(() -> strictParser.parse("A & B"));
        }

        @Test
        @DisplayName("Relaxed configuration")
        void testRelaxedConfiguration() {
            BasicDslParser relaxedParser = new BasicDslParser(DslPolicy.relaxed(), CachePolicy.strict());
            assertNotNull(relaxedParser);
            assertDoesNotThrow(() -> relaxedParser.parse("A & B"));
        }

        @Test
        @DisplayName("Null configuration throws exception")
        void testNullConfiguration() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BasicDslParser(null,null)
            );
        }

        @Test
        @DisplayName("Custom configuration")
        void testCustomConfiguration() {
            DslPolicy custom = DslPolicy.builder().maxExpressionLength(100).build();
            BasicDslParser customParser = new BasicDslParser(custom, CachePolicy.defaults());
            assertNotNull(customParser);
        }
    }

    @Nested
    @DisplayName("Boolean Simplification Integration Tests")
    class SimplificationIntegrationTests {

        @Test
        @DisplayName("Idempotence: A & A -> A")
        void testIdempotenceAnd() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("A & A");
            tree.generate(mockedDefinitions, mockFilterContext);

            // Simplified to just A, so and() should not be called
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockConditionA, never()).and(any());
        }

        @Test
        @DisplayName("Idempotence: A | A -> A")
        void testIdempotenceOr() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("A | A");
            tree.generate(mockedDefinitions, mockFilterContext);

            // Simplified to just A, so or() should not be called
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockConditionA, never()).or(any());
        }

        @Test
        @DisplayName("Double negation elimination: !!A -> A")
        void testDoubleNegationElimination() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("!!A");
            tree.generate(mockedDefinitions, mockFilterContext);

            // Simplified to just A, so not() should not be called
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockConditionA, never()).not();
        }

        @Test
        @DisplayName("Parentheses removal: (((A))) -> A")
        void testParenthesesRemoval() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("(((A)))");
            tree.generate(mockedDefinitions, mockFilterContext);

            verify(mockFilterContext).toCondition(eq("A"),any(),any());
        }

        @Test
        @DisplayName("Complex simplification: (A & A) | (B & B)")
        void testComplexSimplification() throws DSLSyntaxException, FilterValidationException {
            // Should simplify to A | B
            FilterTree tree = parser.parse("(A & A) | (B & B)");
            tree.generate(mockedDefinitions, mockFilterContext);

            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Deeply nested parentheses")
        void testDeeplyNestedParentheses() throws DSLSyntaxException, FilterValidationException {
            String expr = "((((A))))";
            FilterTree tree = parser.parse(expr);
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
        }

        @Test
        @DisplayName("All token types in one expression")
        void testAllTokenTypesInExpression() throws DSLSyntaxException, FilterValidationException {
            String expr = "!(A & B) | (C)";
            FilterTree tree = parser.parse(expr);
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockFilterContext).toCondition(eq("C"),any(),any());
        }
    }

    @Nested
    @DisplayName("Cache Integration Tests")
    class CacheIntegrationTests {

        @Test
        @DisplayName("Cache is enabled by default")
        void testCacheEnabledByDefault() {
            BasicDslParser defaultParser = new BasicDslParser();
            var stats = defaultParser.getCacheStats();

            assertTrue((Boolean) stats.get("enabled"));
            assertEquals(1000, stats.get("maxSize"));
        }

        @Test
        @DisplayName("Cache can be disabled")
        void testCacheDisabled() {
            DslPolicy noCacheConfig = DslPolicy.defaults();
            BasicDslParser noCacheParser = new BasicDslParser(noCacheConfig, CachePolicy.none());

            var stats = noCacheParser.getCacheStats();
            assertFalse((Boolean) stats.get("enabled"));
            assertEquals(1, stats.size()); // Only "enabled" key
        }

        @Test
        @DisplayName("Cache hit for identical filter structure")
        void testCacheHit() throws DSLSyntaxException, FilterValidationException {
            BasicDslParser cachedParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());
            String expression = "A & B | C";

            // Parse the expression
            FilterTree tree = cachedParser.parse(expression);
            assertNotNull(tree);

            // First generate - cache miss
            Condition condition1 = tree.generate(mockedDefinitions, mockFilterContext);
            assertNotNull(condition1);

            // Second generate with same tree and same definitions - cache hit
            Condition condition2 = tree.generate(mockedDefinitions, mockFilterContext);
            assertNotNull(condition2);

            // Cache hit means toCondition should only be called once per filter (3 times total, not 6)
            verify(mockFilterContext, times(1)).toCondition(eq("A"),any(),any());
            verify(mockFilterContext, times(1)).toCondition(eq("B"),any(),any());
            verify(mockFilterContext, times(1)).toCondition(eq("C"),any(),any());
        }

        @Test
        @DisplayName("Cache is based on filter structure, not DSL expression")
        void testCacheDifferentWhitespace() throws DSLSyntaxException, FilterValidationException {
            BasicDslParser cachedParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());

            // Different DSL expressions but same structure after parsing
            FilterTree tree1 = cachedParser.parse("A & B");
            FilterTree tree2 = cachedParser.parse("  A & B  ");
            FilterTree tree3 = cachedParser.parse("A&B");

            assertNotNull(tree1);
            assertNotNull(tree2);
            assertNotNull(tree3);

            // Generate with all trees - they should share the same cache entry
            tree1.generate(mockedDefinitions, mockFilterContext);
            tree2.generate(mockedDefinitions, mockFilterContext);
            tree3.generate(mockedDefinitions, mockFilterContext);

            // Verify cache size - should have 1 entry (same structure)
            var stats = cachedParser.getCacheStats();
            assertEquals(1, (Integer) stats.get("size"));

            // Verify toCondition called only once per filter (cache hit on subsequent generates)
            verify(mockFilterContext, times(1)).toCondition(eq("A"),any(),any());
            verify(mockFilterContext, times(1)).toCondition(eq("B"),any(),any());
        }

        @Test
        @DisplayName("Cache stores simplified expressions")
        void testCacheStoresSimplifiedExpressions() throws DSLSyntaxException, FilterValidationException {
            BasicDslParser cachedParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());

            // Parse an expression that can be simplified (A & A -> A)
            String expression = "A & A";
            FilterTree tree1 = cachedParser.parse(expression);
            tree1.generate(mockedDefinitions, mockFilterContext);

            // Verify that simplification happened
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockConditionA, never()).and(any());

            // Parse again - should get cached simplified result
            FilterTree tree2 = cachedParser.parse(expression);
            tree2.generate(mockedDefinitions, mockFilterContext);

            // Should still be simplified
            verify(mockConditionA, never()).and(any());
        }

        @Test
        @DisplayName("clearCache clears all cached entries")
        void testClearCache() throws DSLSyntaxException, FilterValidationException {
            BasicDslParser cachedParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());

            // Parse and generate different expressions to populate cache
            Map<String, FilterDefinition<TestRef>> defsAB = Map.of("A", mockDefinitionA, "B", mockDefinitionB);
            Map<String, FilterDefinition<TestRef>> defsBC = Map.of("B", mockDefinitionB, "C", mockDefinitionC);
            Map<String, FilterDefinition<TestRef>> defsC = Map.of("C", mockDefinitionC);

            cachedParser.parse("A & B").generate(defsAB, mockFilterContext);
            cachedParser.parse("B | C").generate(defsBC, mockFilterContext);
            cachedParser.parse("!(C)").generate(defsC, mockFilterContext);

            var statsBefore = cachedParser.getCacheStats();
            assertTrue((Integer) statsBefore.get("size") > 0);

            // Clear cache
            cachedParser.clearCache();

            var statsAfter = cachedParser.getCacheStats();
            assertEquals(0, statsAfter.get("size"));
        }

        @Test
        @DisplayName("clearCache is safe when cache disabled")
        void testClearCacheWhenDisabled() {
            BasicDslParser noCacheParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.none());

            // Should not throw
            assertDoesNotThrow(() -> noCacheParser.clearCache());
        }

        @Test
        @DisplayName("Cache respects configured size size")
        void testCacheSizeLimit() throws DSLSyntaxException, FilterValidationException {
            // Create parser with small cache (5 entries)
            DslPolicy smallCacheConfig = DslPolicy.defaults();
            BasicDslParser cachedParser = new BasicDslParser(smallCacheConfig,  CachePolicy.custom(5));

            // Add more than cache size entries with different filter structures
            for (int i = 0; i < 10; i++) {
                Map<String, FilterDefinition<TestRef>> uniqueDefs = Map.of(
                    "X" + i, mockDefinitionA
                );
                when(mockFilterContext.toCondition(eq("X" + i),any(),any())).thenReturn(mockConditionA);
                cachedParser.parse("X" + i).generate(uniqueDefs, mockFilterContext);
            }

            // Cache size should not exceed size (LRU eviction)
            var stats = cachedParser.getCacheStats();
            assertTrue((Integer) stats.get("size") <= 5);
            assertEquals(5, stats.get("maxSize"));
        }

        @Test
        @DisplayName("Cache statistics for strict configuration")
        void testCacheStatsStrict() {
            BasicDslParser strictParser = new BasicDslParser(DslPolicy.strict(), CachePolicy.strict());
            var stats = strictParser.getCacheStats();

            assertTrue((Boolean) stats.get("enabled"));
            assertEquals(500, stats.get("maxSize")); // Strict has 500 cache entries
            assertEquals(0, stats.get("size")); // Empty initially
        }

        @Test
        @DisplayName("Cache statistics for relaxed configuration")
        void testCacheStatsRelaxed() {
            BasicDslParser relaxedParser = new BasicDslParser(DslPolicy.relaxed(), CachePolicy.relaxed());
            var stats = relaxedParser.getCacheStats();

            assertTrue((Boolean) stats.get("enabled"));
            assertEquals(2000, stats.get("maxSize")); // Relaxed has 2000 cache entries
            assertEquals(0, stats.get("size")); // Empty initially
        }

        @Test
        @DisplayName("Cache tracks entry count correctly")
        void testCacheEntryCount() throws DSLSyntaxException, FilterValidationException {
            BasicDslParser cachedParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());

            // Initially empty
            assertEquals(0, cachedParser.getCacheStats().get("size"));

            // Add first entry (parse + generate)
            Map<String, FilterDefinition<TestRef>> defsA = Map.of("A", mockDefinitionA);
            cachedParser.parse("A").generate(defsA, mockFilterContext);
            assertEquals(1, cachedParser.getCacheStats().get("size"));

            // Add second unique entry with different structure
            Map<String, FilterDefinition<TestRef>> defsB = Map.of("B", mockDefinitionB);
            cachedParser.parse("B").generate(defsB, mockFilterContext);
            assertEquals(2, cachedParser.getCacheStats().get("size"));

            // Re-generate first entry (cache hit, size should not change)
            cachedParser.parse("A").generate(defsA, mockFilterContext);
            assertEquals(2, cachedParser.getCacheStats().get("size"));
        }

        @Test
        @DisplayName("Cache works with complex boolean simplifications")
        void testCacheWithSimplifications() throws DSLSyntaxException, FilterValidationException {
            BasicDslParser cachedParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());

            // Expression with multiple simplification opportunities
            String expression = "(A & A) | (B | B) | !!C";

            // First parse
            FilterTree tree1 = cachedParser.parse(expression);
            tree1.generate(mockedDefinitions, mockFilterContext);

            // Verify simplifications occurred
            verify(mockFilterContext).toCondition(eq("A"),any(),any());
            verify(mockFilterContext).toCondition(eq("B"),any(),any());
            verify(mockFilterContext).toCondition(eq("C"),any(),any());

            // Second parse (cache hit)
            FilterTree tree2 = cachedParser.parse(expression);
            tree2.generate(mockedDefinitions, mockFilterContext);

            // Should still be simplified
            verify(mockConditionA, never()).and(any());
            verify(mockConditionB, never()).or(any());
            verify(mockConditionC, never()).not();
        }

        @Test
        @DisplayName("Cache is thread-safe")
        void testCacheThreadSafety() throws InterruptedException {
            BasicDslParser cachedParser = new BasicDslParser(DslPolicy.defaults(), CachePolicy.defaults());
            int threadCount = 10;
            int iterationsPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        try {
                            cachedParser.parse("A" + (threadId % 5) + " & B" + (i % 5));
                        } catch (DSLSyntaxException e) {
                            fail("Thread " + threadId + " failed: " + e.getMessage());
                        }
                    }
                });
            }

            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }

            // Wait for completion
            for (Thread thread : threads) {
                thread.join();
            }

            // Verify cache is still valid
            var stats = cachedParser.getCacheStats();
            assertTrue((Boolean) stats.get("enabled"));
            assertTrue((Integer) stats.get("size") <= (Integer) stats.get("maxSize"));
        }

        @Test
        @DisplayName("Custom cache configuration")
        @Disabled
        void testCustomCacheConfiguration() throws DSLSyntaxException {
            DslPolicy customConfig = DslPolicy.defaults();
            BasicDslParser customParser = new BasicDslParser(customConfig, CachePolicy.custom(100));

            var stats = customParser.getCacheStats();
            assertTrue((Boolean) stats.get("enabled"));
            assertEquals(100, stats.get("maxSize"));

            // Add an entry
            customParser.parse("A & B");
            assertEquals(1, stats.get("size"));
        }

        @Test
        @DisplayName("Unlimited configuration disables cache")
        @Disabled
        void testUnlimitedConfigurationDisablesCache() {
//            DSLParser unlimitedParser = new DSLParser(DslPolicy.unlimited());
//            var stats = unlimitedParser.getCacheStats();
//
//            assertFalse((Boolean) stats.get("enabled"));
        }
    }
}

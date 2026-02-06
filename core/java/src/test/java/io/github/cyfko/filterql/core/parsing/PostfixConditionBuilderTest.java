package io.github.cyfko.filterql.core.parsing;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link PostfixConditionBuilder}.
 * <p>
 * Tests single-pass Condition construction from simplified postfix expressions.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.1
 */
@DisplayName("PostfixConditionBuilder Tests")
class PostfixConditionBuilderTest {
    
    enum TestRef implements PropertyReference {
        MY_REF;

        @Override
        public Class<?> getType() {
            return null;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return Set.of();
        }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }

    @Mock
    private FilterContext mockFilterContext;

    @Mock
    private FilterDefinition<TestRef> mockDefinitionA, mockDefinitionB, mockDefinitionC, mockDefinitionD, mockDefinitionE;

    private Map<String, FilterDefinition<TestRef>> mockedDefinitions;

    @Mock
    private Condition mockConditionA;

    @Mock
    private Condition mockConditionB;

    @Mock
    private Condition mockConditionC;

    @Mock
    private Condition mockConditionResult;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup mocked definitions associations
        mockedDefinitions = Map.of(
                "a", mockDefinitionA,
                "b", mockDefinitionB,
                "c", mockDefinitionC,
                "d", mockDefinitionD,
                "e", mockDefinitionD
        );


        // Setup definition to return elements
        when(mockDefinitionA.ref()).thenReturn(TestRef.MY_REF);
        when(mockDefinitionA.operator()).thenReturn(Op.EQ);
        when(mockDefinitionB.ref()).thenReturn(TestRef.MY_REF);
        when(mockDefinitionB.operator()).thenReturn(Op.EQ);
        when(mockDefinitionC.ref()).thenReturn(TestRef.MY_REF);
        when(mockDefinitionC.operator()).thenReturn(Op.EQ);
        when(mockDefinitionD.ref()).thenReturn(TestRef.MY_REF);
        when(mockDefinitionD.operator()).thenReturn(Op.EQ);
        when(mockDefinitionE.ref()).thenReturn(TestRef.MY_REF);
        when(mockDefinitionE.operator()).thenReturn(Op.EQ);

        // Setup context to return conditions
        when(mockFilterContext.toCondition(eq("a"), any(), any())).thenReturn(mockConditionA);
        when(mockFilterContext.toCondition(eq("b"), any(), any())).thenReturn(mockConditionB);
        when(mockFilterContext.toCondition(eq("c"), any(), any())).thenReturn(mockConditionC);

        // Setup condition operations to return mockConditionResult
        when(mockConditionA.and(any())).thenReturn(mockConditionResult);
        when(mockConditionA.or(any())).thenReturn(mockConditionResult);
        when(mockConditionA.not()).thenReturn(mockConditionResult);
        when(mockConditionB.and(any())).thenReturn(mockConditionResult);
        when(mockConditionB.or(any())).thenReturn(mockConditionResult);
        when(mockConditionB.not()).thenReturn(mockConditionResult);
        when(mockConditionC.and(any())).thenReturn(mockConditionResult);
        when(mockConditionC.or(any())).thenReturn(mockConditionResult);
        when(mockConditionC.not()).thenReturn(mockConditionResult);
        when(mockConditionResult.and(any())).thenReturn(mockConditionResult);
        when(mockConditionResult.or(any())).thenReturn(mockConditionResult);
        when(mockConditionResult.not()).thenReturn(mockConditionResult);
    }

    @Nested
    @DisplayName("Valid Postfix Expression Tests")
    class ValidExpressionTests {

        @Test
        @DisplayName("Single identifier: [a]")
        void testSingleIdentifier() {
            List<String> postfix = List.of("a");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            assertEquals(mockConditionA, result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
        }

        @Test
        @DisplayName("Simple AND: [a, b, &]")
        void testSimpleAnd() {
            List<String> postfix = List.of("a", "b", "&");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockFilterContext).toCondition(eq("b"),any(),any());
            verify(mockConditionA).and(mockConditionB);
        }

        @Test
        @DisplayName("Simple OR: [a, b, |]")
        void testSimpleOr() {
            List<String> postfix = List.of("a", "b", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockFilterContext).toCondition(eq("b"),any(),any());
            verify(mockConditionA).or(mockConditionB);
        }

        @Test
        @DisplayName("Simple NOT: [a, !]")
        void testSimpleNot() {
            List<String> postfix = List.of("a", "!");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockConditionA).not();
        }

        @Test
        @DisplayName("Expression with precedence: [a, b, c, &, |]  (a | (b & c))")
        void testPrecedenceExpression() {
            // a | b & c -> a | (b & c) -> [a, b, c, &, |]
            List<String> postfix = List.of("a", "b", "c", "&", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockFilterContext).toCondition(eq("b"),any(),any());
            verify(mockFilterContext).toCondition(eq("c"),any(),any());
            verify(mockConditionB).and(mockConditionC);
            verify(mockConditionA).or(mockConditionResult);
        }

        @Test
        @DisplayName("Complex expression: [a, b, &, !, c, |]  (!(a & b) | c)")
        void testComplexExpression() {
            // !(a & b) | c -> [a, b, &, !, c, |]
            List<String> postfix = List.of("a", "b", "&", "!", "c", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockFilterContext).toCondition(eq("b"),any(),any());
            verify(mockFilterContext).toCondition(eq("c"),any(),any());
            verify(mockConditionA).and(mockConditionB);
            verify(mockConditionResult).not();
            verify(mockConditionResult).or(mockConditionC);
        }

        @Test
        @DisplayName("Left associativity: [a, b, &, c, &]  ((a & b) & c)")
        void testLeftAssociativity() {
            // a & b & c -> (a & b) & c -> [a, b, &, c, &]
            List<String> postfix = List.of("a", "b", "&", "c", "&");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockFilterContext).toCondition(eq("b"),any(),any());
            verify(mockFilterContext).toCondition(eq("c"),any(),any());
            verify(mockConditionA).and(mockConditionB);
            verify(mockConditionResult).and(mockConditionC);
        }

        @Test
        @DisplayName("Double NOT: [a, !, !]  (!!a)")
        void testDoubleNot() {
            // !!a -> [a, !, !]
            List<String> postfix = List.of("a", "!", "!");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockConditionA).not();
            verify(mockConditionResult).not();
        }

        @Test
        @DisplayName("Complex nested expression: [a, b, |, c, &, d, |]")
        void testComplexNested() {
            // ((a | b) & c) | d -> [a, b, |, c, &, d, |]
            when(mockFilterContext.toCondition(eq("d"),any(),any())).thenReturn(mockConditionC);

            List<String> postfix = List.of("a", "b", "|", "c", "&", "d", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("a"),any(),any());
            verify(mockFilterContext).toCondition(eq("b"),any(),any());
            verify(mockFilterContext).toCondition(eq("c"),any(),any());
            verify(mockFilterContext).toCondition(eq("d"),any(),any());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Null postfix tokens should throw NullPointerException")
        void testNullPostfixTokens() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> PostfixConditionBuilder.build(null,  mockedDefinitions, mockFilterContext)
            );
            assertTrue(exception.getMessage().contains("postfixTokens cannot be null"));
        }

        @Test
        @DisplayName("Null context should throw NullPointerException")
        void testNullContext() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> PostfixConditionBuilder.build(List.of("a"),  mockedDefinitions, null)
            );
            assertTrue(exception.getMessage().contains("context cannot be null"));
        }

        @Test
        @DisplayName("Empty postfix tokens should throw DSLSyntaxException")
        void testEmptyPostfixTokens() {
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(List.of(),  mockedDefinitions, mockFilterContext)
            );
            assertTrue(exception.getMessage().contains("Cannot build condition from empty postfix expression"));
        }

        @Test
        @DisplayName("NOT without operand should throw DSLSyntaxException")
        void testNotWithoutOperand() {
            List<String> postfix = List.of("!");
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext)
            );
            assertTrue(exception.getMessage().contains("NOT operator (!) without operand"));
        }

        @Test
        @DisplayName("AND without sufficient operands should throw DSLSyntaxException")
        void testAndWithoutSufficientOperands() {
            List<String> postfix = List.of("a", "&");
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext)
            );
            assertTrue(exception.getMessage().contains("AND operator (&) requires two operands"));
        }

        @Test
        @DisplayName("OR without sufficient operands should throw DSLSyntaxException")
        void testOrWithoutSufficientOperands() {
            List<String> postfix = List.of("a", "|");
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext)
            );
            assertTrue(exception.getMessage().contains("OR operator (|) requires two operands"));
        }

        @Test
        @DisplayName("Too many operands (missing operators) should throw DSLSyntaxException")
        void testTooManyOperands() {
            List<String> postfix = List.of("a", "b", "c");
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext)
            );
            assertTrue(exception.getMessage().contains("evaluation resulted in 3 conditions on stack"));
            assertTrue(exception.getMessage().contains("expected 1"));
        }

        @Test
        @DisplayName("Only operators should throw DSLSyntaxException")
        void testOnlyOperators() {
            List<String> postfix = List.of("&", "|");
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext)
            );
            // Should fail when trying to pop from empty stack
            assertTrue(exception.getMessage().contains("requires two operands") ||
                      exception.getMessage().contains("without operand"));
        }

        @Test
        @DisplayName("Undefined filter should throw DSLSyntaxException")
        void testUndefinedFilter() {
            Map<String, FilterDefinition<TestRef>> definitionMap = new HashMap<>();

            List<String> postfix = List.of("unknown");
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(postfix,  definitionMap, mockFilterContext)
            );
            // Updated error message format (now includes 'Available filters')
            assertTrue(exception.getMessage().contains("undefined filter 'unknown'") && 
                      exception.getMessage().contains("Available filters"));
        }

        @Test
        @DisplayName("Filter resolving to null should throw DSLSyntaxException")
        void testFilterResolvingToNull() {
            Map<String, FilterDefinition<TestRef>> definitionMap = Map.of("nullFilter", mockDefinitionA);
            when(mockFilterContext.toCondition(eq("a"),any(),any())).thenReturn(null);

            List<String> postfix = List.of("nullFilter");
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> PostfixConditionBuilder.build(postfix,  definitionMap, mockFilterContext)
            );
            // Updated error message format
            assertTrue(exception.getMessage().contains("failed to create condition for filter 'nullFilter'"));
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Long chain of ANDs")
        void testLongChainOfAnds() {
            // a & b & c & d & e -> [a, b, &, c, &, d, &, e, &]
            when(mockFilterContext.toCondition(eq("d"),any(),any())).thenReturn(mockConditionC);
            when(mockFilterContext.toCondition(eq("e"),any(),any())).thenReturn(mockConditionC);

            List<String> postfix = List.of("a", "b", "&", "c", "&", "d", "&", "e", "&");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockConditionA).and(mockConditionB);
            // Multiple and() calls on mockConditionResult
            verify(mockConditionResult, times(3)).and(any());
        }

        @Test
        @DisplayName("Long chain of ORs")
        void testLongChainOfOrs() {
            // a | b | c | d | e -> [a, b, |, c, |, d, |, e, |]
            when(mockFilterContext.toCondition(eq("d"),any(),any())).thenReturn(mockConditionC);
            when(mockFilterContext.toCondition(eq("e"),any(),any())).thenReturn(mockConditionC);

            List<String> postfix = List.of("a", "b", "|", "c", "|", "d", "|", "e", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockConditionA).or(mockConditionB);
            verify(mockConditionResult, atLeast(3)).or(any());
        }

        @Test
        @DisplayName("Multiple NOTs in sequence")
        void testMultipleNots() {
            // !!!!a -> [a, !, !, !, !]
            List<String> postfix = List.of("a", "!", "!", "!", "!");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockConditionA).not();
            verify(mockConditionResult, times(3)).not();
        }

        @Test
        @DisplayName("Mixed operators in complex pattern")
        void testMixedOperatorsComplex() {
            // (a & !b) | (c & !d) -> [a, b, !, &, c, d, !, &, |]
            when(mockFilterContext.toCondition(eq("d"),any(),any())).thenReturn(mockConditionC);

            List<String> postfix = List.of("a", "b", "!", "&", "c", "d", "!", "&", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockConditionB, times(1)).not();
            verify(mockConditionC, times(1)).not(); // 'd' is also mockConditionC
        }

        @Test
        @DisplayName("Real-world complex expression")
        void testRealWorldExpression() {
            Map<String, FilterDefinition<TestRef>> definitionMap = Map.of(
                    "active", mockDefinitionA,
                    "deleted", mockDefinitionB,
                    "premium", mockDefinitionC,
                    "verified", mockDefinitionC
            );

            // (active & !deleted) | (premium & verified)
            // -> [active, deleted, !, &, premium, verified, &, |]
            when(mockFilterContext.toCondition(eq("active"),any(),any())).thenReturn(mockConditionA);
            when(mockFilterContext.toCondition(eq("deleted"),any(),any())).thenReturn(mockConditionB);
            when(mockFilterContext.toCondition(eq("premium"),any(),any())).thenReturn(mockConditionC);
            when(mockFilterContext.toCondition(eq("verified"),any(),any())).thenReturn(mockConditionC);

            List<String> postfix = List.of(
                "active", "deleted", "!", "&", "premium", "verified", "&", "|"
            );
            Condition result = PostfixConditionBuilder.build(postfix, definitionMap, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("active"),any(),any());
            verify(mockFilterContext).toCondition(eq("deleted"),any(),any());
            verify(mockFilterContext).toCondition(eq("premium"),any(),any());
            verify(mockFilterContext).toCondition(eq("verified"),any(),any());

            verify(mockConditionB, times(1)).not();
            verify(mockConditionC, times(0)).and(mockConditionC);
        }
    }

    @Nested
    @DisplayName("Integration Tests with Context")
    class IntegrationTests {

        @Test
        @DisplayName("Same filter referenced zero times for AND operator")
        void testSameFilterMultipleTimesForAnd() {
            // a & a -> [a, a, &]
            List<String> postfix = List.of("a", "a", "&");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext, times(2)).toCondition(eq("a"),any(),any());
            verify(mockConditionA, times(0)).and(mockConditionA);
        }

        @Test
        @DisplayName("Same filter referenced zero times for OR operator")
        void testSameFilterZeroTimesForOr() {
            // a | a -> [a, a, |]
            List<String> postfix = List.of("a", "a", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext, times(2)).toCondition(eq("a"),any(),any());
            verify(mockConditionA, times(0)).or(mockConditionA);
        }

        @Test
        @DisplayName("All three operators in one expression")
        void testAllThreeOperators() {
            // !a & b | c -> [a, !, b, &, c, |]
            List<String> postfix = List.of("a", "!", "b", "&", "c", "|");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            verify(mockConditionA).not();
            verify(mockConditionResult).and(mockConditionB);
            verify(mockConditionResult).or(mockConditionC);
        }

        @Test
        @DisplayName("Expression with single character identifiers")
        void testSingleCharIdentifiers() {
            List<String> postfix = List.of("a", "b", "&");
            Condition result = PostfixConditionBuilder.build(postfix,  mockedDefinitions, mockFilterContext);

            assertNotNull(result);
            assertEquals(mockConditionResult, result);
        }

        @Test
        @DisplayName("Expression with long identifiers")
        void testLongIdentifiers() {
            Map<String, FilterDefinition<TestRef>> definitionMap = Map.of(
                    "veryLongFilterName1", mockDefinitionA,
                    "veryLongFilterName2", mockDefinitionB
            );
            
            when(mockFilterContext.toCondition(eq("veryLongFilterName1"),any(),any())).thenReturn(mockConditionA);
            when(mockFilterContext.toCondition(eq("veryLongFilterName2"),any(),any())).thenReturn(mockConditionB);

            List<String> postfix = List.of("veryLongFilterName1", "veryLongFilterName2", "&");
            Condition result = PostfixConditionBuilder.build(postfix,  definitionMap, mockFilterContext);

            assertNotNull(result);
            verify(mockFilterContext).toCondition(eq("veryLongFilterName1"),any(),any());
            verify(mockFilterContext).toCondition(eq("veryLongFilterName2"),any(),any());
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Large postfix expression should complete quickly")
        @Disabled
        void testLargeExpression() {
            Map<String, FilterDefinition<TestRef>> definitionMap = new HashMap<>();

            // Create a large expression: a | b | c | ... | z (26 identifiers, 25 ORs)
            java.util.List<String> postfix = new java.util.ArrayList<>();
            postfix.add("a");
            definitionMap.put("a", mockDefinitionA);
            for (char c = 'b'; c <= 'z'; c++) {
                String id = String.valueOf(c);
                definitionMap.put(id, mockDefinitionA);
                when(mockFilterContext.toCondition(eq(id),any(),any())).thenReturn(mockConditionB);
                postfix.add(id);
                postfix.add("|");
            }

            long start = System.nanoTime();
            Condition result = PostfixConditionBuilder.build(postfix, definitionMap, mockFilterContext);
            long duration = System.nanoTime() - start;

            assertNotNull(result);
            // Should complete in less than 1ms (very generous)
            assertTrue(duration < 1_000_000, "Build took too long: " + duration + "ns");
        }
    }
}

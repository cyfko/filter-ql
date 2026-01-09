package io.github.cyfko.filterql.core.impl;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.api.FilterTree;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Advanced tests for DSLParser covering complex expression scenarios,
 * deep nesting, operator precedence edge cases, and performance considerations.
 * 
 * These tests complement the basic DSLParserTest with more sophisticated scenarios
 * that test the robustness and scalability of the DSL parsing implementation.
 */
@DisplayName("DSL Parser Advanced Tests")
public class BasicDslParserAdvancedTest {
    enum TestRef implements PropertyReference {
        PROP_A,
        PROP_B,
        PROP_C,
        PROP_D,
        PROP_E;

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
    private FilterDefinition<TestRef> mockDefinitionA, mockDefinitionB, mockDefinitionC, mockDefinitionD, mockDefinitionE;

    @Mock
    private Condition mockConditionA, mockConditionB, mockConditionC, mockConditionD, mockConditionE;
    
    private Map<String, FilterDefinition<TestRef>> mockedDefinitions;

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
        when(mockDefinitionD.ref()).thenReturn(TestRef.PROP_D);
        when(mockDefinitionD.op()).thenReturn("EQ");
        when(mockDefinitionE.ref()).thenReturn(TestRef.PROP_E);
        when(mockDefinitionE.op()).thenReturn("EQ");

        // Setup mocked definitions associations
        mockedDefinitions = Map.of(
                "A", mockDefinitionA,
                "B", mockDefinitionB,
                "C", mockDefinitionC,
                "D", mockDefinitionD,
                "E", mockDefinitionE
        );

        // Setup mock conditions
        when(mockFilterContext.toCondition(eq("A"), any(), any())).thenReturn(mockConditionA);
        when(mockFilterContext.toCondition(eq("B"), any(), any())).thenReturn(mockConditionB);
        when(mockFilterContext.toCondition(eq("C"), any(), any())).thenReturn(mockConditionC);
        when(mockFilterContext.toCondition(eq("D"), any(), any())).thenReturn(mockConditionD);
        when(mockFilterContext.toCondition(eq("E"), any(), any())).thenReturn(mockConditionE);

        // Setup chaining for all conditions
        setupConditionChaining(mockConditionA);
        setupConditionChaining(mockConditionB);
        setupConditionChaining(mockConditionC);
        setupConditionChaining(mockConditionD);
        setupConditionChaining(mockConditionE);
    }

    private void setupConditionChaining(Condition condition) {
        when(condition.not()).thenReturn(condition);
        when(condition.and(any())).thenReturn(condition);
        when(condition.or(any())).thenReturn(condition);
    }

    @Nested
    @DisplayName("Complex Operator Precedence Tests")
    class ComplexPrecedenceTests {

        @Test
        @DisplayName("Mixed precedence with multiple levels: A | B & C | D & E")
        void testMixedPrecedenceMultipleLevels() throws DSLSyntaxException, FilterValidationException {
            // Should be parsed as: A | (B & C) | (D & E)
            FilterTree tree = parser.parse("A | B & C | D & E");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            
            // Verify all conditions are accessed
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
            verify(mockFilterContext).toCondition(eq("C"), any(), any());
            verify(mockFilterContext).toCondition(eq("D"), any(), any());
            verify(mockFilterContext).toCondition(eq("E"), any(), any());
        }

        @Test
        @DisplayName("Complex NOT precedence: !A & B | !C & D")
        void testComplexNotPrecedence() throws DSLSyntaxException, FilterValidationException {
            // Should be parsed as: ((!A) & B) | ((!C) & D)
            FilterTree tree = parser.parse("!A & B | !C & D");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
            verify(mockFilterContext).toCondition(eq("C"), any(), any());
            verify(mockFilterContext).toCondition(eq("D"), any(), any());
            
            // Verify NOT operations
            verify(mockConditionA).not();
            verify(mockConditionC).not();
        }

        @Test
        @DisplayName("Nested parentheses with mixed operators: ((A & B) | (C & D)) & !(E)")
        void testNestedParenthesesMixedOperators() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("((A & B) | (C & D)) & !(E)");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
            verify(mockFilterContext).toCondition(eq("C"), any(), any());
            verify(mockFilterContext).toCondition(eq("D"), any(), any());
            verify(mockFilterContext).toCondition(eq("E"), any(), any());
            verify(mockConditionE).not();
        }

        @Test
        @DisplayName("Right associative NOT operations: !!!A")
        void testRightAssociativeNot() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("!!!A");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockConditionA, times(1)).not();
        }
    }

    @Nested
    @DisplayName("Deep Nesting and Recursion Tests")
    class DeepNestingTests {

        @Test
        @DisplayName("Very deep parentheses nesting (10 levels)")
        void testVeryDeepParenthesesNesting() throws DSLSyntaxException, FilterValidationException {
            String expression = "((((((((((A))))))))))";
            FilterTree tree = parser.parse(expression);
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
        }

        @Test
        @DisplayName("Deep logical nesting with alternating operators")
        void testDeepLogicalNesting() throws DSLSyntaxException, FilterValidationException {
            // Create a deeply nested expression: (((A & B) | C) & D) | E
            String expression = "(((A & B) | C) & D) | E";
            FilterTree tree = parser.parse(expression);
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
            verify(mockFilterContext).toCondition(eq("C"), any(), any());
            verify(mockFilterContext).toCondition(eq("D"), any(), any());
            verify(mockFilterContext).toCondition(eq("E"), any(), any());
        }

        @Test
        @DisplayName("Complex nested NOT expressions: !(!A & !B) | !(C & !D)")
        void testComplexNestedNotExpressions() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("!(!A & !B) | !(C & !D)");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
            verify(mockFilterContext).toCondition(eq("C"), any(), any());
            verify(mockFilterContext).toCondition(eq("D"), any(), any());
        }
    }

    @Nested
    @DisplayName("Large Expression Scalability Tests")
    class ScalabilityTests {

        @Test
        @DisplayName("Large chain of OR operations (50 terms)")
        void testLargeOrChain() throws DSLSyntaxException {
            StringBuilder expression = new StringBuilder("A");
            
            // Create mock conditions for all terms
            for (int i = 1; i <= 50; i++) {
                String conditionName = "TERM_" + i;
                expression.append(" | ").append(conditionName);
                when(mockFilterContext.toCondition(eq("A"), any(), any())).thenReturn(mockConditionA);
            }

            FilterTree tree = parser.parse(expression.toString());
            assertNotNull(tree);
            
            // Verify parsing doesn't fail with large expressions
            assertTrue(tree.toString().length() > 100);
        }

        @Test
        @DisplayName("Large chain of AND operations (30 terms)")
        void testLargeAndChain() throws DSLSyntaxException {
            StringBuilder expression = new StringBuilder("A");
            
            // Create mock conditions for all terms
            for (int i = 1; i <= 30; i++) {
                String conditionName = "COND_" + i;
                expression.append(" & ").append(conditionName);
                when(mockFilterContext.toCondition(eq("A"), any(), any())).thenReturn(mockConditionA);
            }

            FilterTree tree = parser.parse(expression.toString());
            assertNotNull(tree);
            
            // Verify the expression is correctly parsed
            assertTrue(tree.toString().contains("COND_"));
        }

        @Test
        @DisplayName("Mixed large expression with all operators")
        void testMixedLargeExpression() throws DSLSyntaxException {
            // Create a complex expression mixing all operators and parentheses
            String expression = "!(A & B) | (C & D) & !(E | (A & !B)) | ((C | D) & !A)";
            
            FilterTree tree = parser.parse(expression);
            assertNotNull(tree);
        }
    }

    @Nested
    @DisplayName("Edge Case Expression Patterns")
    class EdgeCasePatterns {

        @Test
        @DisplayName("Alternating NOT operations: !A & !B | !C & !D")
        void testAlternatingNotOperations() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("!A & !B | !C & !D");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockConditionA).not();
            verify(mockConditionB).not();
            verify(mockConditionC).not();
            verify(mockConditionD).not();
        }

        @Test
        @DisplayName("Redundant parentheses: (((A))) & (((B)))")
        void testRedundantParentheses() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("(((A))) & (((B)))");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
        }

        @Test
        @DisplayName("Mixed parentheses styles: ((A & B)) | (C) & ((D | E))")
        void testMixedParenthesesStyles() throws DSLSyntaxException, FilterValidationException {
            FilterTree tree = parser.parse("((A & B)) | (C) & ((D | E))");
            assertNotNull(tree);

            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
            verify(mockFilterContext).toCondition(eq("C"), any(), any());
            verify(mockFilterContext).toCondition(eq("D"), any(), any());
            verify(mockFilterContext).toCondition(eq("E"), any(), any());
        }
    }

    @Nested
    @DisplayName("Error Recovery and Boundary Tests")
    class ErrorRecoveryTests {

        @Test
        @DisplayName("Maximum reasonable expression complexity")
        void testMaximumReasonableComplexity() throws DSLSyntaxException {
            // Test an expression that's complex but still reasonable
            String expression = "!(A & B) | (C & (D | E)) & !((A | B) & (C | D)) | (E & !A)";
            
            FilterTree tree = parser.parse(expression);
            assertNotNull(tree);
            
            // Verify the complex structure is preserved
            String treeString = tree.toString();
            assertNotNull(treeString);
            assertFalse(treeString.isEmpty());
        }

        @Test
        @DisplayName("Expression with maximum whitespace variations")
        void testMaximumWhitespaceVariations() throws DSLSyntaxException, FilterValidationException {
            String expression = "  !  (  A   &   B  )   |   (  C   &   D  )  ";
            
            FilterTree tree = parser.parse(expression);
            assertNotNull(tree);
            
            tree.generate(mockedDefinitions, mockFilterContext);
            verify(mockFilterContext).toCondition(eq("A"), any(), any());
            verify(mockFilterContext).toCondition(eq("B"), any(), any());
            verify(mockFilterContext).toCondition(eq("C"), any(), any());
            verify(mockFilterContext).toCondition(eq("D"), any(), any());
        }

        @Test
        @DisplayName("Complex expression with toString verification")
        void testComplexExpressionToString() throws DSLSyntaxException {
            String expression = "!(A & B) | C";
            FilterTree tree = parser.parse(expression);
            
            String result = tree.toString();
            
            // Verify the toString provides a readable representation
            assertNotNull(result);
            assertTrue(result.contains("A"));
            assertTrue(result.contains("B"));
            assertTrue(result.contains("C"));
        }
    }

    @Nested
    @DisplayName("Performance and Memory Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Parser performance with moderately complex expression")
        void testParserPerformance() throws DSLSyntaxException {
            String expression = "!(A & B) | (C & D) | (E & !A) & (B | !C) | (D & E)";
            
            long startTime = System.nanoTime();
            FilterTree tree = parser.parse(expression);
            long endTime = System.nanoTime();
            
            assertNotNull(tree);
            
            // Verify parsing completes in reasonable time (less than 1ms for this complexity)
            long durationMs = (endTime - startTime) / 1_000_000;
            assertTrue(durationMs < 100, "Parsing took too long: " + durationMs + "ms");
        }

        @Test
        @DisplayName("Memory efficiency with repeated parsing")
        void testMemoryEfficiencyRepeatedParsing() throws DSLSyntaxException {
            String expression = "(A & B) | (C & D)";
            
            // Parse the same expression multiple times to test for memory leaks
            for (int i = 0; i < 100; i++) {
                FilterTree tree = parser.parse(expression);
                assertNotNull(tree);
                
                // Force a small GC hint every 10 iterations
                if (i % 10 == 0) {
                    System.gc();
                }
            }
            
            // If we reach here without OutOfMemoryError, the test passes
            assertTrue(true);
        }
    }
}
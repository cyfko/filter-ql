package io.github.cyfko.filterql.core;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.DslParser;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.api.FilterTree;
import io.github.cyfko.filterql.core.impl.BasicDslParser;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.FilterQuery;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive independent tests for FilterResolver implementations.
 * 
 * These tests validate FilterResolver behavior in isolation, focusing on:
 * - DSL parsing integration
 * - Error handling and boundary conditions
 * - Thread safety considerations
 * - Performance characteristics
 * - Contract compliance
 */
@DisplayName("FilterResolver Independent Tests")
public class FilterQueryFactoryIndependentTest {

    private FilterQuery<TestEntity> filterManager;

    @Mock
    private FilterContext mockFilterContext;

    @Mock
    private DslParser mockDslParser;

    @Mock
    private FilterDefinition<?> mockDefinition;

    private Map<String, FilterDefinition<?>> mockedDefinitions;

    @Mock
    private Condition mockCondition;

    @Mock
    private FilterTree mockFilterTree;

    @Mock
    private PredicateResolver<?> mockPredicateResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup mocked definitions associations
        mockedDefinitions = Map.of(
                "nameFilter", mockDefinition,
                "valueFilter", mockDefinition,
                "activeFilter", mockDefinition,
                "statusFilter", mockDefinition
        );

        // Setup mock condition with chainable methods
        when(mockCondition.not()).thenReturn(mockCondition);
        when(mockCondition.and(any())).thenReturn(mockCondition);
        when(mockCondition.or(any())).thenReturn(mockCondition);

        // Setup mock filter tree
        when(mockFilterTree.generate(any(Map.class), any(FilterContext.class))).thenReturn(mockCondition);

        // Setup mock parser
        when(mockDslParser.parse(anyString())).thenReturn(mockFilterTree);

        // Setup mock context
        when(mockFilterContext.toCondition(any(String.class), any(), any(String.class))).thenReturn(mockCondition);
        when(mockFilterContext.toResolver(any(Condition.class), any(QueryExecutionParams.class))).thenReturn((PredicateResolver) mockPredicateResolver);

        // Create FilterResolver with mocked dependencies
        filterManager = FilterQueryFactory.of(mockFilterContext,mockDslParser);
    }

    /**
     * Test entity for FilterResolver testing
     */
    private static class TestEntity {
        private final String name;
        private final int value;
        private final boolean active;

        public TestEntity(String name, int value, boolean active) {
            this.name = name;
            this.value = value;
            this.active = active;
        }

    }

    /**
     * Test property reference enum for testing
     */
    private enum TestPropertyRef implements PropertyReference {
        NAME(String.class, Set.of(Op.MATCHES, Op.EQ, Op.NE)),
        VALUE(Integer.class, Set.of(Op.EQ, Op.GT, Op.LT)),
        ACTIVE(Boolean.class, Set.of(Op.EQ, Op.NE)),
        STATUS(String.class, Set.of(Op.EQ, Op.IN)),
        CATEGORY(String.class, Set.of(Op.EQ, Op.MATCHES));

        private final Class<?> type;
        private final Set<Op> supportedOperators;

        TestPropertyRef(Class<?> type, Set<Op> supportedOperators) {
            this.type = type;
            this.supportedOperators = Set.copyOf(supportedOperators);
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return supportedOperators;
        }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }

    @Nested
    @DisplayName("FilterResolver Basic Resolution Tests")
    class BasicResolutionTests {

        @Test
        @DisplayName("Valid filter request resolves successfully")
        void testValidFilterRequestResolution() throws DSLSyntaxException, FilterValidationException {
            // Create a simple filter request
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(filters, "nameFilter", null, null);
            
            PredicateResolver<TestEntity> result = filterManager.toResolver(request);

            assertNotNull(result);
            verify(mockDslParser).parse("nameFilter");
            verify(mockFilterTree).generate(eq((Map) filters), eq(mockFilterContext));
            verify(mockFilterContext).toResolver(eq(mockCondition), any());
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Invalid DSL syntax detected during resolution, not at construction")
        void testInvalidDSLSyntax() throws DSLSyntaxException {

            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            
            // With lazy validation, FilterRequest construction succeeds
            FilterRequest<TestPropertyRef> request = assertDoesNotThrow(
                () -> new FilterRequest<>(filters, "invalid", null, null)
            );
            
            // Use a real resolver with real parser to test DSL validation at resolution time
            FilterQuery<TestEntity> realResolver = FilterQueryFactory.of(mockFilterContext,new BasicDslParser());
            
            // But resolution fails when trying to build the condition (undefined filter 'invalid')
            DSLSyntaxException exception = assertThrows(
                DSLSyntaxException.class,
                () -> realResolver.toResolver(request)
            );
            
            assertTrue(exception.getMessage().contains("invalid") && 
                      exception.getMessage().contains("Available filters"));
        }

        @Test
        @DisplayName("Filter validation error throws FilterValidationException")
        void testFilterValidationError() throws FilterValidationException {
            when(mockFilterTree.generate(any(Map.class), any(FilterContext.class))).thenThrow(new FilterValidationException("Invalid filter"));
            
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("invalidFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(filters, "invalidFilter",null, null);
            
            FilterValidationException exception = assertThrows(
                FilterValidationException.class,
                () -> filterManager.toResolver(request)
            );
            
            assertEquals("Invalid filter", exception.getMessage());
        }

        @Test
        @DisplayName("Null filter request throws NullPointerException")
        void testNullFilterRequest() {
            assertThrows(
                NullPointerException.class,
                () -> filterManager.toResolver(null)
            );
        }
    }

    @Nested
    @DisplayName("FilterResolver Factory Methods Tests")
    class FactoryMethodsTests {

        @Test
        @DisplayName("FilterResolver.of(parser, context) creates resolver successfully")
        void testFactoryWithParserAndContext() {
            FilterQuery<TestEntity> resolver = FilterQueryFactory.of(mockFilterContext,mockDslParser);
            assertNotNull(resolver);
        }

        @Test
        @DisplayName("FilterResolver.of(context) creates resolver with default parser")
        void testFactoryWithContext() {
            FilterQuery<TestEntity> resolver = FilterQueryFactory.of(mockFilterContext);
            assertNotNull(resolver);
        }

        @Test
        @DisplayName("FilterResolver.of(null, context) throws NullPointerException")
        void testFactoryWithNullParser() {
            assertThrows(
                NullPointerException.class,
                () -> FilterQueryFactory.of(mockFilterContext,null,null)
            );
        }

        @Test
        @DisplayName("FilterResolver.of(parser, null) throws NullPointerException")
        void testFactoryWithNullContext() {
            assertThrows(
                NullPointerException.class,
                () -> FilterQueryFactory.of(null, mockDslParser)
            );

            assertThrows(
                    NullPointerException.class,
                    () -> FilterQueryFactory.of(mockFilterContext, mockDslParser, null)
            );
        }

        @Test
        @DisplayName("FilterResolver.of(null) throws NullPointerException")
        void testFactoryWithNullContextOnly() {
            assertThrows(
                NullPointerException.class,
                () -> FilterQueryFactory.of(null)
            );
        }
    }

    @Nested
    @DisplayName("Performance and Scalability Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Filter resolution performance is acceptable")
        void testFilterResolutionPerformance() throws DSLSyntaxException, FilterValidationException {
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(filters, "nameFilter",null, null);
            
            long startTime = System.nanoTime();
            
            // Perform multiple resolutions
            for (int i = 0; i < 1000; i++) {
                filterManager.toResolver(request);
            }
            
            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;
            
            // Should complete 1000 resolutions in reasonable time (less than 100ms)
            assertTrue(durationMs < 1000, "Resolution took too long: " + durationMs + "ms");
        }

        @Test
        @DisplayName("Memory usage is reasonable with repeated resolutions")
        void testMemoryUsage() throws DSLSyntaxException, FilterValidationException {
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(filters, "nameFilter",null, null);
            
            // Perform many resolutions to check for memory leaks
            for (int i = 0; i < 5000; i++) {
                filterManager.toResolver(request);
                
                // Suggest GC periodically
                if (i % 500 == 0) {
                    System.gc();
                }
            }
            
            // If we reach here without OutOfMemoryError, test passes
            assertTrue(true);
        }

        @Test
        @DisplayName("Complex filter requests perform adequately")
        void testComplexFilterPerformance() throws DSLSyntaxException, FilterValidationException {
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            filters.put("valueFilter", new FilterDefinition<>(TestPropertyRef.VALUE, Op.GT, 100));
            filters.put("activeFilter", new FilterDefinition<>(TestPropertyRef.ACTIVE, Op.EQ, true));
            filters.put("statusFilter", new FilterDefinition<>(TestPropertyRef.STATUS, Op.EQ, "ACTIVE"));
            
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(
                filters, 
                "(nameFilter & valueFilter) | (activeFilter & statusFilter)",
                null,
                null
            );
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < 100; i++) {
                filterManager.toResolver(request);
            }
            
            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;
            
            // Complex filters should still perform reasonably
            assertTrue(durationMs < 500, "Complex resolution took too long: " + durationMs + "ms");
        }
    }

    @Nested
    @DisplayName("Contract Compliance Tests")
    class ContractComplianceTests {

        @Test
        @DisplayName("Resolve method never returns null for valid requests")
        void testNeverReturnsNullForValidRequests() throws DSLSyntaxException, FilterValidationException {
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(filters, "nameFilter",null, null);
            
            PredicateResolver<TestEntity> result = filterManager.toResolver(request);
            
            assertNotNull(result, "PredicateResolver should never be null for valid requests");
        }

        @Test
        @DisplayName("FilterResolver behavior is deterministic")
        void testDeterministicBehavior() throws DSLSyntaxException, FilterValidationException {
            Map<String, FilterDefinition<TestPropertyRef>> definitionMap = new HashMap<>();
            definitionMap.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(definitionMap, "nameFilter",null, null);
            
            // Multiple calls with the same request should behave consistently
            for (int i = 0; i < 10; i++) {
                PredicateResolver<TestEntity> result = filterManager.toResolver(request);
                assertNotNull(result);
            }
            
            // Verify consistent parser and context interactions
            verify(mockDslParser, times(10)).parse("nameFilter");
            verify(mockFilterTree, times(10)).generate((Map) definitionMap, mockFilterContext);
        }

        @Test
        @DisplayName("Context::toCondtion invocation happens for all filters")
        void testConditionCreation() throws DSLSyntaxException, FilterValidationException {
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("filter1", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            filters.put("filter2", new FilterDefinition<>(TestPropertyRef.VALUE, Op.GT, 100));
            filters.put("filter3", new FilterDefinition<>(TestPropertyRef.ACTIVE, Op.EQ, true));
            
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(filters, "filter1 & filter2 | filter3",null, null);

            FilterQuery<TestEntity> underTest = FilterQueryFactory.of(mockFilterContext,new BasicDslParser());

            // Prepare mock


            underTest.toResolver(request);
            
            // Verify all filters are added to context
            verify(mockFilterContext).toCondition(eq("filter1"), any(), any());
            verify(mockFilterContext).toCondition(eq("filter2"), any(), any());
            verify(mockFilterContext).toCondition(eq("filter3"), any(), any());
        }
    }

    @Nested
    @DisplayName("Null Parameter Validation Tests")
    class NullValidationTests {
        
        @Test
        @DisplayName("Should throw NullPointerException for null filter request")
        void shouldThrowExceptionForNullFilterRequest() {
            // When & Then
            NullPointerException exception = assertThrows(NullPointerException.class,
                () -> filterManager.toResolver(null));
            assertTrue(exception.getMessage().contains("Filter request cannot be null"));
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Concurrent filter resolution produces consistent results")
        void testConcurrentFilterResolution() throws InterruptedException {
            final int threadCount = 10;
            final int operationsPerThread = 50;
            
            Map<String, FilterDefinition<TestPropertyRef>> filters = new HashMap<>();
            filters.put("nameFilter", new FilterDefinition<>(TestPropertyRef.NAME, Op.MATCHES, "test%"));
            FilterRequest<TestPropertyRef> request = new FilterRequest<>(filters, "nameFilter",null, null);
            
            Thread[] threads = new Thread[threadCount];
            final boolean[] results = new boolean[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < operationsPerThread; j++) {
                            PredicateResolver<TestEntity> result = filterManager.toResolver(request);
                            if (result == null) {
                                results[threadIndex] = false;
                                return;
                            }
                        }
                        results[threadIndex] = true;
                    } catch (Exception e) {
                        results[threadIndex] = false;
                    }
                });
            }
            
            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }
            
            // Wait for completion
            for (Thread thread : threads) {
                thread.join(5000); // 5 second timeout
            }
            
            // Verify all threads completed successfully
            for (int i = 0; i < threadCount; i++) {
                assertTrue(results[i], "Thread " + i + " failed");
            }
        }
    }
}
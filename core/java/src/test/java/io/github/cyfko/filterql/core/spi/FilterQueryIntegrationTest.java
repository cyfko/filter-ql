package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for FilterQuery SPI interfaces using a generic context.
 * These tests verify the contract of the SPI interfaces without any
 * JPA-specific dependencies, demonstrating backend-agnostic design.
 */
class FilterQueryIntegrationTest {

    static class DummyEntity {}
    
    /**
     * A simple context object representing any backend context.
     * In a real JPA implementation this would be EntityManager,
     * in MongoDB it could be MongoDatabase, etc.
     */
    record TestContext(String name) {}
    
    enum DummyProperty implements PropertyReference {
        NAME;
        public Class<?> getType() { return String.class; }
        public Set<Op> getSupportedOperators() { return java.util.Collections.emptySet(); }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }

    /**
     * Concrete implementation of ExecutionStrategy for testing.
     * Required because ExecutionStrategy.execute() is a generic method
     * and lambdas cannot implement generic methods.
     */
    static class TestListMapStrategy implements ExecutionStrategy<List<Map<String,Object>>> {
        @Override
        public <Context> List<Map<String, Object>> execute(
                Context ctx,
                PredicateResolver<?> predicateResolver,
                QueryExecutionParams params) {
            return List.of(Map.of("name", "John"));
        }
    }

    static class TestEntityListStrategy implements ExecutionStrategy<List<DummyEntity>> {
        private final boolean shouldThrow;
        
        TestEntityListStrategy(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }
        
        @Override
        public <Context> List<DummyEntity> execute(
                Context ctx,
                PredicateResolver<?> predicateResolver,
                QueryExecutionParams params) {
            if (shouldThrow) {
                throw new RuntimeException("fail");
            }
            return List.of(new DummyEntity());
        }
    }

    static class TestStringListStrategy implements ExecutionStrategy<List<String>> {
        @Override
        public <Context> List<String> execute(
                Context ctx,
                PredicateResolver<?> predicateResolver,
                QueryExecutionParams params) {
            return List.of("ok");
        }
    }

    @Test
    void testQueryExecutorExecutesWithCustomStrategy() {
        FilterQuery<List<Map<String,Object>>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<Map<String,Object>>> executor = mock(QueryExecutor.class);
        ExecutionStrategy<List<Map<String,Object>>> strategy = new TestListMapStrategy();

        TestContext ctx = new TestContext("test");
        
        when(handler.toExecutor(request)).thenReturn((QueryExecutor) executor);
        when(executor.executeWith(eq(ctx), eq(strategy))).thenReturn(List.of(Map.of("name", "John")));

        List<Map<String,Object>> results = handler.<DummyProperty, List<Map<String,Object>>>toExecutor(request).executeWith(ctx, strategy);
        assertEquals(1, results.size());
        assertEquals("John", results.getFirst().get("name"));
        verify(executor).executeWith(eq(ctx), eq(strategy));
    }

    @Test
    void testHandlerDefaultExecuteMethod() {
        FilterQuery<List<DummyEntity>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        ExecutionStrategy<List<DummyEntity>> strategy = new TestEntityListStrategy(false);
        TestContext ctx = new TestContext("test");
        
        when(handler.execute(eq(request), eq(ctx), eq(strategy))).thenReturn(List.of(new DummyEntity()));
        List<DummyEntity> results = handler.execute(request, ctx, strategy);
        assertEquals(1, results.size());
    }

    @Test
    void testProjectionReturnsMapResults() {
        FilterQuery<Map<String,Object>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<Map<String,Object>>> executor = mock(QueryExecutor.class);
        ExecutionStrategy<List<Map<String,Object>>> strategy = new TestListMapStrategy();
        TestContext ctx = new TestContext("test");

        when(handler.toExecutor(request)).thenReturn((QueryExecutor) executor);
        when(executor.executeWith(eq(ctx), eq(strategy))).thenReturn(List.of(Map.of("field", 42)));

        List<Map<String,Object>> results = handler.<DummyProperty, List<Map<String,Object>>>toExecutor(request).executeWith(ctx, strategy);
        assertEquals(42, results.getFirst().get("field"));
    }

    @Test
    void testExecutionStrategyThrowsException() {
        FilterQuery<List<DummyEntity>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<DummyEntity>> executor = mock(QueryExecutor.class);
        when(handler.<DummyProperty, List<DummyEntity>>toExecutor(request)).thenReturn(executor);

        ExecutionStrategy<List<DummyEntity>> strategy = new TestEntityListStrategy(true);
        TestContext ctx = new TestContext("test");

        when(executor.executeWith(eq(ctx), any())).thenThrow(new RuntimeException("fail"));
        assertThrows(RuntimeException.class, () -> handler.<DummyProperty, List<DummyEntity>>toExecutor(request).executeWith(ctx, strategy));
    }

    @Test
    void testQueryExecutorTypeSafety() {
        FilterQuery<List<String>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<String>> executor = mock(QueryExecutor.class);
        ExecutionStrategy<List<String>> strategy = new TestStringListStrategy();
        TestContext ctx = new TestContext("test");

        when(handler.<DummyProperty,List<String>>toExecutor(request)).thenReturn(executor);
        when(executor.executeWith(eq(ctx), eq(strategy))).thenReturn(List.of("ok"));

        List<String> results = handler.<DummyProperty,List<String>>toExecutor(request).executeWith(ctx, strategy);
        assertEquals(List.of("ok"), results);
    }
}

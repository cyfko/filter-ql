package io.github.cyfko.filterql.core.spi;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FilterQueryIntegrationTest {

    static class DummyEntity {}
    enum DummyProperty implements PropertyReference {
        NAME;
        public Class<?> getType() { return String.class; }
        public Set<Op> getSupportedOperators() { return java.util.Collections.emptySet(); }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }

    @Test
    void testQueryExecutorExecutesWithCustomStrategy() {
        FilterQuery<List<Map<String,Object>>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<Map<String,Object>>> executor = mock(QueryExecutor.class);
        ExecutionStrategy<List<Map<String,Object>>> strategy = mock(ExecutionStrategy.class);

        when(handler.toExecutor(request)).thenReturn((QueryExecutor) executor);
        when(executor.executeWith(any(EntityManager.class), eq(strategy))).thenReturn(List.of(Map.of("name", "John")));

        List<Map<String,Object>> results = handler.<DummyProperty, List<Map<String,Object>>>toExecutor(request).executeWith(mock(EntityManager.class), strategy);
        assertEquals(1, results.size());
        assertEquals("John", results.getFirst().get("name"));
        verify(executor).executeWith(any(EntityManager.class), eq(strategy));
    }

    @Test
    void testHandlerDefaultExecuteMethod() {
        FilterQuery<List<DummyEntity>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        ExecutionStrategy<List<DummyEntity>> strategy = mock(ExecutionStrategy.class);
        when(handler.execute(eq(request), any(EntityManager.class), eq(strategy))).thenReturn(List.of(new DummyEntity()));
        List<DummyEntity> results = handler.execute(request, mock(EntityManager.class), strategy);
        assertEquals(1, results.size());
    }

    @Test
    void testProjectionReturnsMapResults() {
        FilterQuery<Map<String,Object>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<Map<String,Object>>> executor = mock(QueryExecutor.class);
        ExecutionStrategy<List<Map<String,Object>>> strategy = mock(ExecutionStrategy.class);

        when(handler.toExecutor(request)).thenReturn((QueryExecutor) executor);
        when(executor.executeWith(any(EntityManager.class), eq(strategy))).thenReturn(List.of(Map.of("field", 42)));

        List<Map<String,Object>> results = handler.<DummyProperty, List<Map<String,Object>>>toExecutor(request).executeWith(mock(EntityManager.class), strategy);
        assertEquals(42, results.getFirst().get("field"));
    }

    @Test
    void testExecutionStrategyThrowsException() {
        FilterQuery<List<DummyEntity>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<DummyEntity>> executor = mock(QueryExecutor.class);
        when(handler.<DummyProperty, List<DummyEntity>>toExecutor(request)).thenReturn(executor);

        ExecutionStrategy<List<DummyEntity>> strategy = (em, pr, params) -> {
            throw new RuntimeException("fail");
        };

        when(executor.executeWith(any(EntityManager.class), any())).thenThrow(new RuntimeException("fail"));
        assertThrows(RuntimeException.class, () -> handler.<DummyProperty, List<DummyEntity>>toExecutor(request).executeWith(mock(EntityManager.class), strategy));
    }

    @Test
    void testQueryExecutorTypeSafety() {
        FilterQuery<List<String>> handler = mock(FilterQuery.class);
        FilterRequest<DummyProperty> request = mock(FilterRequest.class);
        QueryExecutor<List<String>> executor = mock(QueryExecutor.class);
        ExecutionStrategy<List<String>> strategy = mock(ExecutionStrategy.class);

        when(handler.<DummyProperty,List<String>>toExecutor(request)).thenReturn(executor);
        when(executor.executeWith(any(EntityManager.class), eq(strategy))).thenReturn(List.of("ok"));

        List<String> results = handler.<DummyProperty,List<String>>toExecutor(request).executeWith(mock(EntityManager.class), strategy);
        assertEquals(List.of("ok"), results);
    }
}

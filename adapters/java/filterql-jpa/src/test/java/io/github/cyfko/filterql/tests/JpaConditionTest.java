package io.github.cyfko.filterql.tests;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.jpa.JpaCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires simples pour FilterCondition.
 */
class JpaConditionTest {

    @Test
    void testConstructor() {
        PredicateResolver<TestEntity> resolver = mock(PredicateResolver.class);
        JpaCondition<TestEntity> adapter = new JpaCondition<>(resolver);
        
        assertNotNull(adapter);
        assertEquals(resolver, adapter.getResolver());
    }

    @Test
    void testGetPredicateResolver() {
        PredicateResolver<TestEntity> resolver = mock(PredicateResolver.class);
        JpaCondition<TestEntity> adapter = new JpaCondition<>(resolver);

        PredicateResolver<TestEntity> result = adapter.getResolver();
        assertEquals(resolver, result);
    }

    @Test
    void testAndOperation() {
        PredicateResolver<TestEntity> spec1 = mock(PredicateResolver.class);
        PredicateResolver<TestEntity> spec2 = mock(PredicateResolver.class);
        
        JpaCondition<TestEntity> adapter1 = new JpaCondition<>(spec1);
        JpaCondition<TestEntity> adapter2 = new JpaCondition<>(spec2);
        
        Condition result = adapter1.and(adapter2);
        
        assertNotNull(result);
        assertTrue(result instanceof JpaCondition);
    }

    @Test
    void testOrOperation() {
        PredicateResolver<TestEntity> spec1 = mock(PredicateResolver.class);
        PredicateResolver<TestEntity> spec2 = mock(PredicateResolver.class);
        
        JpaCondition<TestEntity> adapter1 = new JpaCondition<>(spec1);
        JpaCondition<TestEntity> adapter2 = new JpaCondition<>(spec2);
        
        Condition result = adapter1.or(adapter2);
        
        assertNotNull(result);
        assertTrue(result instanceof JpaCondition);
    }

    @Test
    void testNotOperation() {
        PredicateResolver<TestEntity> resolver = mock(PredicateResolver.class);
        JpaCondition<TestEntity> adapter = new JpaCondition<>(resolver);
        
        Condition result = adapter.not();
        
        assertNotNull(result);
        assertTrue(result instanceof JpaCondition);
    }

    @Test
    void testAndOperationWithNonJpaCondition() {
        PredicateResolver<TestEntity> resolver = mock(PredicateResolver.class);
        JpaCondition<TestEntity> adapter = new JpaCondition<>(resolver);
        Condition nonJpaCondition = mock(Condition.class);
        
        assertThrows(IllegalArgumentException.class, () -> {
            adapter.and(nonJpaCondition);
        });
    }

    @Test
    void testOrOperationWithNonJpaCondition() {
        PredicateResolver<TestEntity> resolver = mock(PredicateResolver.class);
        JpaCondition<TestEntity> adapter = new JpaCondition<>(resolver);
        Condition nonJpaCondition = mock(Condition.class);
        
        assertThrows(IllegalArgumentException.class, () -> {
            adapter.or(nonJpaCondition);
        });
    }

    /**
     * Classe d'entité de test simple.
     */
    static class TestEntity {
        private Long id;
        private String name;
        
        public TestEntity() {}
        
        public TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
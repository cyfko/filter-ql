package io.github.cyfko.filterql.core;

import io.github.cyfko.filterql.core.api.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class ConditionTest {

    private TestCondition condition1;
    private TestCondition condition2;
    private TestCondition condition3;

    @BeforeEach
    void setUp() {
        condition1 = new TestCondition("condition1");
        condition2 = new TestCondition("condition2");
        condition3 = new TestCondition("condition3");
    }

    @Test
    @DisplayName("Should combine conditions with AND operation")
    void shouldCombineConditionsWithAndOperation() {
        // When
        Condition result = condition1.and(condition2);

        // Then
        assertNotNull(result);
        assertInstanceOf(TestCondition.AndCondition.class, result);
    }

    @Test
    @DisplayName("Should combine conditions with OR operation")
    void shouldCombineConditionsWithOrOperation() {
        // When
        Condition result = condition1.or(condition2);

        // Then
        assertNotNull(result);
        assertInstanceOf(TestCondition.OrCondition.class, result);
    }

    @Test
    @DisplayName("Should negate condition with NOT operation")
    void shouldNegateConditionWithNotOperation() {
        // When
        Condition result = condition1.not();

        // Then
        assertNotNull(result);
        assertInstanceOf(TestCondition.NotCondition.class, result);
    }

    @Test
    @DisplayName("Should chain multiple operations")
    void shouldChainMultipleOperations() {
        // When
        Condition result = condition1.and(condition2).or(condition3).not();

        // Then
        assertNotNull(result);
        assertInstanceOf(TestCondition.NotCondition.class, result);
    }

    @Test
    @DisplayName("Should handle null conditions in operations")
    void shouldHandleNullConditionsInOperations() {
        // When & Then
        assertThrows(NullPointerException.class, () -> condition1.and(null));
        assertThrows(NullPointerException.class, () -> condition1.or(null));
    }

    // Test implementation of Condition interface
    private static class TestCondition implements Condition {
        private final String name;

        public TestCondition(String name) {
            this.name = name;
        }

        @Override
        public Condition and(Condition other) {
            if (other == null) throw new NullPointerException("Other condition cannot be null");
            return new AndCondition(this, other);
        }

        @Override
        public Condition or(Condition other) {
            if (other == null) throw new NullPointerException("Other condition cannot be null");
            return new OrCondition(this, other);
        }

        @Override
        public Condition not() {
            return new NotCondition(this);
        }

        @Override
        public String toString() {
            return "TestCondition{" + name + "}";
        }

        static class AndCondition implements Condition {
            private final Condition left, right;

            AndCondition(Condition left, Condition right) {
                this.left = left;
                this.right = right;
            }

            @Override
            public Condition and(Condition other) {
                if (other == null) throw new NullPointerException("Other condition cannot be null");
                return new AndCondition(this, other);
            }

            @Override
            public Condition or(Condition other) {
                if (other == null) throw new NullPointerException("Other condition cannot be null");
                return new OrCondition(this, other);
            }

            @Override
            public Condition not() {
                return new NotCondition(this);
            }
        }

        static class OrCondition implements Condition {
            private final Condition left, right;

            OrCondition(Condition left, Condition right) {
                this.left = left;
                this.right = right;
            }

            @Override
            public Condition and(Condition other) {
                if (other == null) throw new NullPointerException("Other condition cannot be null");
                return new AndCondition(this, other);
            }

            @Override
            public Condition or(Condition other) {
                if (other == null) throw new NullPointerException("Other condition cannot be null");
                return new OrCondition(this, other);
            }

            @Override
            public Condition not() {
                return new NotCondition(this);
            }
        }

        static class NotCondition implements Condition {
            private final Condition condition;

            NotCondition(Condition condition) {
                this.condition = condition;
            }

            @Override
            public Condition and(Condition other) {
                if (other == null) throw new NullPointerException("Other condition cannot be null");
                return new AndCondition(this, other);
            }

            @Override
            public Condition or(Condition other) {
                if (other == null) throw new NullPointerException("Other condition cannot be null");
                return new OrCondition(this, other);
            }

            @Override
            public Condition not() {
                return new NotCondition(this);
            }
        }
    }
}


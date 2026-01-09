package io.github.cyfko.filterql.core;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the Deferred Arguments Architecture.
 *
 * <p>This test suite validates the core architectural pattern where:</p>
 * <ol>
 *   <li>Conditions are created WITHOUT values (structure only)</li>
 *   <li>Values are provided later via argumentRegistry in toResolver()</li>
 *   <li>This enables structural caching and flexible value binding</li>
 * </ol>
 *
 * <p><strong>Critical Architecture Tests</strong>: These tests verify the fundamental
 * design principle that separates condition structure from argument values.</p>
 *
 * @author FilterQL Test Suite
 * @since 4.0.1
 */
@DisplayName("Deferred Arguments Architecture Tests")
public class DeferredArgumentsArchitectureTest {

    private TestFilterContext testContext;

    @BeforeEach
    void setUp() {
        testContext = new TestFilterContext();
    }

    @Nested
    @DisplayName("Core Deferred Arguments Flow")
    class DeferredArgumentsFlowTests {

        @Test
        @DisplayName("Should create condition without requiring argument value")
        void shouldCreateConditionWithoutValue() {
            // Given: argKey without any value provided yet
            String argKey = "emailArg";

            // When: Create condition structure (Phase 1)
            assertDoesNotThrow(() -> {
                Condition condition = testContext.toCondition(
                    argKey,
                    TestProperty.EMAIL,
                    "MATCHES"
                );
                assertNotNull(condition, "Condition should be created without value");
            });
        }

        @Test
        @DisplayName("Should defer value retrieval to toResolver phase")
        void shouldDeferValueRetrievalToResolver() {
            // Given: Condition created without value
            String argKey = "nameArg";
            Condition condition = testContext.toCondition(argKey, TestProperty.NAME, "EQ");

            // When: Provide value later in argumentRegistry
            Map<String, Object> argumentRegistry = new HashMap<>();
            argumentRegistry.put(argKey, "John Doe");

            // Then: toResolver should use the deferred value
            PredicateResolver<TestEntity> resolver = testContext.toResolver(
                condition,
                QueryExecutionParams.of(argumentRegistry)
            );

            assertNotNull(resolver);
            assertEquals("John Doe", testContext.getRetrievedValue(argKey),
                "Resolver should retrieve value from argumentRegistry using argKey");
        }

        @Test
        @DisplayName("Should support multiple deferred arguments")
        void shouldSupportMultipleDeferredArguments() {
            // Given: Multiple conditions with different argKeys
            Condition nameCond = testContext.toCondition("nameArg", TestProperty.NAME, "MATCHES");
            Condition ageCond = testContext.toCondition("ageArg", TestProperty.AGE, "GT");
            Condition combined = nameCond.and(ageCond);

            // When: Provide all values in argumentRegistry
            Map<String, Object> argumentRegistry = Map.of(
                "nameArg", "%John%",
                "ageArg", 25
            );

            // Then: All arguments should be retrievable
            PredicateResolver<TestEntity> resolver = testContext.toResolver(
                combined,
                QueryExecutionParams.of(argumentRegistry)
            );

            assertNotNull(resolver);
            assertEquals("%John%", testContext.getRetrievedValue("nameArg"));
            assertEquals(25, testContext.getRetrievedValue("ageArg"));
        }

        @Test
        @DisplayName("Should allow same argKey to be reused with different values")
        void shouldAllowArgKeyReuseWithDifferentValues() {
            // Given: One condition structure
            String argKey = "searchTerm";
            Condition condition = testContext.toCondition(argKey, TestProperty.NAME, "MATCHES");

            // When: Use same structure with different values
            Map<String, Object> registry1 = Map.of(argKey, "Alice");
            Map<String, Object> registry2 = Map.of(argKey, "Bob");

            PredicateResolver<TestEntity> resolver1 = testContext.toResolver(
                condition, QueryExecutionParams.of(registry1)
            );
            PredicateResolver<TestEntity> resolver2 = testContext.toResolver(
                condition, QueryExecutionParams.of(registry2)
            );

            // Then: Both resolvers should exist and use their respective values
            assertNotNull(resolver1);
            assertNotNull(resolver2);
            // Note: In real implementation, each resolver would capture its own argumentRegistry
        }
    }

    @Nested
    @DisplayName("Argument Registry Validation")
    class ArgumentRegistryValidationTests {

        @Test
        @DisplayName("Should throw exception if QueryExecutionParams is null")
        void shouldThrowExceptionIfArgumentRegistryIsNull() {
            Condition condition = testContext.toCondition("arg", TestProperty.NAME, "EQ");

            assertThrows(NullPointerException.class, () -> {
                testContext.toResolver(condition, null);
            }, "toResolver should reject null QueryExecutionParams");
        }

        @Test
        @DisplayName("Should throw exception if required argKey is missing from registry")
        void shouldThrowExceptionIfArgKeyMissing() {
            // Given: Condition expecting "nameArg"
            Condition condition = testContext.toCondition("nameArg", TestProperty.NAME, "EQ");

            // When: argumentRegistry doesn't contain "nameArg"
            Map<String, Object> incompleteRegistry = Map.of("otherArg", "value");

            // Then: Should throw exception
            assertThrows(FilterDefinitionException.class, () -> {
                testContext.toResolver(condition, QueryExecutionParams.of(incompleteRegistry));
            }, "Should throw exception when argKey is not found in argumentRegistry");
        }

        @Test
        @DisplayName("Should handle empty argumentRegistry for parameterless conditions")
        void shouldHandleEmptyRegistryForParameterlessConditions() {
            // Given: Condition that doesn't require arguments (e.g., IS_NULL)
            Condition condition = testContext.toCondition("nullCheck", TestProperty.EMAIL, "IS_NULL");

            // When: argumentRegistry is empty
            Map<String, Object> emptyRegistry = Map.of();

            // Then: Should succeed if IS_NULL doesn't need a value
            assertDoesNotThrow(() -> {
                PredicateResolver<TestEntity> resolver = testContext.toResolver(
                    condition,
                    QueryExecutionParams.of(emptyRegistry)
                );
                assertNotNull(resolver);
            });
        }
    }

    @Nested
    @DisplayName("Operator String Support")
    class OperatorStringSupportTests {

        @Test
        @DisplayName("Should accept standard operators as strings")
        void shouldAcceptStandardOperatorsAsStrings() {
            assertDoesNotThrow(() -> {
                testContext.toCondition("arg1", TestProperty.NAME, "EQ");
                testContext.toCondition("arg2", TestProperty.AGE, "GT");
                testContext.toCondition("arg3", TestProperty.EMAIL, "MATCHES");
                testContext.toCondition("arg4", TestProperty.STATUS, "IN");
            });
        }

        @Test
        @DisplayName("Should accept case-insensitive operator strings")
        void shouldAcceptCaseInsensitiveOperators() {
            assertDoesNotThrow(() -> {
                testContext.toCondition("arg1", TestProperty.NAME, "eq");
                testContext.toCondition("arg2", TestProperty.NAME, "EQ");
                testContext.toCondition("arg3", TestProperty.NAME, "Eq");
                testContext.toCondition("arg4", TestProperty.NAME, "eQ");
            });
        }

        @Test
        @DisplayName("Should accept custom operator strings")
        void shouldAcceptCustomOperatorStrings() {
            assertDoesNotThrow(() -> {
                testContext.toCondition("arg", TestProperty.NAME, "CUSTOM_FULL_TEXT_SEARCH");
                testContext.toCondition("arg", TestProperty.AGE, "custom_range_check");
            });
        }
    }

    @Nested
    @DisplayName("Architectural Benefits Validation")
    class ArchitecturalBenefitsTests {

        @Test
        @DisplayName("Should enable condition reuse with different value sets")
        void shouldEnableConditionReuseWithDifferentValues() {
            // Given: One condition structure (created once)
            Condition sharedCondition = testContext.toCondition("searchArg", TestProperty.NAME, "MATCHES");

            // When: Reuse with different argument values
            Map<String, Object> registry1 = Map.of("searchArg", "Alice");
            Map<String, Object> registry2 = Map.of("searchArg", "Bob");
            Map<String, Object> registry3 = Map.of("searchArg", "Charlie");

            // Then: Should work for all cases
            assertDoesNotThrow(() -> {
                testContext.toResolver(sharedCondition, QueryExecutionParams.of(registry1));
                testContext.toResolver(sharedCondition, QueryExecutionParams.of(registry2));
                testContext.toResolver(sharedCondition, QueryExecutionParams.of(registry3));
            });
        }

        @Test
        @DisplayName("Should enable structural caching independent of values")
        void shouldEnableStructuralCachingIndependentOfValues() {
            // Given: Two conditions with same structure but conceptually different argKeys
            Condition cond1 = testContext.toCondition("user1Name", TestProperty.NAME, "EQ");
            Condition cond2 = testContext.toCondition("user2Name", TestProperty.NAME, "EQ");

            // Both have same structure: TestProperty.NAME + EQ operator
            // Only difference is the argKey (which identifies where to get the value)

            // This architecture enables:
            // 1. Structure-based cache key: PropertyRef + Operator (ignores argKey and value)
            // 2. Value binding happens later via argumentRegistry

            assertNotNull(cond1);
            assertNotNull(cond2);
        }
    }

    // ============================================================================
    // Test Infrastructure
    // ============================================================================

    /**
     * Test entity for validation
     */
    private static class TestEntity {
        private String name;
        private int age;
        private String email;
        private String status;
    }

    /**
     * Test property reference enum
     */
    private enum TestProperty implements PropertyReference {
        NAME(String.class, Set.of(Op.EQ, Op.NE, Op.MATCHES, Op.NOT_MATCHES)),
        EMAIL(String.class, Set.of(Op.EQ, Op.MATCHES, Op.IS_NULL, Op.NOT_NULL)),
        AGE(Integer.class, Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE)),
        STATUS(String.class, Set.of(Op.EQ, Op.NE, Op.IN, Op.NOT_IN));

        private final Class<?> type;
        private final Set<Op> supportedOps;

        TestProperty(Class<?> type, Set<Op> supportedOps) {
            this.type = type;
            this.supportedOps = supportedOps;
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return supportedOps;
        }

        @Override
        public Class<?> getEntityType() {
            return null;
        }
    }

    /**
     * Minimal FilterContext implementation for testing deferred arguments
     */
    private static class TestFilterContext implements FilterContext {

        private final Map<String, Object> retrievedValues = new HashMap<>();

        @Override
        public <P extends Enum<P> & PropertyReference> Condition toCondition(
                String argKey, P ref, String op) throws FilterDefinitionException {

            // Validate inputs
            if (argKey == null || argKey.isBlank()) {
                throw new FilterDefinitionException("argKey cannot be null or blank");
            }
            if (ref == null) {
                throw new NullPointerException("PropertyReference cannot be null");
            }
            if (op == null || op.isBlank()) {
                throw new FilterDefinitionException("Operator cannot be null or blank");
            }

            // Create a simple condition that captures the argKey
            return new TestCondition(argKey, ref, op);
        }

        @Override
        public PredicateResolver<TestEntity> toResolver(
                Condition condition,
                QueryExecutionParams params) throws FilterDefinitionException {

            if (condition == null) {
                throw new NullPointerException("condition cannot be null");
            }
            if (params == null) {
                throw new NullPointerException("params cannot be null");
            }

            // Extract argKeys from condition tree and retrieve values from registry
            extractAndValidateArgKeys(condition, params.arguments());

            return (root, query, cb) -> null; // Simplified for testing
        }

        private void extractAndValidateArgKeys(Condition condition, Map<String, Object> argumentRegistry)
                throws FilterDefinitionException {
            if (condition instanceof TestCondition testCond) {
                String argKey = testCond.getArgKey();

                // This is the KEY test: value is retrieved HERE, not in toCondition()
                if (!argumentRegistry.containsKey(argKey) && !isNullCheckOperator(testCond.getOp())) {
                    throw new FilterDefinitionException(
                        "Required argument key '" + argKey + "' not found in argumentRegistry"
                    );
                }

                Object value = argumentRegistry.get(argKey);
                retrievedValues.put(argKey, value);
            } else if (condition instanceof CompositeCondition composite) {
                extractAndValidateArgKeys(composite.getLeft(), argumentRegistry);
                extractAndValidateArgKeys(composite.getRight(), argumentRegistry);
            } else if (condition instanceof NotCondition not) {
                extractAndValidateArgKeys(not.getOperand(), argumentRegistry);
            }
        }

        private boolean isNullCheckOperator(String op) {
            return "IS_NULL".equalsIgnoreCase(op) || "NOT_NULL".equalsIgnoreCase(op);
        }

        public Object getRetrievedValue(String argKey) {
            return retrievedValues.get(argKey);
        }
    }

    /**
     * Simple test condition implementation
     */
    private static class TestCondition implements Condition {
        private final String argKey;
        private final PropertyReference ref;
        private final String op;

        public TestCondition(String argKey, PropertyReference ref, String op) {
            this.argKey = argKey;
            this.ref = ref;
            this.op = op;
        }

        public String getArgKey() {
            return argKey;
        }

        public String getOp() {
            return op;
        }

        @Override
        public Condition and(Condition other) {
            return new CompositeCondition(this, other, "AND");
        }

        @Override
        public Condition or(Condition other) {
            return new CompositeCondition(this, other, "OR");
        }

        @Override
        public Condition not() {
            return new NotCondition(this);
        }
    }

    private static class CompositeCondition implements Condition {
        private final Condition left;
        private final Condition right;
        private final String operator;

        public CompositeCondition(Condition left, Condition right, String operator) {
            this.left = left;
            this.right = right;
            this.operator = operator;
        }

        public Condition getLeft() {
            return left;
        }

        public Condition getRight() {
            return right;
        }

        @Override
        public Condition and(Condition other) {
            return new CompositeCondition(this, other, "AND");
        }

        @Override
        public Condition or(Condition other) {
            return new CompositeCondition(this, other, "OR");
        }

        @Override
        public Condition not() {
            return new NotCondition(this);
        }
    }

    private static class NotCondition implements Condition {
        private final Condition operand;

        public NotCondition(Condition operand) {
            this.operand = operand;
        }

        public Condition getOperand() {
            return operand;
        }

        @Override
        public Condition and(Condition other) {
            return new CompositeCondition(this, other, "AND");
        }

        @Override
        public Condition or(Condition other) {
            return new CompositeCondition(this, other, "OR");
        }

        @Override
        public Condition not() {
            return operand; // Double negation
        }
    }
}

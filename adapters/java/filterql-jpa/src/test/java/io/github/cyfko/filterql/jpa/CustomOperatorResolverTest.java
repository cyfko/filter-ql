package io.github.cyfko.filterql.jpa;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.mappings.CustomOperatorResolver;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CustomOperatorResolver functionality.
 * 
 * <p>CustomOperatorResolver allows centralized handling of custom operators
 * (like SOUNDEX, GEO_WITHIN) that can apply to multiple properties.
 * 
 * <p>Resolution flow (deferred execution):
 * <ol>
 *   <li>toCondition() creates a JpaCondition with a wrapped resolver</li>
 *   <li>At resolution time, CustomOperatorResolver.resolve() is called first</li>
 *   <li>If it returns non-null, that resolver is used</li>
 *   <li>If it returns null, default mechanism (path/PredicateResolverMapping) is used</li>
 * </ol>
 * 
 * <p>Note: Due to deferred arguments architecture, the CustomOperatorResolver
 * is called at predicate resolution time, not at condition creation time.
 */
@DisplayName("CustomOperatorResolver Tests")
class CustomOperatorResolverTest {

    // Test entity (no JPA annotations needed for unit tests)
    static class User {
        Long id;
        String firstName;
        String lastName;
        String email;
        Integer age;
    }

    // Test property reference enum
    enum UserProperty implements PropertyReference {
        FIRST_NAME,
        LAST_NAME,
        EMAIL,
        AGE;
    
        @Override
        public Class<?> getType() {
            return switch(this){
                case FIRST_NAME, LAST_NAME, EMAIL -> String.class;
                case AGE -> Integer.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return Set.of(Op.EQ, Op.MATCHES, Op.GT, Op.LT);
        }

        @Override
        public Class<?> getEntityType() {
            return User.class;
        }
    }

    private JpaFilterContext<UserProperty> context;

    @BeforeEach
    void setUp() {
        context = new JpaFilterContext<>(
            UserProperty.class,
            ref -> switch (ref) {
                case FIRST_NAME -> "firstName";
                case LAST_NAME -> "lastName";
                case EMAIL -> "email";
                case AGE -> "age";
            }
        );
    }

    @Nested
    @DisplayName("Configuration and fluent API")
    class ConfigurationTests {

        @Test
        @DisplayName("Fluent withCustomOperatorResolver should return context for chaining")
        void fluentMethodShouldReturnContextForChaining() {
            // When
            JpaFilterContext<UserProperty> result = context.withCustomOperatorResolver((ref, op, args) -> null);

            // Then
            assertSame(context, result);
        }

        @Test
        @DisplayName("getCustomOperatorResolver should return configured resolver")
        void getterShouldReturnConfiguredResolver() {
            // Given
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> null;
            context.withCustomOperatorResolver(resolver);

            // When
            CustomOperatorResolver<UserProperty> retrieved = context.getCustomOperatorResolver();

            // Then
            assertSame(resolver, retrieved);
        }

        @Test
        @DisplayName("getCustomOperatorResolver should return null when not configured")
        void getterShouldReturnNullWhenNotConfigured() {
            // Given - fresh context without resolver

            // When
            CustomOperatorResolver<UserProperty> retrieved = context.getCustomOperatorResolver();

            // Then
            assertNull(retrieved);
        }
    }

    @Nested
    @DisplayName("Condition creation with CustomOperatorResolver")
    class ConditionCreationTests {

        @Test
        @DisplayName("Should create condition when custom resolver is configured")
        void shouldCreateConditionWithCustomResolver() {
            // Given - a custom resolver for SOUNDEX operator
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
                if ("SOUNDEX".equals(op)) {
                    return (root, query, cb) -> cb.conjunction(); // dummy predicate
                }
                return null;
            };
            context.withCustomOperatorResolver(resolver);

            // When - create condition with custom operator
            Condition condition = context.toCondition("arg1", UserProperty.FIRST_NAME, "SOUNDEX");

            // Then - condition should be created successfully
            assertNotNull(condition);
            assertInstanceOf(JpaCondition.class, condition);
        }

        @Test
        @DisplayName("Should create condition when custom resolver returns null")
        void shouldCreateConditionWhenResolverReturnsNull() {
            // Given - resolver that always returns null
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> null;
            context.withCustomOperatorResolver(resolver);

            // When - create condition with standard operator
            Condition condition = context.toCondition("arg1", UserProperty.FIRST_NAME, "EQ");

            // Then - should still work with default mechanism
            assertNotNull(condition);
            assertInstanceOf(JpaCondition.class, condition);
        }

        @Test
        @DisplayName("Should work without custom resolver configured")
        void shouldWorkWithoutCustomResolver() {
            // Given - no custom resolver

            // When - create condition
            Condition condition = context.toCondition("arg1", UserProperty.FIRST_NAME, "EQ");

            // Then - should work normally
            assertNotNull(condition);
            assertInstanceOf(JpaCondition.class, condition);
        }

        @Test
        @DisplayName("Should handle multiple custom operators")
        void shouldHandleMultipleCustomOperators() {
            // Given - resolver handling multiple operators
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
                return switch (op) {
                    case "SOUNDEX" -> (root, query, cb) -> cb.conjunction();
                    case "LEVENSHTEIN" -> (root, query, cb) -> cb.conjunction();
                    case "FULL_TEXT" -> (root, query, cb) -> cb.conjunction();
                    default -> null;
                };
            };
            context.withCustomOperatorResolver(resolver);

            // When/Then - all custom operators should create conditions
            assertDoesNotThrow(() -> context.toCondition("arg1", UserProperty.FIRST_NAME, "SOUNDEX"));
            assertDoesNotThrow(() -> context.toCondition("arg2", UserProperty.LAST_NAME, "LEVENSHTEIN"));
            assertDoesNotThrow(() -> context.toCondition("arg3", UserProperty.FIRST_NAME, "FULL_TEXT"));
        }
    }

    @Nested
    @DisplayName("Condition combination with custom operators")
    class CombinationTests {

        @Test
        @DisplayName("Should combine conditions from custom and standard operators")
        void shouldCombineCustomAndStandardOperators() {
            // Given
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
                if ("SOUNDEX".equals(op)) {
                    return (root, query, cb) -> cb.conjunction();
                }
                return null;
            };
            context.withCustomOperatorResolver(resolver);

            // When - create conditions with both custom and standard operators
            Condition customCond = context.toCondition("arg1", UserProperty.FIRST_NAME, "SOUNDEX");
            Condition standardCond = context.toCondition("arg2", UserProperty.LAST_NAME, "EQ");

            // Then - both should be combinable
            assertNotNull(customCond);
            assertNotNull(standardCond);
            
            Condition combined = customCond.and(standardCond);
            assertNotNull(combined);
            
            Condition orCombined = customCond.or(standardCond);
            assertNotNull(orCombined);
        }

        @Test
        @DisplayName("Should negate custom operator condition")
        void shouldNegateCustomOperatorCondition() {
            // Given
            context.withCustomOperatorResolver((ref, op, args) -> {
                if ("SOUNDEX".equals(op)) {
                    return (root, query, cb) -> cb.conjunction();
                }
                return null;
            });

            // When
            Condition condition = context.toCondition("arg1", UserProperty.FIRST_NAME, "SOUNDEX");
            Condition negated = condition.not();

            // Then
            assertNotNull(negated);
        }
    }

    @Nested
    @DisplayName("Override standard operators")
    class OverrideStandardOperatorsTests {

        @Test
        @DisplayName("Should allow overriding standard operator for specific property")
        void shouldAllowOverridingStandardOperatorForSpecificProperty() {
            // Given - override EQ for EMAIL property
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
                if (ref == UserProperty.EMAIL && "EQ".equals(op)) {
                    // Custom case-insensitive comparison - returns a valid resolver
                    return (root, query, cb) -> cb.conjunction();
                }
                return null;
            };
            context.withCustomOperatorResolver(resolver);

            // When
            Condition condition = context.toCondition("arg1", UserProperty.EMAIL, "EQ");

            // Then
            assertNotNull(condition);
        }

        @Test
        @DisplayName("Standard operator on non-overridden property should use default")
        void standardOperatorOnNonOverriddenPropertyShouldUseDefault() {
            // Given - only override EMAIL
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
                if (ref == UserProperty.EMAIL && "EQ".equals(op)) {
                    return (root, query, cb) -> cb.conjunction();
                }
                return null; // All other properties use default
            };
            context.withCustomOperatorResolver(resolver);

            // When - use EQ on FIRST_NAME (not overridden)
            Condition condition = context.toCondition("arg1", UserProperty.FIRST_NAME, "EQ");

            // Then - should work with default mechanism
            assertNotNull(condition);
        }
    }

    @Nested
    @DisplayName("Integration with PredicateResolverMapping")
    class IntegrationWithMappingTests {

        @Test
        @DisplayName("Context with both CustomOperatorResolver and PredicateResolverMapping should create conditions")
        void contextWithBothShouldCreateConditions() {
            // Given - context with PredicateResolverMapping and CustomOperatorResolver
            JpaFilterContext<UserProperty> contextWithMapping = new JpaFilterContext<>(
                UserProperty.class,
                ref -> {
                    if (ref == UserProperty.FIRST_NAME) {
                        return (io.github.cyfko.filterql.jpa.mappings.PredicateResolverMapping<User>) (op, args) -> {
                            return (root, query, cb) -> cb.conjunction();
                        };
                    }
                    return "name";
                }
            );

            // Add CustomOperatorResolver
            contextWithMapping.withCustomOperatorResolver((ref, op, args) -> {
                if ("SOUNDEX".equals(op)) {
                    return (root, query, cb) -> cb.conjunction();
                }
                return null;
            });

            // When - create conditions with custom and mapping operators
            Condition customCond = contextWithMapping.toCondition("arg1", UserProperty.FIRST_NAME, "SOUNDEX");
            Condition mappingCond = contextWithMapping.toCondition("arg2", UserProperty.FIRST_NAME, "EQ");

            // Then - both should be created
            assertNotNull(customCond);
            assertNotNull(mappingCond);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle resolver that returns resolver for all operators")
        void shouldHandleResolverForAllOperators() {
            // Given - resolver that handles everything
            CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
                return (root, query, cb) -> cb.conjunction();
            };
            context.withCustomOperatorResolver(resolver);

            // When/Then - all operators should work
            assertDoesNotThrow(() -> context.toCondition("arg1", UserProperty.FIRST_NAME, "EQ"));
            assertDoesNotThrow(() -> context.toCondition("arg2", UserProperty.FIRST_NAME, "MATCHES"));
            assertDoesNotThrow(() -> context.toCondition("arg3", UserProperty.AGE, "GT"));
            assertDoesNotThrow(() -> context.toCondition("arg4", UserProperty.AGE, "LT"));
        }

        @Test
        @DisplayName("Should allow replacing custom resolver")
        void shouldAllowReplacingCustomResolver() {
            // Given - first resolver
            CustomOperatorResolver<UserProperty> first = (ref, op, args) -> null;
            context.withCustomOperatorResolver(first);
            
            // When - replace with second resolver
            CustomOperatorResolver<UserProperty> second = (ref, op, args) -> 
                (root, query, cb) -> cb.conjunction();
            context.withCustomOperatorResolver(second);

            // Then - second should be active
            assertSame(second, context.getCustomOperatorResolver());
        }

        @Test
        @DisplayName("Should handle null resolver via withCustomOperatorResolver")
        void shouldHandleNullResolver() {
            // Given - configure then clear
            context.withCustomOperatorResolver((ref, op, args) -> null);
            context.withCustomOperatorResolver(null);

            // When
            CustomOperatorResolver<UserProperty> resolver = context.getCustomOperatorResolver();

            // Then
            assertNull(resolver);

            // And conditions should still work
            Condition condition = context.toCondition("arg1", UserProperty.FIRST_NAME, "EQ");
            assertNotNull(condition);
        }
    }
}

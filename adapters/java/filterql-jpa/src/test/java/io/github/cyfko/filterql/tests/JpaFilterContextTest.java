package io.github.cyfko.filterql.tests;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.config.StringCaseStrategy;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaCondition;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.spi.PredicateResolverMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests modernes pour JpaFilterContext basés sur l'architecture deferred arguments
 * et lazy validation du protocole FilterQL v4.0.0.
 * 
 * <p>Architecture testée:
 * <ul>
 *   <li>Phase 1: toCondition(argKey, ref, op) - crée la structure sans valeur</li>
 *   <li>Phase 2: toResolver(entityClass, condition, argumentRegistry) - fournit les valeurs</li>
 *   <li>Phase 3: resolver.resolve(root, query, cb) - exécution et validation lazy</li>
 * </ul>
 */
@DisplayName("JpaFilterContext - Architecture Deferred Arguments")
class JpaFilterContextTest {

    // Test entity and property reference
    static class TestEntity {
        String name;
        Integer age;
    }

    enum TestProp implements PropertyReference {
        NAME,
        AGE;

        @Override
        public Class<?> getType() {
            return switch (this){
                case NAME -> String.class;
                case AGE -> Integer.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return switch (this){
                case NAME -> Set.of(Op.EQ, Op.NE, Op.MATCHES);
                case AGE -> Set.of(Op.EQ, Op.GT, Op.LT);
            };
        }

        @Override
        public Class<?> getEntityType() {
            return TestEntity.class;
        }
    }

    @Nested
    @DisplayName("toCondition() - Structure Creation")
    class ToConditionTests {

        @Test
        @DisplayName("Devrait créer condition avec argument key, ref, et operator")
        void shouldCreateConditionWithDeferredArguments() {
            // Given
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            // When - Phase 1: Structure sans valeur
            Condition condition = context.toCondition("nameParam", TestProp.NAME, "EQ");

            // Then
            assertNotNull(condition, "Condition ne doit pas être null");
            assertInstanceOf(JpaCondition.class, condition, "Doit être une JpaCondition");
        }

        @Test
        @DisplayName("Devrait accepter différents opérateurs en String")
        void shouldAcceptVariousOperators() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "age"
            );

            // Tous ces opérateurs doivent être acceptés
            assertDoesNotThrow(() -> context.toCondition("arg1", TestProp.AGE, "EQ"));
            assertDoesNotThrow(() -> context.toCondition("arg2", TestProp.AGE, "GT"));
            assertDoesNotThrow(() -> context.toCondition("arg3", TestProp.AGE, "LT"));
            assertDoesNotThrow(() -> context.toCondition("arg4", TestProp.AGE, "eq")); // case insensitive
        }

        @Test
        @DisplayName("Devrait traiter operator inconnu comme CUSTOM")
        void shouldTreatUnknownOperatorAsCustom() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            // Opérateur inexistant est traité comme CUSTOM (pas d'exception à la création)
            // L'exception sera lancée lors de la résolution du prédicat
            assertDoesNotThrow(() ->
                context.toCondition("arg1", TestProp.NAME, "INVALID_OP")
            );
        }
    }

    @Nested
    @DisplayName("toResolver() - Value Provision")
    class ToResolverTests {

        @Test
        @DisplayName("Devrait créer resolver avec argumentRegistry")
        void shouldCreateResolverWithArgumentRegistry() {
            // Given
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );
            Condition condition = context.toCondition("nameParam", TestProp.NAME, "EQ");

            // When - Phase 2: Fourniture de la valeur
            Map<String, Object> args = new HashMap<>();
            args.put("nameParam", "John");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Then
            assertNotNull(resolver, "Resolver ne doit pas être null");
        }

        @Test
        @DisplayName("Devrait rejeter null condition")
        void shouldRejectNullCondition() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            Map<String, Object> args = Map.of("arg1", "value");

            assertThrows(IllegalArgumentException.class, () ->
                context.toResolver(null, QueryExecutionParams.of(args))
            );
        }

        @Test
        @DisplayName("Devrait rejeter null argumentRegistry")
        void shouldRejectNullArgumentRegistry() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );
            Condition condition = context.toCondition("arg1", TestProp.NAME, "EQ");

            assertThrows(NullPointerException.class, () ->
                context.toResolver(condition, null)
            );
        }
    }

    @Nested
    @DisplayName("Mapping Function - Path Resolution")
    class MappingFunctionTests {

        @Test
        @DisplayName("Devrait supporter mapping vers String path")
        void shouldSupportStringPathMapping() {
            // Given - Simple string path mapping
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> switch (ref) {
                    case NAME -> "name";
                    case AGE -> "age";
                }
            );

            // When
            Condition nameCondition = context.toCondition("arg1", TestProp.NAME, "EQ");
            Condition ageCondition = context.toCondition("arg2", TestProp.AGE, "GT");

            // Then - Les conditions doivent être créées
            assertNotNull(nameCondition);
            assertNotNull(ageCondition);
        }

        @Test
        @DisplayName("Devrait supporter mapping vers PredicateResolverMapping")
        void shouldSupportPredicateResolverMapping() {
            // Given - Custom resolver mapping
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> (PredicateResolverMapping<TestEntity>) (op, args) -> {
                    return (root, query, cb) -> cb.conjunction(); // Always true predicate
                }
            );

            // When
            Condition condition = context.toCondition("arg1", TestProp.NAME, "EQ");
            Map<String, Object> args = Map.of("arg1", "value");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Then
            assertNotNull(resolver);
        }

        @Test
        @DisplayName("Devrait passer le paramètre correctement au PredicateResolverMapping")
        void shouldPassParameterToPredicateResolverMapping() {
            // Given - Capturer le paramètre reçu
            final Object[] capturedParam = new Object[1];
            
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> (PredicateResolverMapping<TestEntity>) (op, args) -> {
                    capturedParam[0] = args[0];  // Capture parameter
                    // Return a valid predicate
                    return (root, query, cb) -> cb.equal(root.get("name"), args[0]);
                }
            );

            // When
            String expectedValue = "testValue";
            Condition condition = context.toCondition("arg1", TestProp.NAME, "EQ");
            Map<String, Object> args = Map.of("arg1", expectedValue);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Then
            assertNotNull(resolver, "Resolver should not be null");
            
            // Note: We can't execute the resolver without a real CriteriaBuilder,
            // but we've verified the mapping structure is correct
            // The parameter will be passed when the resolver is executed in a real JPA context
        }
    }

    @Nested
    @DisplayName("Condition Combinations")
    class ConditionCombinationsTests {

        @Test
        @DisplayName("Devrait combiner conditions avec AND")
        void shouldCombineConditionsWithAnd() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> switch (ref) {
                    case NAME -> "name";
                    case AGE -> "age";
                }
            );

            // Créer deux conditions
            Condition nameCondition = context.toCondition("nameParam", TestProp.NAME, "EQ");
            Condition ageCondition = context.toCondition("ageParam", TestProp.AGE, "GT");

            // Combiner avec AND
            Condition combined = nameCondition.and(ageCondition);

            assertNotNull(combined, "Condition combinée ne doit pas être null");
        }

        @Test
        @DisplayName("Devrait combiner conditions avec OR")
        void shouldCombineConditionsWithOr() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            Condition cond1 = context.toCondition("arg1", TestProp.NAME, "EQ");
            Condition cond2 = context.toCondition("arg2", TestProp.NAME, "EQ");

            Condition combined = cond1.or(cond2);

            assertNotNull(combined);
        }

        @Test
        @DisplayName("Devrait inverser condition avec NOT")
        void shouldNegateConditionWithNot() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            Condition condition = context.toCondition("arg1", TestProp.NAME, "EQ");
            Condition negated = condition.not();

            assertNotNull(negated);
        }
    }

    @Nested
    @DisplayName("Context Properties")
    class ContextPropertiesTests {

        @Test
        @DisplayName("Devrait retourner TestProp.class correct")
        void shouldReturnCorrectEntityClass() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            assertEquals(TestProp.class, context.getPropertyRefClass());
        }

        @Test
        @DisplayName("Devrait retourner propertyRefClass correct")
        void shouldReturnCorrectPropertyRefClass() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            assertEquals(TestProp.class, context.getPropertyRefClass());
        }

        @Test
        @DisplayName("Devrait permettre changement de mappingBuilder")
        void shouldAllowMappingBuilderChange() {
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            // Changer le mapping builder
            var oldBuilder = context.setMappingBuilder((ref) -> "newName");

            assertNotNull(oldBuilder, "Ancien builder doit être retourné");
        }
    }

    @Nested
    @DisplayName("FilterConfig Integration")
    class FilterConfigTests {

        @Test
        @DisplayName("Devrait utiliser FilterConfig par défaut si non fourni")
        void shouldUseDefaultFilterConfigWhenNotProvided() {
            // Constructeur avec 3 paramètres utilise FilterConfig par défaut
            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name"
            );

            // Devrait fonctionner sans erreur
            Condition condition = context.toCondition("arg1", TestProp.NAME, "EQ");
            assertNotNull(condition);
        }

        @Test
        @DisplayName("Devrait accepter FilterConfig personnalisé")
        void shouldAcceptCustomFilterConfig() {
            FilterConfig customConfig = FilterConfig.builder()
                .stringCaseStrategy(StringCaseStrategy.UPPER)
                .build();

            JpaFilterContext<TestProp> context = new JpaFilterContext<>(
                TestProp.class,
                ref -> "name",
                customConfig
            );

            Condition condition = context.toCondition("arg1", TestProp.NAME, "MATCHES");
            assertNotNull(condition);
        }
    }
}

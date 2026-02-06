package io.github.cyfko.filterql.tests;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.config.NullValuePolicy;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.tests.entities.policies.Item;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour NullValuePolicy avec lazy validation.
 * Vérifie que les 3 politiques (STRICT_EXCEPTION, COERCE_TO_IS_NULL, IGNORE_FILTER)
 * fonctionnent correctement et que les exceptions surviennent au bon moment (resolver.resolve()).
 */
@DisplayName("JPA NullValuePolicy - Lazy Validation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JpaNullValuePolicyTest {

    private static EntityManagerFactory emf;

    enum ItemProp implements PropertyReference {
        NAME,
        QUANTITY;

        @Override
        public Class<?> getType() {
            return switch (this){
                case NAME -> String.class;
                case QUANTITY -> Integer.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return switch (this){
                case NAME -> Set.of(Op.EQ, Op.NE, Op.IS_NULL, Op.NOT_NULL);
                case QUANTITY -> Set.of(Op.EQ, Op.NE, Op.GT, Op.LT, Op.IS_NULL, Op.NOT_NULL);
            };
        }

        @Override
        public Class<?> getEntityType() {
            return Item.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");

        // Insert test data
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(new Item("Item1", 10));
        em.persist(new Item("Item2", 20));
        em.persist(new Item("Item3", null)); // Item with null quantity
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    private JpaFilterContext<ItemProp> createContext(FilterConfig config) {
        return new JpaFilterContext<>(
            ItemProp.class,
            ref -> switch (ref) {
                case NAME -> "name";
                case QUANTITY -> "quantity";
            },
            config
        );
    }

    private List<Item> executeQuery(PredicateResolver<?> resolver) {
        try (EntityManager em = emf.createEntityManager()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Item> query = cb.createQuery(Item.class);
            Root<Item> root = query.from(Item.class);

            //noinspection rawtypes,unchecked
            query.where(resolver.resolve((Root) root, query, cb));

            return em.createQuery(query).getResultList();
        }
    }

    @Nested
    @DisplayName("STRICT_EXCEPTION Policy (Default)")
    @Order(1)
    class StrictExceptionPolicy {

        @Test
        @DisplayName("Should throw FilterValidationException at resolver.resolve() for (EQ, null)")
        void shouldThrowAtResolveTimeForEqNull() {
            // Default policy is STRICT_EXCEPTION
            JpaFilterContext<ItemProp> context = createContext(FilterConfig.builder().build());

            // Phase 1: toCondition() should NOT throw (lazy validation)
            Condition condition = assertDoesNotThrow(() ->
                context.toCondition("nameParam", ItemProp.NAME, "EQ")
            );

            // Phase 2: toResolver() with null value should NOT throw yet
            Map<String, Object> args = new HashMap<>();
            args.put("nameParam", null);
            PredicateResolver<?> resolver = assertDoesNotThrow(() ->
                context.toResolver(condition, QueryExecutionParams.of(args))
            );

            // Phase 3: resolver.resolve() MUST throw (lazy validation triggers here)
            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<Item> query = cb.createQuery(Item.class);
                Root<Item> root = query.from(Item.class);

                //noinspection unchecked,rawtypes
                FilterValidationException exception = assertThrows(
                        FilterValidationException.class,
                        () -> resolver.resolve((Root) root, query, cb)
                );

                assertTrue(exception.getMessage().contains("Null value not allowed"));
                assertTrue(exception.getMessage().contains("STRICT_EXCEPTION"));
            }
        }

        @Test
        @DisplayName("Should throw FilterValidationException at resolver.resolve() for (NE, null)")
        void shouldThrowAtResolveTimeForNeNull() {
            JpaFilterContext<ItemProp> context = createContext(FilterConfig.builder().build());

            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "NE");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Exception occurs at resolve time
            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<Item> query = cb.createQuery(Item.class);
                Root<Item> root = query.from(Item.class);

                //noinspection unchecked,rawtypes
                assertThrows(FilterValidationException.class, () -> resolver.resolve((Root) root, query, cb));
            }
        }

        @Test
        @DisplayName("Should accept null for IS_NULL operator")
        void shouldAcceptNullForIsNullOperator() {
            JpaFilterContext<ItemProp> context = createContext(FilterConfig.builder().build());

            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "IS_NULL");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null); // IS_NULL accepts null
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Should execute without exception
            List<Item> results = assertDoesNotThrow(() -> executeQuery(resolver));

            assertEquals(1, results.size());
            assertEquals("Item3", results.getFirst().getName());
            assertNull(results.getFirst().getQuantity());
        }

        @Test
        @DisplayName("Should accept null for NOT_NULL operator")
        void shouldAcceptNullForNotNullOperator() {
            JpaFilterContext<ItemProp> context = createContext(FilterConfig.builder().build());

            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "NOT_NULL");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null); // NOT_NULL accepts null
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Item> results = executeQuery(resolver);

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(item -> item.getQuantity() != null));
        }
    }

    @Nested
    @DisplayName("COERCE_TO_IS_NULL Policy")
    @Order(2)
    class CoerceToIsNullPolicy {

        @Test
        @DisplayName("Should transform (EQ, null) to IS_NULL at execution time")
        void shouldCoerceEqNullToIsNull() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            // EQ with null should be transformed to IS_NULL
            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "EQ");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Should execute without exception and return items with null quantity
            List<Item> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals("Item3", results.getFirst().getName());
            assertNull(results.getFirst().getQuantity());
        }

        @Test
        @DisplayName("Should transform (NE, null) to NOT_NULL at execution time")
        void shouldCoerceNeNullToNotNull() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            // NE with null should be transformed to NOT_NULL
            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "NE");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Should return items with non-null quantity
            List<Item> results = executeQuery(resolver);

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(item -> item.getQuantity() != null));
        }

        @Test
        @DisplayName("Should throw at resolve time for unsupported operators like GT with null")
        void shouldThrowForUnsupportedOperatorWithNull() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            // GT cannot be coerced, should throw at resolve time
            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "GT");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<Item> query = cb.createQuery(Item.class);
                Root<Item> root = query.from(Item.class);

                //noinspection unchecked,rawtypes
                FilterValidationException exception = assertThrows(
                        FilterValidationException.class,
                        () -> resolver.resolve((Root) root, query, cb)
                );

                assertTrue(exception.getMessage().contains("Cannot coerce null value"));
                assertTrue(exception.getMessage().contains("COERCE_TO_IS_NULL"));
            }
        }

        @Test
        @DisplayName("Should work normally with non-null values")
        void shouldWorkWithNonNullValues() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "EQ");

            Map<String, Object> args = Map.of("qtyParam", 10);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Item> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals(10, results.getFirst().getQuantity());
        }
    }

    @Nested
    @DisplayName("IGNORE_FILTER Policy")
    @Order(3)
    class IgnoreFilterPolicy {

        @Test
        @DisplayName("Should create neutral (always-true) condition for (EQ, null)")
        void shouldCreateNeutralConditionForEqNull() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            // EQ with null should be ignored (neutral condition)
            Condition condition = context.toCondition("nameParam", ItemProp.NAME, "EQ");

            Map<String, Object> args = new HashMap<>();
            args.put("nameParam", null);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Should return ALL items (neutral condition = always true)
            List<Item> results = executeQuery(resolver);

            assertEquals(3, results.size()); // All 3 items
        }

        @Test
        @DisplayName("Should create neutral condition for (NE, null)")
        void shouldCreateNeutralConditionForNeNull() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "NE");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // Returns all items
            List<Item> results = executeQuery(resolver);

            assertEquals(3, results.size());
        }

        @Test
        @DisplayName("Should create neutral condition for any operator with null")
        void shouldCreateNeutralConditionForAnyOperator() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            // Even GT with null should be ignored
            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "GT");

            Map<String, Object> args = new HashMap<>();
            args.put("qtyParam", null);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            // No exception, returns all items
            List<Item> results = assertDoesNotThrow(() -> executeQuery(resolver));

            assertEquals(3, results.size());
        }

        @Test
        @DisplayName("Should work normally with non-null values")
        void shouldWorkWithNonNullValues() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            Condition condition = context.toCondition("qtyParam", ItemProp.QUANTITY, "GT");

            Map<String, Object> args = Map.of("qtyParam", 10);
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Item> results = executeQuery(resolver);

            assertEquals(1, results.size()); // Only Item2 with quantity 20
            assertEquals(20, results.getFirst().getQuantity());
        }

        @Test
        @DisplayName("Should allow combining IGNORE_FILTER condition with normal condition")
        void shouldCombineIgnoredWithNormalCondition() {
            FilterConfig config = FilterConfig.builder()
                .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
                .build();
            JpaFilterContext<ItemProp> context = createContext(config);

            // First condition: ignored (null)
            Condition ignoredCondition = context.toCondition("nameParam", ItemProp.NAME, "EQ");
            // Second condition: normal
            Condition normalCondition = context.toCondition("qtyParam", ItemProp.QUANTITY, "GT");

            Condition combined = ignoredCondition.and(normalCondition);

            Map<String, Object> args = new HashMap<>();
            args.put("nameParam", null); // Ignored
            args.put("qtyParam", 10);     // Active filter
            PredicateResolver<?> resolver = context.toResolver(combined, QueryExecutionParams.of(args));

            // Should only apply the quantity filter
            List<Item> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals(20, results.getFirst().getQuantity());
        }
    }

    @Nested
    @DisplayName("Lazy Validation Timing Verification")
    @Order(4)
    class LazyValidationTiming {

        @Test
        @DisplayName("toCondition() should NEVER throw regardless of future value")
        void toConditionShouldNeverThrow() {
            JpaFilterContext<ItemProp> context = createContext(FilterConfig.builder().build());

            // None of these should throw at toCondition() time
            assertDoesNotThrow(() -> context.toCondition("param1", ItemProp.NAME, "EQ"));
            assertDoesNotThrow(() -> context.toCondition("param2", ItemProp.QUANTITY, "GT"));
            assertDoesNotThrow(() -> context.toCondition("param3", ItemProp.NAME, "NE"));
        }

        @Test
        @DisplayName("toResolver() should NEVER throw even with null values")
        void toResolverShouldNeverThrow() {
            JpaFilterContext<ItemProp> context = createContext(FilterConfig.builder().build());

            Condition condition = context.toCondition("param", ItemProp.NAME, "EQ");

            Map<String, Object> args = new HashMap<>();
            args.put("param", null);

            // toResolver() should not throw, even with STRICT_EXCEPTION policy
            assertDoesNotThrow(() -> context.toResolver(condition, QueryExecutionParams.of(args)));
        }

        @Test
        @DisplayName("Only resolver.resolve() should throw for STRICT_EXCEPTION policy")
        void onlyResolveShouldThrow() {
            JpaFilterContext<ItemProp> context = createContext(FilterConfig.builder().build());

            // Phase 1: OK
            Condition condition = assertDoesNotThrow(() ->
                context.toCondition("param", ItemProp.NAME, "EQ")
            );

            // Phase 2: OK
            Map<String, Object> args = new HashMap<>();
            args.put("param", null);
            PredicateResolver<?> resolver = assertDoesNotThrow(() ->
                context.toResolver(condition, QueryExecutionParams.of(args))
            );

            // Phase 3: THROW
            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<Item> query = cb.createQuery(Item.class);
                Root<Item> root = query.from(Item.class);

                //noinspection unchecked,rawtypes
                assertThrows(FilterValidationException.class, () -> resolver.resolve((Root) root, query, cb));
            }
        }
    }
}

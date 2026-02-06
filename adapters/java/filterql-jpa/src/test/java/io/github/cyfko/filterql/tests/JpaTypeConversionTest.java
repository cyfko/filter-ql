package io.github.cyfko.filterql.tests;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests for automatic type conversion in JPA FilterQL.
 * Validates String→Integer, String→LocalDate, String→Boolean, String→Enum conversions.
 */
@DisplayName("JPA Type Conversion Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JpaTypeConversionTest {

    private static EntityManagerFactory emf;

    enum EmployeeProp implements PropertyReference {
        NAME(String.class),
        AGE(Integer.class),
        ACTIVE(Boolean.class),
        HIRE_DATE(LocalDate.class),
        STATUS(SimpleUser.Status.class);

        private final Class<?> type;
        private static final Set<Op> ops = Set.of(Op.values());

        EmployeeProp(Class<?> type) {
            this.type = type;
        }

        @Override
        public Class<?> getType() { return type; }

        @Override
        public Set<Op> getSupportedOperators() { return ops; }

        @Override
        public Class<?> getEntityType() {
            return SimpleUser.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");
        
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        
        // Create test data
        em.persist(new SimpleUser("Alice", 30, true, LocalDate.of(2020, 1, 15), SimpleUser.Status.ACTIVE));
        em.persist(new SimpleUser("Bob", 25, false, LocalDate.of(2021, 6, 10), SimpleUser.Status.INACTIVE));
        em.persist(new SimpleUser("Charlie", 35, true, LocalDate.of(2019, 3, 20), SimpleUser.Status.ACTIVE));
        em.persist(new SimpleUser("Diana", 28, true, LocalDate.of(2022, 9, 5), SimpleUser.Status.PENDING));
        
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) {
            emf.close();
        }
    }

    private JpaFilterContext<EmployeeProp> createContext() {
        return new JpaFilterContext<>(
            EmployeeProp.class,
            ref -> switch (ref) {
                case NAME -> "name";
                case AGE -> "age";
                case ACTIVE -> "active";
                case HIRE_DATE -> "hireDate";
                case STATUS -> "status";
            },
            FilterConfig.builder().build()
        );
    }

    private List<SimpleUser> executeQuery(PredicateResolver<?> resolver) {
        try (EntityManager em = emf.createEntityManager()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<SimpleUser> query = cb.createQuery(SimpleUser.class);
            Root<SimpleUser> root = query.from(SimpleUser.class);

            //noinspection rawtypes,unchecked
            query.where(resolver.resolve((Root) root, query, cb));

            return em.createQuery(query).getResultList();
        }
    }

    @Nested
    @DisplayName("String to Integer Conversion")
    @Order(1)
    class StringToIntegerConversion {

        @Test
        @DisplayName("Should accept String value for Integer property (EQ)")
        void shouldConvertStringToIntegerForEq() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("ageParam", EmployeeProp.AGE, "EQ");

            // Pass String "30" instead of Integer 30
            Map<String, Object> args = Map.of("ageParam", "30");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals("Alice", results.getFirst().getName());
            assertEquals(30, results.getFirst().getAge());
        }

        @Test
        @DisplayName("Should accept String value for Integer property (GT)")
        void shouldConvertStringToIntegerForGt() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("ageParam", EmployeeProp.AGE, "GT");

            Map<String, Object> args = Map.of("ageParam", "28");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(2, results.size()); // Alice (30), Charlie (35)
            assertTrue(results.stream().allMatch(e -> e.getAge() > 28));
        }

        @Test
        @DisplayName("Should accept String collection for Integer IN operator")
        void shouldConvertStringCollectionToIntegerForIn() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("ageParam", EmployeeProp.AGE, "IN");

            Map<String, Object> args = Map.of("ageParam", List.of("25", "30", "35"));
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(e -> 
                e.getAge() == 25 || e.getAge() == 30 || e.getAge() == 35
            ));
        }

        @Test
        @DisplayName("Should throw for invalid String to Integer conversion")
        void shouldThrowForInvalidStringToInteger() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("ageParam", EmployeeProp.AGE, "EQ");

            Map<String, Object> args = Map.of("ageParam", "invalid");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<SimpleUser> query = cb.createQuery(SimpleUser.class);
                Root<SimpleUser> root = query.from(SimpleUser.class);

                // Should throw IllegalArgumentException wrapping NumberFormatException at resolve time
                //noinspection rawtypes,unchecked
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolve((Root) root, query, cb)
                );
            }
        }
    }

    @Nested
    @DisplayName("String to LocalDate Conversion")
    @Order(2)
    class StringToLocalDateConversion {

        @Test
        @DisplayName("Should accept ISO String for LocalDate property")
        void shouldConvertIsoStringToLocalDate() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("hireDateParam", EmployeeProp.HIRE_DATE, "EQ");

            // ISO 8601 format
            Map<String, Object> args = Map.of("hireDateParam", "2020-01-15");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals("Alice", results.getFirst().getName());
            assertEquals(LocalDate.of(2020, 1, 15), results.getFirst().getHireDate());
        }

        @Test
        @DisplayName("Should accept String for LocalDate GT comparison")
        void shouldConvertStringToLocalDateForGt() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("hireDateParam", EmployeeProp.HIRE_DATE, "GT");

            Map<String, Object> args = Map.of("hireDateParam", "2021-01-01");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(2, results.size()); // Bob (2021-06-10), Diana (2022-09-05)
            assertTrue(results.stream().allMatch(e -> 
                e.getHireDate().isAfter(LocalDate.of(2021, 1, 1))
            ));
        }

        @Test
        @DisplayName("Should accept String collection for LocalDate IN operator")
        void shouldConvertStringCollectionToLocalDateForIn() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("hireDateParam", EmployeeProp.HIRE_DATE, "IN");

            Map<String, Object> args = Map.of("hireDateParam", 
                List.of("2020-01-15", "2021-06-10")
            );
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(2, results.size()); // Alice, Bob
        }

        @Test
        @DisplayName("Should throw for invalid String to LocalDate conversion")
        void shouldThrowForInvalidStringToLocalDate() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("hireDateParam", EmployeeProp.HIRE_DATE, "EQ");

            Map<String, Object> args = Map.of("hireDateParam", "invalid-date");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<SimpleUser> query = cb.createQuery(SimpleUser.class);
                Root<SimpleUser> root = query.from(SimpleUser.class);

                // Should throw DateTimeParseException at resolve time
                //noinspection rawtypes,unchecked
                assertThrows(
                        Exception.class,
                        () -> resolver.resolve((Root) root, query, cb)
                );
            }
        }
    }

    @Nested
    @DisplayName("String to Boolean Conversion")
    @Order(3)
    class StringToBooleanConversion {

        @Test
        @DisplayName("Should accept String 'true' for Boolean property")
        void shouldConvertStringTrueToBoolean() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("activeParam", EmployeeProp.ACTIVE, "EQ");

            Map<String, Object> args = Map.of("activeParam", "true");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(3, results.size()); // Alice, Charlie, Diana
            assertTrue(results.stream().allMatch(SimpleUser::getActive));
        }

        @Test
        @DisplayName("Should accept String 'false' for Boolean property")
        void shouldConvertStringFalseToBoolean() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("activeParam", EmployeeProp.ACTIVE, "EQ");

            Map<String, Object> args = Map.of("activeParam", "false");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(1, results.size()); // Bob
            assertFalse(results.getFirst().getActive());
        }

        @Test
        @DisplayName("Should accept String collection for Boolean IN operator")
        void shouldConvertStringCollectionToBooleanForIn() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("activeParam", EmployeeProp.ACTIVE, "IN");

            Map<String, Object> args = Map.of("activeParam", List.of("true"));
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(SimpleUser::getActive));
        }
    }

    @Nested
    @DisplayName("String to Enum Conversion")
    @Order(4)
    class StringToEnumConversion {

        @Test
        @DisplayName("Should accept String for Enum property (already tested in EnumMatchMode)")
        void shouldConvertStringToEnum() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("statusParam", EmployeeProp.STATUS, "EQ");

            Map<String, Object> args = Map.of("statusParam", "ACTIVE");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(2, results.size()); // Alice, Charlie
            assertTrue(results.stream().allMatch(e -> e.getStatus() == SimpleUser.Status.ACTIVE));
        }

        @Test
        @DisplayName("Should accept String collection for Enum IN operator")
        void shouldConvertStringCollectionToEnumForIn() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("statusParam", EmployeeProp.STATUS, "IN");

            Map<String, Object> args = Map.of("statusParam", 
                List.of("ACTIVE", "PENDING")
            );
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(3, results.size()); // Alice, Charlie, Diana
        }
    }

    @Nested
    @DisplayName("Mixed Type Collections")
    @Order(5)
    class MixedTypeCollections {

        @Test
        @DisplayName("Should handle mixed Integer and String in collection")
        void shouldHandleMixedIntegerStringCollection() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("ageParam", EmployeeProp.AGE, "IN");

            // Mix of Integer and String
            Map<String, Object> args = Map.of("ageParam", List.of(25, "30", 35));
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(e -> 
                e.getAge() == 25 || e.getAge() == 30 || e.getAge() == 35
            ));
        }
    }

    @Nested
    @DisplayName("Range Type Conversion")
    @Order(6)
    class RangeTypeConversion {

        @Test
        @DisplayName("Should convert String range to Integer range")
        void shouldConvertStringRangeToInteger() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("ageParam", EmployeeProp.AGE, "RANGE");

            Map<String, Object> args = Map.of("ageParam", List.of("25", "30"));
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            // RANGE is inclusive: 25 <= age <= 30
            assertEquals(3, results.size()); // Bob (25), Diana (28), Alice (30)
            assertTrue(results.stream().allMatch(e -> e.getAge() >= 25 && e.getAge() <= 30));
        }

        @Test
        @DisplayName("Should convert String range to LocalDate range")
        void shouldConvertStringRangeToLocalDate() {
            JpaFilterContext<EmployeeProp> context = createContext();

            Condition condition = context.toCondition("hireDateParam", EmployeeProp.HIRE_DATE, "RANGE");

            Map<String, Object> args = Map.of("hireDateParam", 
                List.of("2020-01-01", "2021-12-31")
            );
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<SimpleUser> results = executeQuery(resolver);

            assertEquals(2, results.size()); // Alice, Bob
        }
    }
}

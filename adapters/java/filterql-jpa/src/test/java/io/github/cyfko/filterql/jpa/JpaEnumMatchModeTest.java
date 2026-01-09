package io.github.cyfko.filterql.jpa;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.config.EnumMatchMode;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.entities.policies.Task.Priority;
import io.github.cyfko.filterql.jpa.entities.policies.Task;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EnumMatchMode configuration in JPA FilterQL.
 * Validates CASE_SENSITIVE vs CASE_INSENSITIVE enum matching.
 */
@DisplayName("JPA EnumMatchMode Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JpaEnumMatchModeTest {

    private static EntityManagerFactory emf;

    enum TaskProp implements PropertyReference {
        NAME,
        PRIORITY;

        @Override
        public Class<?> getType() {
            return switch (this){
                case NAME -> String.class;
                case PRIORITY -> Priority.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return Set.of(Op.values());
        }

        @Override
        public Class<?> getEntityType() {
            return Task.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");
        
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        
        // Create test data
        em.persist(new Task("Fix critical bug", Priority.CRITICAL));
        em.persist(new Task("Implement new feature", Priority.HIGH));
        em.persist(new Task("Update documentation", Priority.MEDIUM));
        em.persist(new Task("Code review", Priority.MEDIUM));
        em.persist(new Task("Refactor old code", Priority.LOW));
        
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) {
            emf.close();
        }
    }

    private JpaFilterContext<TaskProp> createContext(FilterConfig config) {
        return new JpaFilterContext<>(
            TaskProp.class,
            ref -> switch (ref) {
                case NAME -> "name";
                case PRIORITY -> "priority";
            },
            config
        );
    }

    private List<Task> executeQuery(PredicateResolver<?> resolver) {
        try (EntityManager em = emf.createEntityManager()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Task> query = cb.createQuery(Task.class);
            Root<Task> root = query.from(Task.class);

            //noinspection rawtypes,unchecked
            query.where(resolver.resolve((Root) root, query, cb));

            return em.createQuery(query).getResultList();
        }
    }

    @Nested
    @DisplayName("CASE_SENSITIVE Mode")
    @Order(1)
    class CaseSensitiveMode {

        @Test
        @DisplayName("Should match exact enum constant name")
        void shouldMatchExactEnumName() {
            FilterConfig config = FilterConfig.builder()
                .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
                .build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "EQ");

            Map<String, Object> args = Map.of("priorityParam", "HIGH");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Task> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals(Priority.HIGH, results.getFirst().getPriority());
            assertEquals("Implement new feature", results.getFirst().getName());
        }

        @Test
        @DisplayName("Should throw exception for lowercase enum name")
        void shouldRejectLowercaseEnumName() {
            FilterConfig config = FilterConfig.builder()
                .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
                .build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "EQ");

            Map<String, Object> args = Map.of("priorityParam", "high");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<Task> query = cb.createQuery(Task.class);
                Root<Task> root = query.from(Task.class);

                //noinspection unchecked,rawtypes
                IllegalArgumentException exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolve((Root) root, query, cb)
                );

                assertTrue(exception.getMessage().contains("Invalid value 'high'"));
                assertTrue(exception.getMessage().contains("Task$Priority"));
            }
        }

        @Test
        @DisplayName("Should work with IN operator and multiple enum values")
        void shouldWorkWithInOperatorCaseSensitive() {
            FilterConfig config = FilterConfig.builder()
                .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
                .build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "IN");

            Map<String, Object> args = Map.of("priorityParam", List.of("HIGH", "CRITICAL"));
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Task> results = executeQuery(resolver);

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(t -> 
                t.getPriority() == Priority.HIGH || t.getPriority() == Priority.CRITICAL
            ));
        }
    }

    @Nested
    @DisplayName("CASE_INSENSITIVE Mode")
    @Order(2)
    class CaseInsensitiveMode {

        @Test
        @DisplayName("Should match lowercase enum name")
        void shouldMatchLowercaseEnumName() {
            FilterConfig config = FilterConfig.builder()
                .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE)
                .build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "EQ");

            Map<String, Object> args = Map.of("priorityParam", "high");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Task> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals(Priority.HIGH, results.getFirst().getPriority());
        }

        @Test
        @DisplayName("Should match mixed case enum name")
        void shouldMatchMixedCaseEnumName() {
            FilterConfig config = FilterConfig.builder()
                .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE)
                .build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "EQ");

            Map<String, Object> args = Map.of("priorityParam", "CrItIcAl");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Task> results = executeQuery(resolver);

            assertEquals(1, results.size());
            assertEquals(Priority.CRITICAL, results.getFirst().getPriority());
        }

        @Test
        @DisplayName("Should work with IN operator and mixed case values")
        void shouldWorkWithInOperatorCaseInsensitive() {
            FilterConfig config = FilterConfig.builder()
                .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE)
                .build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "IN");

            Map<String, Object> args = Map.of("priorityParam", List.of("low", "Medium", "HIGH"));
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Task> results = executeQuery(resolver);

            assertEquals(4, results.size()); // LOW (1) + MEDIUM (2) + HIGH (1)
            assertTrue(results.stream().allMatch(t -> 
                t.getPriority() == Priority.LOW ||
                t.getPriority() == Priority.MEDIUM ||
                t.getPriority() == Priority.HIGH
            ));
        }

        @Test
        @DisplayName("Should still throw for invalid enum name")
        void shouldRejectInvalidEnumName() {
            FilterConfig config = FilterConfig.builder()
                .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE)
                .build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "EQ");

            Map<String, Object> args = Map.of("priorityParam", "INVALID");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            try (EntityManager em = emf.createEntityManager()) {
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<Task> query = cb.createQuery(Task.class);
                Root<Task> root = query.from(Task.class);

                //noinspection unchecked,rawtypes
                IllegalArgumentException exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolve((Root) root, query, cb)
                );

                assertTrue(exception.getMessage().contains("Invalid value 'INVALID'"));
                assertTrue(exception.getMessage().contains("Task$Priority"));
            }
        }
    }

    @Nested
    @DisplayName("Default Behavior")
    @Order(3)
    class DefaultBehavior {

        @Test
        @DisplayName("Default should be CASE_INSENSITIVE")
        void defaultShouldBeCaseInsensitive() {
            FilterConfig config = FilterConfig.builder().build();
            JpaFilterContext<TaskProp> context = createContext(config);

            Condition condition = context.toCondition("priorityParam", TaskProp.PRIORITY, "EQ");

            Map<String, Object> args = Map.of("priorityParam", "medium");
            PredicateResolver<?> resolver = context.toResolver(condition, QueryExecutionParams.of(args));

            List<Task> results = executeQuery(resolver);

            // Default is CASE_INSENSITIVE, should match MEDIUM
            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(t -> t.getPriority() == Priority.MEDIUM));
        }
    }
}

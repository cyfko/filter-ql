package io.github.cyfko.filterql.jpa.projection;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.utils.OperatorUtils;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._1.AddressB;
import io.github.cyfko.filterql.jpa.entities.projection._1.City;
import io.github.cyfko.filterql.jpa.entities.projection._1.UserB;
import io.github.cyfko.filterql.jpa.strategies.MultiQueryFetchStrategy;
import io.github.cyfko.filterql.jpa.strategies.helper.RowBuffer;
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
 * Integration tests for JPA projection functionality (Phase 1).
 * <p>
 * Tests the complete projection pipeline:
 * </p>
 * <ol>
 *   <li>{@link JpaFilterContext} for applying projections to queries</li>
 * </ol>
 *
 * @author Frank KOSSI
 */
@DisplayName("JPA Projection Integration Tests (Phase 1)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JpaProjectionIntegrationTest {

    private static EntityManagerFactory emf;
    private static JpaFilterContext<TestUserProperty> context;

    /**
     * Property reference enum for User.
     */
    enum TestUserProperty implements PropertyReference {
        NAME,
        EMAIL,
        ACTIVE,
        PHONE,
        CITY_NAME,
        ZIP_CODE;


        @Override
        public Class<?> getType() {
            return switch (this){
                case NAME -> String.class;
                case EMAIL -> String.class;
                case ACTIVE -> Boolean.class;
                case PHONE -> String.class;
                case CITY_NAME -> String.class;
                case ZIP_CODE -> String.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return switch (this){
                case NAME -> OperatorUtils.FOR_TEXT;
                case EMAIL -> OperatorUtils.FOR_TEXT;
                case ACTIVE -> Set.of(Op.EQ);
                case PHONE -> OperatorUtils.FOR_TEXT;
                case CITY_NAME -> OperatorUtils.FOR_TEXT;
                case ZIP_CODE -> OperatorUtils.FOR_TEXT;
            };
        }

        @Override
        public Class<?> getEntityType() {
            return UserB.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");

        context = new JpaFilterContext<>(
            TestUserProperty.class,
            prop -> switch (prop) {
                case NAME -> "name";
                case EMAIL -> "email";
                case ACTIVE -> "active";
                case PHONE -> "phone";
                case CITY_NAME -> "address.city.name";
                case ZIP_CODE -> "address.city.zipCode";
            },
            FilterConfig.builder().build()
        );

        // Insert test data
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        City paris = new City("Paris", "75001");
        City lyon = new City("Lyon", "69001");

        AddressB addr1 = new AddressB(paris);
        AddressB addr2 = new AddressB(lyon);

        em.persist(new UserB("John Doe", "john@example.com", true, "+1-555-1234", addr1));
        em.persist(new UserB("Jane Smith", "jane@example.com", true, "+1-555-5678", addr2));
        em.persist(new UserB("Bob Johnson", "bob@example.com", false, "+1-555-9999", addr1));

        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    @Nested
    @DisplayName("JpaFilterContext Projection Integration Tests")
    class JpaFilterContextProjectionTests {

        @Test
        @Order(10)
        @DisplayName("Should support entity query without projection")
        void shouldSupportEntityQueryWithoutProjection() {
            try (EntityManager em = emf.createEntityManager()) {
                // Given: Condition for filtering
                Condition condition = context.toCondition("activeArg", TestUserProperty.ACTIVE, "EQ");

                // When: Resolve WITHOUT projection
                QueryExecutionParams params = QueryExecutionParams.of(Map.of("activeArg", true));
                PredicateResolver<?> resolver = context.toResolver(condition, params);

                // Then: Should execute successfully
                CriteriaBuilder cb = em.getCriteriaBuilder();
                CriteriaQuery<UserB> query = cb.createQuery(UserB.class);
                Root<UserB> root = query.from(UserB.class);

                //noinspection rawtypes,unchecked
                query.where(resolver.resolve((Root) root, query, cb));

                List<UserB> results = em.createQuery(query).getResultList();
                assertNotNull(results);
                assertFalse(results.isEmpty());
            }
        }

        @Test
        @Order(11)
        @DisplayName("Should project the two ACTIVE persons")
        void shouldProjectTwoPersonActive() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<TestUserProperty> request = FilterRequest.<TestUserProperty>builder()
                        .filter("activeArg", TestUserProperty.ACTIVE, "EQ", true)
                        .combineWith("and")
                        .projection("name", "email")
                        .build();


                // Then: Should execute successfully
                MultiQueryFetchStrategy fetchStrategy = new MultiQueryFetchStrategy(UserB.class);
                List<RowBuffer> result = FilterQueryFactory.of(context).execute(request, em, fetchStrategy);

                // Verify tuple has expected fields
                assertEquals(2, result.size());

                RowBuffer firstResult = result.getFirst();
                RowBuffer secondResult = result.get(1);
                assertEquals("John Doe", firstResult.get("name"));
                assertEquals("john@example.com", firstResult.get("email"));
                assertEquals("Jane Smith", secondResult.get("name"));
                assertEquals("jane@example.com", secondResult.get("email"));
                assertEquals(2, firstResult.fields());
                assertEquals(2, secondResult.fields());
            }
        }

        @Test
        @Order(12)
        @DisplayName("Should project the single not ACTIVE person")
        void shouldProjectSinglePersonNotActive() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<TestUserProperty> request = FilterRequest.<TestUserProperty>builder()
                        .filter("activeArg", TestUserProperty.ACTIVE, "EQ", false)
                        .combineWith("and")
                        .projection("name", "email")
                        .build();

                // Then: Should execute successfully
                MultiQueryFetchStrategy fetchStrategy = new MultiQueryFetchStrategy(UserB.class);
                List<RowBuffer> result = FilterQueryFactory.of(context).execute(request, em, fetchStrategy);

                // Verify tuple has expected fields
                assertEquals(1, result.size());

                RowBuffer firstResult = result.getFirst();
                assertEquals("Bob Johnson", firstResult.get("name"));
                assertEquals("bob@example.com", firstResult.get("email"));
                assertEquals(2, firstResult.fields());
            }
        }

        @Disabled
        @Test
        @Order(13)
        @DisplayName("Should reject projection without DtoFieldMapper")
        void shouldRejectProjectionWithoutDtoFieldMapper() {
            // Given: Context WITHOUT projection support
            Condition condition = context.toCondition("activeArg", TestUserProperty.ACTIVE, "EQ");
            Set<String> projection = Set.of("name", "email");
            QueryExecutionParams params = QueryExecutionParams.withProjection(Map.of("activeArg", true), projection);

            // When & Then: Should throw IllegalStateException
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> context.toResolver(condition, params));

            assertTrue(ex.getMessage().contains("DtoFieldMapper is null"));
        }
    }
}

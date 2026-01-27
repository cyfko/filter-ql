package io.github.cyfko.filterql.jpa.projection;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.spi.FilterQuery;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._2.OrderC;
import io.github.cyfko.filterql.jpa.entities.projection._2.UserC;
import io.github.cyfko.filterql.core.spi.ExecutionStrategy;
import io.github.cyfko.filterql.jpa.strategies.FullEntityFetchStrategy;
import io.github.cyfko.filterql.jpa.strategies.MultiQueryFetchStrategy;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FilterRequest with projection functionality using ExecutionStrategy.
 */
@DisplayName("FilterRequest with Projection Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FilterRequestProjectionIntegrationTest {

    private static EntityManagerFactory emf;
    private static FilterQuery<UserC> filterQuery;

    enum UserProperty implements PropertyReference {
        NAME,
        EMAIL,
        ORDER_NUMBER,
        ORDER_AMOUNT;

        public Class<?> getType() {
            return switch (this){
                case NAME -> String.class;
                case EMAIL -> String.class;
                case ORDER_NUMBER -> String.class;
                case ORDER_AMOUNT -> Double.class;
            };
        }

        public Set<Op> getSupportedOperators() {
            return switch (this){
                case NAME -> Set.of(Op.EQ, Op.MATCHES);
                case EMAIL -> Set.of(Op.EQ, Op.MATCHES);
                case ORDER_NUMBER -> Set.of(Op.EQ, Op.MATCHES);
                case ORDER_AMOUNT -> Set.of(Op.GT, Op.LT);
            };
        }

        @Override
        public Class<?> getEntityType() {
            return UserC.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");

        JpaFilterContext<UserProperty> filterContext = new JpaFilterContext<>(
                UserProperty.class,
                prop -> switch (prop) {
                    case NAME -> "name";
                    case EMAIL -> "email";
                    case ORDER_NUMBER -> "orders.orderNumber";
                    case ORDER_AMOUNT -> "orders.amount";
                },
                FilterConfig.builder().build()
        );

        filterQuery = FilterQueryFactory.of(filterContext);

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        em.createQuery("DELETE FROM OrderC").executeUpdate();
        em.createQuery("DELETE FROM UserC").executeUpdate();

        UserC john = new UserC("John Doe", "john@example.com");
        john.addOrder(new OrderC("ORD-001", 100.0));
        john.addOrder(new OrderC("ORD-002", 200.0));

        UserC jane = new UserC("Jane Smith", "jane@example.com");
        jane.addOrder(new OrderC("ORD-003", 150.0));

        UserC bob = new UserC("Bob Johnson", "bob@example.com");

        em.persist(john);
        em.persist(jane);
        em.persist(bob);

        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    @Test
    @Order(0)
    @DisplayName("FilterRequest: Should execute query with simple projection (no collections)")
    void shouldExecuteFilterRequestWithSimpleProjection() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("nameFilter", UserProperty.NAME, "LIKE", "John%")
                    .combineWith("nameFilter")
                    .projection(Set.of("id", "name", "email"))
                    .build();

            var strategy = new MultiQueryFetchStrategy(UserC.class);
            List<RowBuffer> results = filterQuery.execute(request, em, strategy);

            assertEquals(1, results.size());
            RowBuffer john = results.getFirst();
            assertEquals("John Doe", john.get("name"));
            assertNotNull(john.get("id"));
            assertEquals("john@example.com", john.get("email"));
        }
    }

    @Test
    @Order(1)
    @DisplayName("FilterRequest: Should execute query with null projection equivalent to project all scalar fields")
    void shouldExecuteFilterRequestWithNullProjection() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("nameFilter", UserProperty.NAME, "LIKE", "John%")
                    .combineWith("nameFilter")
                    .build();

            var strategy = new MultiQueryFetchStrategy(UserC.class);
            List<RowBuffer> results = filterQuery.execute(request, em, strategy);

            assertEquals(1, results.size());
            RowBuffer john = results.getFirst();
            assertNotNull(john.get("id"));
            assertNull(john.get("orders"));
            assertEquals("John Doe", john.get("name"));
            assertEquals("john@example.com", john.get("email"));
        }
    }

    @Test
    @Order(2)
    @DisplayName("FilterRequest: Should execute query with collection projection")
    void shouldExecuteFilterRequestWithCollectionProjection() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("nameFilter", UserProperty.NAME, "LIKE", "John%")
                    .combineWith("nameFilter")
                    .projection(Set.of("id", "name", "orders.orderNumber"))
                    .build();

            var strategy = new MultiQueryFetchStrategy(UserC.class);
            List<RowBuffer> results = filterQuery.execute(request, em, strategy);

            assertEquals(1, results.size());
            RowBuffer john = results.getFirst();
            @SuppressWarnings("unchecked")
            List<RowBuffer> orders = (List<RowBuffer>) john.get("orders");
            assertEquals(2, orders.size());
            Set<String> orderNumbers = orders.stream().map(o -> (String) o.get("orderNumber")).collect(Collectors.toSet());
            assertTrue(orderNumbers.contains("ORD-001"));
            assertTrue(orderNumbers.contains("ORD-002"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("FilterRequest: Should handle multiple filters with projection")
    void shouldHandleMultipleFiltersWithProjection() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("nameFilter", UserProperty.NAME, "LIKE", "J%")
                    .filter("emailFilter", UserProperty.EMAIL, "LIKE", "%@example.com")
                    .combineWith("nameFilter & emailFilter")
                    .projection(Set.of("id", "name", "email"))
                    .build();

            ExecutionStrategy<List<RowBuffer>> strategy = new MultiQueryFetchStrategy(UserC.class);
            List<RowBuffer> results = filterQuery.execute(request, em, strategy);

            assertEquals(2, results.size());
            Set<String> names = results.stream().map(r -> (String) r.get("name")).collect(Collectors.toSet());
            assertTrue(names.contains("John Doe"));
            assertTrue(names.contains("Jane Smith"));
        }
    }

    @Test
    @Order(4)
    @DisplayName("FilterRequest: Should filter on collection field with projection")
    void shouldFilterOnCollectionFieldWithProjection() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("orderAmountFilter", UserProperty.ORDER_AMOUNT, "GT", 150.0)
                    .combineWith("orderAmountFilter")
                    .projection(Set.of("id", "name", "orders.orderNumber", "orders.amount"))
                    .build();

            var strategy = new MultiQueryFetchStrategy(UserC.class);
            List<RowBuffer> results = filterQuery.execute(request, em, strategy);

            assertFalse(results.isEmpty());
            RowBuffer john = results.getFirst();
            @SuppressWarnings("unchecked")
            List<RowBuffer> orders = (List<RowBuffer>) john.get("orders");
            assertEquals(2, orders.size());
            assertEquals(100.0, (Double) orders.get(0).get("amount"));
            assertEquals(200.0, (Double) orders.get(1).get("amount"));
        }
    }

    @Test
    @Order(5)
    @DisplayName("FilterRequest: Should handle empty projection (full entity query)")
    void shouldHandleEmptyProjection() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("nameFilter", UserProperty.NAME, "EQ", "Bob Johnson")
                    .combineWith("nameFilter")
                    .build();

            var strategy = new FullEntityFetchStrategy<>(UserC.class); // full entity
            List<UserC> results = filterQuery.execute(request, em, strategy);

            assertEquals(1, results.size());
            UserC bob = results.getFirst();
            assertEquals("Bob Johnson", bob.getName());
            assertEquals("bob@example.com", bob.getEmail());
        }
    }
}

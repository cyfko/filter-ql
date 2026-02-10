package io.github.cyfko.filterql.tests.strategy;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.spi.FilterQuery;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.strategies.FullEntityFetchStrategy;
import io.github.cyfko.filterql.tests.entities.projection._1.AddressB;
import io.github.cyfko.filterql.tests.entities.projection._1.City;
import io.github.cyfko.filterql.tests.entities.projection._1.UserB;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FullEntityFetchStrategy Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullEntityFetchStrategyIntegrationTest {

    private static EntityManagerFactory emf;
    private static FilterQuery<EntityManager> filterQuery;

    enum UserProperty implements PropertyReference {
        NAME, EMAIL;

        @Override
        public Class<?> getType() {
            return String.class;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return Set.of(Op.EQ, Op.MATCHES);
        }

        @Override
        public Class<?> getEntityType() {
            return UserB.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");

        JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
                UserProperty.class,
                prop -> switch (prop) {
                    case NAME -> "name";
                    case EMAIL -> "email";
                },
                FilterConfig.builder().build());

        filterQuery = FilterQueryFactory.of(context);

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        // Create 10 users for pagination testing
        City city = new City("City", "00000");
        AddressB addr = new AddressB(city);

        for (int i = 0; i < 10; i++) {
            // Names: User 0, User 1, ... User 9
            // Use padding for correct sorting: User 00, User 01, ...
            String name = String.format("User %02d", i);
            em.persist(new UserB(name, "user" + i + "@example.com", true, "123", addr));
        }

        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null)
            emf.close();
    }

    @Test
    @Order(1)
    @DisplayName("Should paginate entities correctly")
    void shouldPaginateEntities() {
        try (EntityManager em = emf.createEntityManager()) {
            // Page 1: 0-4
            FilterRequest<UserProperty> requestPage1 = FilterRequest.<UserProperty>builder()
                    .pagination(0, 5, "name", "ASC")
                    .build();

            List<UserB> page1 = filterQuery.execute(requestPage1, em, new FullEntityFetchStrategy<>(UserB.class));
            assertEquals(5, page1.size());
            assertEquals("User 00", page1.get(0).getName());
            assertEquals("User 04", page1.get(4).getName());

            // Page 2: 5-9
            FilterRequest<UserProperty> requestPage2 = FilterRequest.<UserProperty>builder()
                    .pagination(1, 5, "name", "ASC")
                    .build();

            List<UserB> page2 = filterQuery.execute(requestPage2, em, new FullEntityFetchStrategy<>(UserB.class));
            assertEquals(5, page2.size());
            assertEquals("User 05", page2.get(0).getName());
            assertEquals("User 09", page2.get(4).getName());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Should sort entities descending")
    void shouldSortEntitiesDesc() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .pagination(0, 10, "name", "DESC")
                    .build();

            List<UserB> results = filterQuery.execute(request, em, new FullEntityFetchStrategy<>(UserB.class));
            assertEquals(10, results.size());
            assertEquals("User 09", results.get(0).getName());
            assertEquals("User 00", results.get(9).getName());
        }
    }

    @Test
    @Order(3)
    @DisplayName("Should apply filters with pagination")
    void shouldApplyFiltersWithPagination() {
        try (EntityManager em = emf.createEntityManager()) {

            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("f1", UserProperty.NAME, Op.MATCHES, "%User 0%") // Matches 00-09
                    .combineWith("f1")
                    .pagination(0, 3, "name", "ASC")
                    .build();

            List<UserB> results = filterQuery.execute(request, em, new FullEntityFetchStrategy<>(UserB.class));
            assertEquals(3, results.size());
            assertEquals("User 00", results.get(0).getName());
            assertEquals("User 02", results.get(2).getName());
        }
    }
}

package io.github.cyfko.filterql.tests.projection;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.utils.OperatorUtils;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.tests.entities.projection._3.DtoUserD;
import io.github.cyfko.filterql.tests.entities.projection._3.UserD;
import io.github.cyfko.filterql.jpa.strategies.MultiQueryFetchStrategy;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.filterql.jpa.strategies.helper.RowBuffer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JPA computed field projection tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComputedFieldProjectionTest {
    private static EntityManagerFactory emf;
    private static JpaFilterContext<UserProperty> filterContext;

    /**
     * Property reference enum for UserD with collection paths.
     */
    enum UserProperty implements PropertyReference {
        NAME,
        EMAIL,
        ORDER_NUMBER,
        ITEM_NAME;

        @Override
        public Class<?> getType() {
            return switch (this){
                case NAME, EMAIL, ORDER_NUMBER, ITEM_NAME -> String.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return switch (this){
                case NAME, EMAIL, ORDER_NUMBER, ITEM_NAME -> OperatorUtils.FOR_TEXT;
            };
        }

        @Override
        public Class<?> getEntityType() {
            return UserD.class;
        }
    }

    static void cleanDatabase(EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        // Delete child tables first (due to FK constraints)
        em.createQuery("DELETE FROM OrderItemD").executeUpdate();
        em.createQuery("DELETE FROM OrderD").executeUpdate();
        em.createQuery("DELETE FROM UserD").executeUpdate();

        em.getTransaction().commit();
        em.close();
    }


    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");

        filterContext = new JpaFilterContext<>(
                UserProperty.class,
                prop -> switch (prop) {
                    case NAME -> "name";
                    case EMAIL -> "email";
                    case ORDER_NUMBER -> "orders.orderNumber";
                    case ITEM_NAME -> "orders.items.productName";
                },
                FilterConfig.builder().build()
        );

        // clean database
        cleanDatabase(emf);

        // Insert test data with collections
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        // User : John with 2 orders (3 items total)
        UserD john = new UserD("John Doe", "john@example.com");

        em.persist(john);
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) emf.close();
    }

    @Test
    @Order(1)
    @DisplayName("Should succeed when projection hold a non-nested computed field")
    void shouldSucceedWhenProjectingSimpleComputedField() {
        try (EntityManager em = emf.createEntityManager()) {

            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("f", UserProperty.NAME, "LIKE", "%")
                    .combineWith("f")
                    .projection("keyIdentifier")
                    .build();

            MultiQueryFetchStrategy fetchStrategy = new MultiQueryFetchStrategy(DtoUserD.class, InstanceResolver.noBean());
            List<RowBuffer> result = FilterQueryFactory.of(filterContext).execute(request, em, fetchStrategy);
            RowBuffer first = result.getFirst();
            assertNotNull(first);
            Object keyIdentifier = first.get("keyIdentifier");
            assertEquals("1-John Doe", keyIdentifier);
            assertEquals(1, first.fields());
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should succeed when projection hold a nested computed field")
    void shouldSucceedWhenProjectingNestedComputedField() {
        try (EntityManager em = emf.createEntityManager()) {

            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .filter("f", UserProperty.NAME, "LIKE", "%")
                    .combineWith("f")
                    .projection("lastHistory")
                    .build();

            MultiQueryFetchStrategy fetchStrategy = new MultiQueryFetchStrategy(DtoUserD.class, InstanceResolver.noBean());
            List<RowBuffer> result = FilterQueryFactory.of(filterContext).execute(request, em, fetchStrategy);
            RowBuffer first = result.getFirst();
            assertNotNull(first);
            assertTrue(first.contains("lastHistory"));
            assertEquals(1,first.fields());

        }
    }
}

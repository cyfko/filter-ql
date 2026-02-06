package io.github.cyfko.filterql.tests.strategies;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.utils.OperatorUtils;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.strategies.MultiQueryFetchStrategy;
import io.github.cyfko.filterql.tests.entities.projection._3.OrderD;
import io.github.cyfko.filterql.tests.entities.projection._3.OrderItemD;
import io.github.cyfko.filterql.tests.entities.projection._3.UserD;
import io.github.cyfko.filterql.jpa.strategies.helper.RowBuffer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended benchmark test for MultiQueryFetchStrategy V2:
 * - Large data volumes (500+ users, 2500+ orders, 5000+ items)
 * - Collection projections (User → Orders → Items)
 * - Nested collection traversal
 * - Data correctness verification
 *
 * <h2>Test Data Structure</h2>
 *
 * <pre>
 * 500 Users
 *   └── 5 Orders each (2500 total)
 *         └── 2 Items each (5000 total)
 * </pre>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
@DisplayName("Extended Benchmark (Collections + Large Volume)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExtendedBenchmarkWithCollectionsTest {

    private static EntityManagerFactory emf;
    private static JpaFilterContext<UserProperty> context;

    // Test data configuration
    private static final int USER_COUNT = 500;
    private static final int ORDERS_PER_USER = 5;
    private static final int ITEMS_PER_ORDER = 2;

    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;

    enum UserProperty implements PropertyReference {
        NAME, EMAIL;

        @Override
        public Class<?> getType() {
            return String.class;
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return OperatorUtils.FOR_TEXT;
        }

        @Override
        public Class<?> getEntityType() {
            return UserD.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");

        context = new JpaFilterContext<>(
                UserProperty.class,
                prop -> switch (prop) {
                    case NAME -> "name";
                    case EMAIL -> "email";
                },
                FilterConfig.builder().build());

        insertLargeTestData();
    }

    private static void insertLargeTestData() {
        long startTime = System.currentTimeMillis();
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        int totalOrders = 0;
        int totalItems = 0;

        for (int u = 0; u < USER_COUNT; u++) {
            UserD user = new UserD("User " + u, "user" + u + "@benchmark.com");

            for (int o = 0; o < ORDERS_PER_USER; o++) {
                OrderD order = new OrderD("ORD-" + u + "-" + o);
                user.addOrder(order);
                totalOrders++;

                for (int i = 0; i < ITEMS_PER_ORDER; i++) {
                    OrderItemD item = new OrderItemD("Product " + u + "-" + o + "-" + i, (i + 1) * 10);
                    order.addItem(item);
                    totalItems++;
                }
            }

            em.persist(user);

            // Flush in batches to avoid memory issues
            if (u % 100 == 0) {
                em.flush();
                em.clear();
            }
        }

        em.getTransaction().commit();
        em.close();

        long duration = System.currentTimeMillis() - startTime;
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    TEST DATA SETUP                            ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Users:  %,6d                                              ║%n", USER_COUNT);
        System.out.printf("║  Orders: %,6d (%d per user)                                ║%n", totalOrders,
                ORDERS_PER_USER);
        System.out.printf("║  Items:  %,6d (%d per order)                               ║%n", totalItems,
                ITEMS_PER_ORDER);
        System.out.printf("║  Setup time: %,d ms                                          ║%n", duration);
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @AfterAll
    static void teardown() {
        if (emf != null)
            emf.close();
    }

    // ==================== Warmup ====================

    @Test
    @Order(1)
    @DisplayName("Warmup: JIT compilation")
    void warmup() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                    .projection("name", "email")
                    .pagination(0, 10)
                    .build();

            MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);

            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                FilterQueryFactory.of(context).execute(request, em, strategy);
            }
            System.out.println("✅ Warmup complete");
        }
    }

    // ==================== Scalar Benchmarks ====================

    @Nested
    @DisplayName("Scalar Projection Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ScalarBenchmarks {

        @Test
        @Order(10)
        @DisplayName("50 rows - Scalar projection")
        void benchmark50Rows() {
            runScalarBenchmark(50, "50 rows");
        }

        @Test
        @Order(11)
        @DisplayName("200 rows - Scalar projection")
        void benchmark200Rows() {
            runScalarBenchmark(200, "200 rows");
        }

        @Test
        @Order(12)
        @DisplayName("500 rows - Scalar projection (all users)")
        void benchmark500Rows() {
            runScalarBenchmark(USER_COUNT, USER_COUNT + " rows (ALL)");
        }

        private void runScalarBenchmark(int limit, String label) {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "email")
                        .pagination(0, limit)
                        .build();

                BenchmarkResult result = executeBenchmark(em, request, UserD.class,
                        "Scalar: " + label);

                int expectedCount = Math.min(limit, USER_COUNT);
                assertEquals(expectedCount, result.results.size(),
                        "Should return " + expectedCount + " users");
            }
        }
    }

    // ==================== Collection Benchmarks ====================

    @Nested
    @DisplayName("Collection Projection Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CollectionBenchmarks {

        @Test
        @Order(20)
        @DisplayName("10 users with orders collection")
        void benchmark10UsersWithOrders() {
            runCollectionBenchmark(10, false, "10 users + orders");
        }

        @Test
        @Order(21)
        @DisplayName("50 users with orders collection")
        void benchmark50UsersWithOrders() {
            runCollectionBenchmark(50, false, "50 users + orders");
        }

        @Test
        @Order(22)
        @DisplayName("100 users with orders collection")
        void benchmark100UsersWithOrders() {
            runCollectionBenchmark(100, false, "100 users + orders");
        }

        @Test
        @Order(23)
        @DisplayName("50 users with nested orders.items")
        void benchmark50UsersWithNestedItems() {
            runCollectionBenchmark(50, true, "50 users + orders + items (nested)");
        }

        private void runCollectionBenchmark(int limit, boolean includeItems, String label) {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest.Builder<UserProperty> builder = FilterRequest.<UserProperty>builder()
                        .pagination(0, limit);

                if (includeItems) {
                    builder.projection("name", "orders.orderNumber", "orders.items.productName",
                            "orders.items.quantity");
                } else {
                    builder.projection("name", "orders.orderNumber");
                }

                FilterRequest<UserProperty> request = builder.build();

                BenchmarkResult result = executeBenchmark(em, request, UserD.class,
                        "Collection: " + label);

                int expectedCount = Math.min(limit, USER_COUNT);
                assertEquals(expectedCount, result.results.size(),
                        "Should return exactly " + expectedCount + " users based on pagination");

                // Verify collection data present in results
                if (!result.results.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<RowBuffer> orders = (List<RowBuffer>) result.results.getFirst().get("orders");
                    assertNotNull(orders, "Orders collection should be present");
                }
            }
        }
    }

    // ==================== Data Correctness Tests ====================

    @Nested
    @DisplayName("Data Correctness Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CorrectnessTests {

        @Test
        @Order(40)
        @DisplayName("Should return exact scalar field values")
        void shouldReturnExactScalarValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "email")
                        .pagination(0, 10)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);
                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(10, results.size(), "Should return 10 users");

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);
                    String name = row.get("name").toString();
                    String email = row.get("email").toString();

                    // Extract user index from name
                    int userIndex = Integer.parseInt(name.substring(5));

                    // Verify exact values match insertion logic
                    assertEquals("User " + userIndex, name, "Name should match");
                    assertEquals("user" + userIndex + "@benchmark.com", email, "Email should match");
                }
            }
        }

        @Test
        @Order(41)
        @DisplayName("Should return correct number of orders per user")
        void shouldReturnCorrectOrdersPerUser() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "orders.orderNumber")
                        .pagination(0, 20)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);
                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(20, results.size(), "Should return 20 users");

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);
                    String name = row.get("name").toString();

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> orders = (List<RowBuffer>) row.get("orders");

                    assertNotNull(orders, "Orders should not be null for user: " + name);
                    assertEquals(ORDERS_PER_USER, orders.size(),
                            "User should have exactly " + ORDERS_PER_USER + " orders: " + name);
                }
            }
        }

        @Test
        @Order(42)
        @DisplayName("Should return exact order numbers")
        void shouldReturnExactOrderNumbers() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "orders.orderNumber")
                        .pagination(0, 5)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);
                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(5, results.size(), "Should return 5 users");

                for (RowBuffer row : results) {
                    String name = row.get("name").toString();
                    int userIndex = Integer.parseInt(name.substring(5));

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> orders = (List<RowBuffer>) row.get("orders");

                    for (int o = 0; o < orders.size(); o++) {
                        String orderNumber = orders.get(o).get("orderNumber").toString();
                        String expectedOrderNumber = "ORD-" + userIndex + "-" + o;

                        assertEquals(expectedOrderNumber, orderNumber,
                                "Order number should match for user " + userIndex + ", order " + o);
                    }
                }
            }
        }

        @Test
        @Order(43)
        @DisplayName("Should return correct number of items per order")
        void shouldReturnCorrectItemsPerOrder() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "orders.orderNumber", "orders.items.productName")
                        .pagination(0, 10)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);
                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(10, results.size(), "Should return 10 users");

                for (RowBuffer row : results) {
                    String name = row.get("name").toString();

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> orders = (List<RowBuffer>) row.get("orders");

                    assertNotNull(orders, "Orders should not be null for user: " + name);

                    for (int o = 0; o < orders.size(); o++) {
                        @SuppressWarnings("unchecked")
                        List<RowBuffer> items = (List<RowBuffer>) orders.get(o).get("items");

                        assertNotNull(items, "Items should not be null for order " + o);
                        assertEquals(ITEMS_PER_ORDER, items.size(),
                                "Order should have exactly " + ITEMS_PER_ORDER + " items");
                    }
                }
            }
        }

        @Test
        @Order(44)
        @DisplayName("Should return exact nested item values")
        void shouldReturnExactNestedItemValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "orders.orderNumber", "orders.items.productName", "orders.items.quantity")
                        .pagination(0, 3)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);
                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(3, results.size(), "Should return 3 users");

                for (RowBuffer row : results) {
                    String name = row.get("name").toString();
                    int userIndex = Integer.parseInt(name.substring(5));

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> orders = (List<RowBuffer>) row.get("orders");

                    for (int o = 0; o < orders.size(); o++) {
                        @SuppressWarnings("unchecked")
                        List<RowBuffer> items = (List<RowBuffer>) orders.get(o).get("items");

                        for (int i = 0; i < items.size(); i++) {
                            RowBuffer item = items.get(i);

                            String productName = item.get("productName").toString();
                            Integer quantity = (Integer) item.get("quantity");

                            // Verify exact values match insertion logic
                            String expectedProductName = "Product " + userIndex + "-" + o + "-" + i;
                            int expectedQuantity = (i + 1) * 10;

                            assertEquals(expectedProductName, productName,
                                    "Product name should match for user " + userIndex + ", order " + o + ", item " + i);
                            assertEquals(expectedQuantity, quantity,
                                    "Quantity should match for user " + userIndex + ", order " + o + ", item " + i);
                        }
                    }
                }
            }
        }

        @Test
        @Order(45)
        @DisplayName("Should maintain data integrity across pagination")
        void shouldMaintainDataIntegrityAcrossPagination() {
            try (EntityManager em = emf.createEntityManager()) {
                // Get users 100-104
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "email", "orders.orderNumber")
                        .pagination(99, 5)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);
                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(5, results.size(), "Should return 5 users");

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);
                    int expectedUserIndex = 495 + i;

                    // Verify scalar fields
                    assertEquals("User " + expectedUserIndex, row.get("name").toString());
                    assertEquals("user" + expectedUserIndex + "@benchmark.com", row.get("email").toString());

                    // Verify collection size
                    @SuppressWarnings("unchecked")
                    List<RowBuffer> orders = (List<RowBuffer>) row.get("orders");
                    assertEquals(ORDERS_PER_USER, orders.size());

                    // Verify first order number
                    String firstOrderNumber = orders.get(0).get("orderNumber").toString();
                    assertEquals("ORD-" + expectedUserIndex + "-0", firstOrderNumber);
                }
            }
        }

        @Test
        @Order(46)
        @DisplayName("Should handle large result sets with complete accuracy")
        void shouldHandleLargeResultSetsAccurately() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "email")
                        .pagination(0, 100)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserD.class);
                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(100, results.size(), "Should return 100 users");

                // Verify random samples for accuracy
                int[] samplesToCheck = {0, 25, 50, 75, 99};

                for (int idx : samplesToCheck) {
                    RowBuffer row = results.get(idx);
                    String name = row.get("name").toString();
                    String email = row.get("email").toString();

                    int userIndex = Integer.parseInt(name.substring(5));

                    assertEquals("User " + userIndex, name);
                    assertEquals("user" + userIndex + "@benchmark.com", email);
                }
            }
        }
    }

    // ==================== Benchmark Utilities ====================

    private BenchmarkResult executeBenchmark(EntityManager em, FilterRequest<UserProperty> request,
                                             Class<?> projectionClass, String label) {
        MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(projectionClass);

        long[] times = new long[BENCHMARK_ITERATIONS];
        List<RowBuffer> results = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            results = FilterQueryFactory.of(context).execute(request, em, strategy);
            times[i] = System.nanoTime() - start;
        }

        long avgTime = average(times);
        long minTime = min(times);

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("📊 BENCHMARK: " + label);
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.printf("   Speed: avg=%6.2fms, min=%6.2fms%n", avgTime / 1_000_000.0, minTime / 1_000_000.0);
        System.out.printf("   Results: %d rows%n", results.size());
        System.out.println("═══════════════════════════════════════════════════════════════");

        return new BenchmarkResult(results, avgTime);
    }

    private long average(long[] times) {
        long sum = 0;
        for (long t : times)
            sum += t;
        return sum / times.length;
    }

    private long min(long[] times) {
        long min = Long.MAX_VALUE;
        for (long t : times)
            if (t < min)
                min = t;
        return min;
    }

    private record BenchmarkResult(
            List<RowBuffer> results,
            long avgNanos) {
    }
}
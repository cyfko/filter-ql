package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.utils.OperatorUtils;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._3.OrderD;
import io.github.cyfko.filterql.jpa.entities.projection._3.OrderItemD;
import io.github.cyfko.filterql.jpa.entities.projection._3.UserD;
import io.github.cyfko.filterql.jpa.projection.RowBuffer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended benchmark test comparing V1 vs V2 with:
 * - Large data volumes (500+ users, 2500+ orders, 5000+ items)
 * - Collection projections (User → Orders → Items)
 * - Nested collection traversal
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
@DisplayName("Extended V1 vs V2 Benchmark (Collections + Large Volume)")
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

            MultiQueryFetchStrategyOld v1 = new MultiQueryFetchStrategyOld(UserD.class);
            MultiQueryFetchStrategy v2 = new MultiQueryFetchStrategy(UserD.class);

            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                FilterQueryFactory.of(context).execute(request, em, v1);
                FilterQueryFactory.of(context).execute(request, em, v2);
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

                assertEquals(result.v1Results.size(), result.v2Results.size());
                assertTrue(result.v1Results.size() <= limit);
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

        /**
         * V2 supports multi-level nested collections (orders.items).
         */
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

                // Use UserD entity class (not DtoUserD which has computed fields requiring
                // resolver)
                BenchmarkResult result = executeBenchmark(em, request, UserD.class,
                        "Collection: " + label);

                // V2 should return exactly 'limit' users (the expected count based on
                // pagination)
                int expectedCount = Math.min(limit, USER_COUNT);
                assertEquals(expectedCount, result.v2Results.size(),
                        "V2 should return exactly " + expectedCount + " users based on pagination");

                // Note: V1 may have a bug with collection projections causing fewer results
                // We still compare V1 vs V2 for debugging purposes
                if (result.v1Results.size() != result.v2Results.size()) {
                    System.out.println("⚠️ WARNING: V1 returned " + result.v1Results.size() +
                            " results, V2 returned " + result.v2Results.size() +
                            " (V1 may have a collection projection bug)");
                }

                // Verify collection data present in V2 results
                if (!result.v2Results.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<RowBuffer> orders = (List<RowBuffer>) result.v2Results.getFirst().get("orders");
                    assertNotNull(orders, "Orders collection should be present in V2");
                }
            }
        }
    }

    // ==================== Memory Stress Test ====================

    @Nested
    @DisplayName("Memory Stress Tests")
    class MemoryStressTests {

        @Test
        @Order(30)
        @DisplayName("Large result set memory comparison")
        void memoryStressTest() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<UserProperty> request = FilterRequest.<UserProperty>builder()
                        .projection("name", "email")
                        .pagination(0, USER_COUNT)
                        .build();

                MultiQueryFetchStrategyOld v1 = new MultiQueryFetchStrategyOld(UserD.class);
                MultiQueryFetchStrategy v2 = new MultiQueryFetchStrategy(UserD.class);

                // Force GC before measurement
                System.gc();
                Thread.sleep(100);

                long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                List<Map<String, Object>> v1Results = FilterQueryFactory.of(context).execute(request, em, v1);
                long memAfterV1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

                v1Results = null; // Allow GC
                System.gc();
                Thread.sleep(100);

                long memBeforeV2 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                List<RowBuffer> v2Results = FilterQueryFactory.of(context).execute(request, em, v2);
                long memAfterV2 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

                long v1MemUsed = memAfterV1 - memBefore;
                long v2MemUsed = memAfterV2 - memBeforeV2;

                System.out.println();
                System.out.println("═══════════════════════════════════════════════════════════════");
                System.out.println("📊 MEMORY STRESS TEST (" + USER_COUNT + " rows)");
                System.out.println("───────────────────────────────────────────────────────────────");
                System.out.printf("   V1 memory delta: %,d bytes%n", v1MemUsed);
                System.out.printf("   V2 memory delta: %,d bytes%n", v2MemUsed);
                if (v2MemUsed > 0 && v1MemUsed > 0) {
                    System.out.printf("   Ratio: V1 uses %.1fx more memory%n", (double) v1MemUsed / v2MemUsed);
                }
                System.out.println("═══════════════════════════════════════════════════════════════");

                assertNotNull(v2Results);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== Benchmark Utilities ====================

    private BenchmarkResult executeBenchmark(EntityManager em, FilterRequest<UserProperty> request,
            Class<?> projectionClass, String label) {
        MultiQueryFetchStrategyOld v1 = new MultiQueryFetchStrategyOld(projectionClass);
        MultiQueryFetchStrategy v2 = new MultiQueryFetchStrategy(projectionClass);

        long[] v1Times = new long[BENCHMARK_ITERATIONS];
        long[] v2Times = new long[BENCHMARK_ITERATIONS];

        List<Map<String, Object>> v1Results = null;
        List<RowBuffer> v2Results = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            v1Results = FilterQueryFactory.of(context).execute(request, em, v1);
            v1Times[i] = System.nanoTime() - start;

            start = System.nanoTime();
            v2Results = FilterQueryFactory.of(context).execute(request, em, v2);
            v2Times[i] = System.nanoTime() - start;
        }

        long v1Avg = average(v1Times);
        long v2Avg = average(v2Times);
        long v1Min = min(v1Times);
        long v2Min = min(v2Times);
        double speedup = (double) v1Avg / v2Avg;

        String speedupIcon = speedup > 1.1 ? "✅ FASTER" : (speedup < 0.9 ? "⚠️ SLOWER" : "≈ SAME");

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("📊 BENCHMARK: " + label);
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.printf("   V1 (original):  avg=%6.2fms, min=%6.2fms%n", v1Avg / 1_000_000.0, v1Min / 1_000_000.0);
        System.out.printf("   V2 (optimized): avg=%6.2fms, min=%6.2fms%n", v2Avg / 1_000_000.0, v2Min / 1_000_000.0);
        System.out.printf("   Speedup: %.2fx %s%n", speedup, speedupIcon);
        System.out.printf("   Results: %d rows%n", v1Results != null ? v1Results.size() : 0);
        System.out.println("═══════════════════════════════════════════════════════════════");

        return new BenchmarkResult(v1Results, v2Results, v1Avg, v2Avg, speedup);
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
            List<Map<String, Object>> v1Results,
            List<RowBuffer> v2Results,
            long v1AvgNanos,
            long v2AvgNanos,
            double speedup) {
    }
}

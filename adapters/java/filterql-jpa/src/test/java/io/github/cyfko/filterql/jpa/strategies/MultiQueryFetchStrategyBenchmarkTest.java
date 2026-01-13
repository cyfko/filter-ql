package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.utils.OperatorUtils;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._1.AddressB;
import io.github.cyfko.filterql.jpa.entities.projection._1.City;
import io.github.cyfko.filterql.jpa.entities.projection._1.UserB;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark test comparing MultiQueryFetchStrategy V1 vs V2 performance.
 * <p>
 * This test measures execution time and verifies result correctness between
 * the original implementation and the optimized V2 implementation.
 * </p>
 * 
 * <h2>Metrics Measured</h2>
 * <ul>
 * <li>Execution time (ms)</li>
 * <li>Result correctness (identical outputs)</li>
 * <li>Warmup iterations to eliminate JIT effects</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
@DisplayName("MultiQueryFetchStrategy V1 vs V2 Benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiQueryFetchStrategyBenchmarkTest {

    private static EntityManagerFactory emf;
    private static JpaFilterContext<BenchmarkUserProperty> context;

    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 10;
    private static final int DATA_SIZE = 100; // Number of test entities

    /**
     * Property reference enum for User entity.
     */
    enum BenchmarkUserProperty implements PropertyReference {
        NAME,
        EMAIL,
        ACTIVE,
        PHONE,
        CITY_NAME;

        @Override
        public Class<?> getType() {
            return switch (this) {
                case NAME, EMAIL, PHONE, CITY_NAME -> String.class;
                case ACTIVE -> Boolean.class;
            };
        }

        @Override
        public Set<Op> getSupportedOperators() {
            return switch (this) {
                case NAME, EMAIL, PHONE, CITY_NAME -> OperatorUtils.FOR_TEXT;
                case ACTIVE -> Set.of(Op.EQ);
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
                BenchmarkUserProperty.class,
                prop -> switch (prop) {
                    case NAME -> "name";
                    case EMAIL -> "email";
                    case ACTIVE -> "active";
                    case PHONE -> "phone";
                    case CITY_NAME -> "address.city.name";
                },
                FilterConfig.builder().build());

        // Insert test data
        insertTestData();
    }

    private static void insertTestData() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        City[] cities = {
                new City("Paris", "75001"),
                new City("Lyon", "69001"),
                new City("Marseille", "13001"),
                new City("Bordeaux", "33000"),
                new City("Lille", "59000")
        };

        for (int i = 0; i < DATA_SIZE; i++) {
            City city = cities[i % cities.length];
            AddressB address = new AddressB(city);

            UserB user = new UserB(
                    "User " + i,
                    "user" + i + "@example.com",
                    i % 2 == 0, // Alternate active/inactive
                    "+1-555-" + String.format("%04d", i),
                    address);
            em.persist(user);
        }

        em.getTransaction().commit();
        em.close();

        System.out.println("✅ Inserted " + DATA_SIZE + " test entities");
    }

    @AfterAll
    static void teardown() {
        if (emf != null)
            emf.close();
    }

    @Nested
    @DisplayName("Performance Comparison Tests")
    class PerformanceTests {

        @Test
        @Order(1)
        @DisplayName("Warmup: Execute both strategies to trigger JIT compilation")
        void warmup() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .filter("activeArg", BenchmarkUserProperty.ACTIVE, "EQ", true)
                        .combineWith("and")
                        .projection("name", "email", "phone")
                        .pagination(0, 50)
                        .build();

                MultiQueryFetchStrategyOld strategyV1 = new MultiQueryFetchStrategyOld(UserB.class);
                MultiQueryFetchStrategy strategyV2 = new MultiQueryFetchStrategy(UserB.class);

                // Warmup iterations
                for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                    FilterQueryFactory.of(context).execute(request, em, strategyV1);
                    FilterQueryFactory.of(context).execute(request, em, strategyV2);
                }

                System.out.println("✅ Warmup complete (" + WARMUP_ITERATIONS + " iterations each)");
            }
        }

        @Test
        @Order(2)
        @DisplayName("Benchmark: Simple scalar projection (3 fields)")
        void benchmarkScalarProjection() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .filter("activeArg", BenchmarkUserProperty.ACTIVE, "EQ", true)
                        .combineWith("and")
                        .projection("name", "email", "phone")
                        .pagination(0, 50)
                        .build();

                BenchmarkResult result = runBenchmark(em, request, "Scalar projection (3 fields)");

                // Assertions
                assertTrue(result.v2SpeedupFactor >= 0.5,
                        "V2 should not be significantly slower than V1. Speedup: " + result.v2SpeedupFactor);
                assertEquals(result.v1Results.size(), result.v2Results.size(),
                        "Result counts should match");
            }
        }

        @Test
        @Order(3)
        @DisplayName("Benchmark: Nested path projection")
        void benchmarkNestedProjection() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .projection("name", "email", "address.city.name", "address.city.zipCode")
                        .pagination(0, 50)
                        .build();

                BenchmarkResult result = runBenchmark(em, request, "Nested path projection");

                // Assertions
                assertTrue(result.v2SpeedupFactor >= 0.5,
                        "V2 should not be significantly slower");
                assertEquals(result.v1Results.size(), result.v2Results.size());
            }
        }

        @Test
        @Order(4)
        @DisplayName("Benchmark: Large result set (all entities)")
        void benchmarkLargeResultSet() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .projection("name", "email")
                        .pagination(0, DATA_SIZE)
                        .build();

                BenchmarkResult result = runBenchmark(em, request, "Large result set (" + DATA_SIZE + " rows)");

                assertEquals(result.v1Results.size(), result.v2Results.size());
            }
        }

        private BenchmarkResult runBenchmark(EntityManager em, FilterRequest<BenchmarkUserProperty> request,
                String testName) {
            MultiQueryFetchStrategyOld strategyV1 = new MultiQueryFetchStrategyOld(UserB.class);
            MultiQueryFetchStrategy strategyV2 = new MultiQueryFetchStrategy(UserB.class);

            long[] v1Times = new long[BENCHMARK_ITERATIONS];
            long[] v2Times = new long[BENCHMARK_ITERATIONS];

            List<Map<String, Object>> v1Results = null;
            List<Map<String, Object>> v2Results = null;

            // Run benchmark iterations
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                // V1
                long startV1 = System.nanoTime();
                v1Results = FilterQueryFactory.of(context).execute(request, em, strategyV1);
                v1Times[i] = System.nanoTime() - startV1;

                // V2
                long startV2 = System.nanoTime();
                v2Results = FilterQueryFactory.of(context).execute(request, em, strategyV2);
                v2Times[i] = System.nanoTime() - startV2;
            }

            // Calculate statistics
            long v1Avg = average(v1Times);
            long v2Avg = average(v2Times);
            long v1Min = min(v1Times);
            long v2Min = min(v2Times);
            double speedup = (double) v1Avg / v2Avg;

            // Print results
            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("📊 BENCHMARK: " + testName);
            System.out.println("───────────────────────────────────────────────────────────────");
            System.out.printf("   V1 (original):  avg=%6.2fms, min=%6.2fms%n", v1Avg / 1_000_000.0,
                    v1Min / 1_000_000.0);
            System.out.printf("   V2 (optimized): avg=%6.2fms, min=%6.2fms%n", v2Avg / 1_000_000.0,
                    v2Min / 1_000_000.0);
            System.out.printf("   Speedup: %.2fx %s%n", speedup,
                    speedup > 1 ? "✅ FASTER" : (speedup < 0.9 ? "⚠️ SLOWER" : "≈ SAME"));
            System.out.printf("   Results: V1=%d rows, V2=%d rows%n", v1Results.size(), v2Results.size());
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
                List<Map<String, Object>> v2Results,
                long v1AvgNanos,
                long v2AvgNanos,
                double v2SpeedupFactor) {
        }
    }

    @Nested
    @DisplayName("Result Correctness Tests")
    class CorrectnessTests {

        @Test
        @Order(10)
        @DisplayName("V1 and V2 should return identical scalar results")
        void shouldReturnIdenticalScalarResults() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .filter("activeArg", BenchmarkUserProperty.ACTIVE, "EQ", true)
                        .combineWith("and")
                        .projection("name", "email")
                        .pagination(0, 10)
                        .build();

                MultiQueryFetchStrategyOld strategyV1 = new MultiQueryFetchStrategyOld(UserB.class);
                MultiQueryFetchStrategy strategyV2 = new MultiQueryFetchStrategy(UserB.class);

                List<Map<String, Object>> v1Results = FilterQueryFactory.of(context).execute(request, em, strategyV1);
                List<Map<String, Object>> v2Results = FilterQueryFactory.of(context).execute(request, em, strategyV2);

                // Size check
                assertEquals(v1Results.size(), v2Results.size(), "Result count should match");

                // Content check
                for (int i = 0; i < v1Results.size(); i++) {
                    Map<String, Object> v1Row = v1Results.get(i);
                    Map<String, Object> v2Row = v2Results.get(i);

                    assertEquals(v1Row.get("name"), v2Row.get("name"), "Name should match at index " + i);
                    assertEquals(v1Row.get("email"), v2Row.get("email"), "Email should match at index " + i);
                }

                System.out.println("✅ Correctness verified: " + v1Results.size() + " rows match");
            }
        }

        @Test
        @Order(11)
        @DisplayName("V1 and V2 should return identical nested path results")
        void shouldReturnIdenticalNestedResults() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .projection("name", "address.city.name")
                        .pagination(0, 10)
                        .build();

                MultiQueryFetchStrategyOld strategyV1 = new MultiQueryFetchStrategyOld(UserB.class);
                MultiQueryFetchStrategy strategyV2 = new MultiQueryFetchStrategy(UserB.class);

                List<Map<String, Object>> v1Results = FilterQueryFactory.of(context).execute(request, em, strategyV1);
                List<Map<String, Object>> v2Results = FilterQueryFactory.of(context).execute(request, em, strategyV2);

                assertEquals(v1Results.size(), v2Results.size());

                for (int i = 0; i < v1Results.size(); i++) {
                    Map<String, Object> v1Row = v1Results.get(i);
                    Map<String, Object> v2Row = v2Results.get(i);

                    assertEquals(v1Row.get("name"), v2Row.get("name"));

                    // Check nested structure
                    @SuppressWarnings("unchecked")
                    Map<String, Object> v1Address = (Map<String, Object>) v1Row.get("address");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> v2Address = (Map<String, Object>) v2Row.get("address");

                    assertNotNull(v1Address, "V1 should have address");
                    assertNotNull(v2Address, "V2 should have address");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> v1City = (Map<String, Object>) v1Address.get("city");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> v2City = (Map<String, Object>) v2Address.get("city");

                    assertEquals(v1City.get("name"), v2City.get("name"), "City name should match");
                }

                System.out.println("✅ Nested structure correctness verified: " + v1Results.size() + " rows match");
            }
        }
    }
}

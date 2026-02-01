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
import io.github.cyfko.filterql.jpa.projection.RowBuffer;
import io.github.cyfko.filterql.jpa.projection.NestedView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark test MultiQueryFetchStrategy performance.
 * <p>
 * This test measures execution time and verifies result correctness of
 * the optimized V2 implementation.
 * </p>
 *
 * <h2>Metrics Measured</h2>
 * <ul>
 * <li>Execution time (ms)</li>
 * <li>Result correctness</li>
 * <li>Warmup iterations to eliminate JIT effects</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 */
@DisplayName("MultiQueryFetchStrategy Benchmark")
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
        @DisplayName("Warmup: Execute strategy to trigger JIT compilation")
        void warmup() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .filter("activeArg", BenchmarkUserProperty.ACTIVE, "EQ", true)
                        .combineWith("and")
                        .projection("name", "email", "phone")
                        .pagination(0, 50)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

                // Warmup iterations
                for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                    FilterQueryFactory.of(context).execute(request, em, strategy);
                }

                System.out.println("✅ Warmup complete (" + WARMUP_ITERATIONS + " iterations)");
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
                assertEquals(50, result.results.size(), "Should return 50 active users");
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
                assertEquals(50, result.results.size(), "Should return first 50 users");
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

                assertEquals(DATA_SIZE, result.results.size(), "Should return all " + DATA_SIZE + " users");
            }
        }

        private BenchmarkResult runBenchmark(EntityManager em, FilterRequest<BenchmarkUserProperty> request,
                                             String testName) {
            MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

            long[] times = new long[BENCHMARK_ITERATIONS];
            List<RowBuffer> results = null;

            // Run benchmark iterations
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                results = FilterQueryFactory.of(context).execute(request, em, strategy);
                times[i] = System.nanoTime() - start;
            }

            // Calculate statistics
            long avgTime = average(times);
            long minTime = min(times);

            // Print results
            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("📊 BENCHMARK: " + testName);
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

    @Nested
    @DisplayName("Result Correctness Tests")
    class CorrectnessTests {

        @Test
        @Order(10)
        @DisplayName("Should return valid scalar results")
        void shouldReturnValidScalarResults() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .filter("activeArg", BenchmarkUserProperty.ACTIVE, "EQ", true)
                        .combineWith("and")
                        .projection("name", "email")
                        .pagination(0, 10)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                // Verify results
                assertEquals(10, results.size(), "Should return 10 results");

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);

                    assertNotNull(row.get("name"), "Name should not be null at index " + i);
                    assertNotNull(row.get("email"), "Email should not be null at index " + i);
                    assertTrue(row.get("email").toString().contains("@example.com"),
                            "Email should have correct format at index " + i);
                }
            }
        }

        @Test
        @Order(11)
        @DisplayName("Should return valid nested path results")
        void shouldReturnValidNestedResults() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .projection("name", "address.city.name")
                        .pagination(0, 10)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(10, results.size(), "Should return 10 results");

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);

                    assertNotNull(row.get("name"), "Name should not be null at index " + i);

                    // Check nested structure
                    NestedView address = (NestedView) row.get("address");
                    assertNotNull(address, "Address should not be null at index " + i);

                    NestedView city = (NestedView) address.get("city");
                    assertNotNull(city, "City should not be null at index " + i);
                    assertNotNull(city.get("name"), "City name should not be null at index " + i);
                }
            }
        }

        @Test
        @Order(12)
        @DisplayName("Should return exact field values matching inserted data")
        void shouldReturnExactFieldValues() {
            try (EntityManager em = emf.createEntityManager()) {
                // Test specific users with known IDs
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .projection("name", "email", "phone", "active")
                        .pagination(0, 5)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(5, results.size(), "Should return 5 results");

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);

                    String name = row.get("name").toString();
                    String email = row.get("email").toString();
                    String phone = row.get("phone").toString();
                    Boolean active = (Boolean) row.get("active");

                    // Extract user index from name (format: "User X")
                    int userIndex = Integer.parseInt(name.substring(5));

                    // Verify exact values match insertion logic
                    assertEquals("User " + userIndex, name, "Name should match");
                    assertEquals("user" + userIndex + "@example.com", email, "Email should match");
                    assertEquals("+1-555-" + String.format("%04d", userIndex), phone, "Phone should match");
                    assertEquals(userIndex % 2 == 0, active, "Active status should match");
                }
            }
        }

        @Test
        @Order(13)
        @DisplayName("Should return exact nested values matching inserted data")
        void shouldReturnExactNestedValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .projection("name", "address.city.name", "address.city.zipCode")
                        .pagination(0, 20)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(20, results.size(), "Should return 20 results");

                // Expected cities in order (cyclic pattern)
                String[][] expectedCities = {
                        {"Paris", "75001"},
                        {"Lyon", "69001"},
                        {"Marseille", "13001"},
                        {"Bordeaux", "33000"},
                        {"Lille", "59000"}
                };

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);

                    String name = row.get("name").toString();
                    int userIndex = Integer.parseInt(name.substring(5));

                    // Get nested city data
                    NestedView address = (NestedView) row.get("address");
                    assertNotNull(address, "Address should exist for user " + userIndex);

                    NestedView city = (NestedView) address.get("city");
                    assertNotNull(city, "City should exist for user " + userIndex);

                    String cityName = city.get("name").toString();
                    String zipCode = city.get("zipCode").toString();

                    // Verify city matches insertion pattern (userIndex % 5)
                    int cityIndex = userIndex % 5;
                    assertEquals(expectedCities[cityIndex][0], cityName,
                            "City name should match for user " + userIndex);
                    assertEquals(expectedCities[cityIndex][1], zipCode,
                            "Zip code should match for user " + userIndex);
                }
            }
        }

        @Test
        @Order(14)
        @DisplayName("Should correctly filter active users and return exact values")
        void shouldFilterActiveUsersWithExactValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .filter("activeArg", BenchmarkUserProperty.ACTIVE, "EQ", true)
                        .combineWith("and")
                        .projection("name", "email", "active")
                        .pagination(0, 10)
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(10, results.size(), "Should return 10 active users");

                for (RowBuffer row : results) {
                    String name = row.get("name").toString();
                    int userIndex = Integer.parseInt(name.substring(5));

                    // All returned users must be active
                    Boolean active = (Boolean) row.get("active");
                    assertTrue(active, "User " + userIndex + " should be active");

                    // Verify user index is even (as per insertion logic: i % 2 == 0 -> active)
                    assertEquals(0, userIndex % 2,
                            "Active users should have even indices, but got " + userIndex);
                }
            }
        }

        @Test
        @Order(15)
        @DisplayName("Should return all fields with complete data integrity")
        void shouldReturnCompleteDataIntegrity() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<BenchmarkUserProperty> request = FilterRequest.<BenchmarkUserProperty>builder()
                        .projection("name", "email", "phone", "active", "address.city.name", "address.city.zipCode")
                        .pagination(1, 10) // Get users 10-19
                        .build();

                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserB.class);

                List<RowBuffer> results = FilterQueryFactory.of(context).execute(request, em, strategy);

                assertEquals(10, results.size(), "Should return 10 results");

                String[][] expectedCities = {
                        {"Paris", "75001"},
                        {"Lyon", "69001"},
                        {"Marseille", "13001"},
                        {"Bordeaux", "33000"},
                        {"Lille", "59000"}
                };

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);
                    int expectedUserIndex = 10 + i;

                    // Verify all scalar fields
                    assertEquals("User " + expectedUserIndex, row.get("name").toString());
                    assertEquals("user" + expectedUserIndex + "@example.com", row.get("email").toString());
                    assertEquals("+1-555-" + String.format("%04d", expectedUserIndex), row.get("phone").toString());
                    assertEquals(expectedUserIndex % 2 == 0, row.get("active"));

                    // Verify nested fields
                    NestedView address = (NestedView) row.get("address");
                    NestedView city = (NestedView) address.get("city");

                    int cityIndex = expectedUserIndex % 5;
                    assertEquals(expectedCities[cityIndex][0], city.get("name").toString(),
                            "City name should match for user " + expectedUserIndex);
                    assertEquals(expectedCities[cityIndex][1], city.get("zipCode").toString(),
                            "Zip code should match for user " + expectedUserIndex);
                }
            }
        }
    }
}
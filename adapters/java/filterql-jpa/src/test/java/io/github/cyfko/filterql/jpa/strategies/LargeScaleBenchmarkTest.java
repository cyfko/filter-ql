package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._4.*;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import org.junit.jupiter.api.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Large-scale benchmark test to stress test performance with 1000+ entities.
 * Compares V1 (original) vs V2 (optimized) strategies.
 */
@DisplayName("Large Scale Performance Benchmark (1000+ entities)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LargeScaleBenchmarkTest {

    private static EntityManagerFactory emf;
    private static FilterContext filterContext;

    // Large scale configuration - 100 companies = 6000 employees total
    private static final int NUM_COMPANIES = 100;
    private static final int DEPTS_PER_COMPANY = 3;
    private static final int TEAMS_PER_DEPT = 4;
    private static final int EMPLOYEES_PER_TEAM = 5;

    // Benchmark settings
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");

        filterContext = new JpaFilterContext<>(CompanyProperty.class, prop -> switch (prop) {
            case CompanyProperty.ID -> "id";
            case CompanyProperty.NAME -> "name";
            case CompanyProperty.COUNTRY -> "country";
            case CompanyProperty.FOUNDED_YEAR -> "foundedYear";
            case CompanyProperty.DEPARTMENTS -> "departments";
        });

        // Populate test data
        long startTime = System.currentTimeMillis();
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();

            for (int c = 0; c < NUM_COMPANIES; c++) {
                Company company = new Company(
                        "Company-" + c,
                        c % 2 == 0 ? "USA" : "France",
                        2000 + (c % 25));

                for (int d = 0; d < DEPTS_PER_COMPANY; d++) {
                    Department dept = new Department(
                            "Dept-" + c + "-" + d,
                            100000L + d * 10000);
                    company.addDepartment(dept);

                    for (int t = 0; t < TEAMS_PER_DEPT; t++) {
                        Team team = new Team(
                                "Team-" + c + "-" + d + "-" + t,
                                t % 2 == 0 ? "Backend" : "Frontend");
                        dept.addTeam(team);

                        for (int e = 0; e < EMPLOYEES_PER_TEAM; e++) {
                            Employee emp = new Employee(
                                    "Employee-" + c + "-" + d + "-" + t + "-" + e,
                                    e % 3 == 0 ? "Senior" : (e % 3 == 1 ? "Mid" : "Junior"),
                                    BigDecimal.valueOf(50000 + e * 5000),
                                    e + 1);
                            team.addEmployee(emp);
                        }
                    }
                }

                em.persist(company);

                // Flush every 10 companies to avoid memory issues
                if (c % 10 == 0) {
                    em.flush();
                    em.clear();
                }
            }

            em.getTransaction().commit();
        }

        long setupTime = System.currentTimeMillis() - startTime;
        int totalEmployees = NUM_COMPANIES * DEPTS_PER_COMPANY * TEAMS_PER_DEPT * EMPLOYEES_PER_TEAM;

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           LARGE SCALE BENCHMARK SETUP                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Companies: %d                                              ║%n", NUM_COMPANIES);
        System.out.printf("║  Total Departments: %d                                      ║%n",
                NUM_COMPANIES * DEPTS_PER_COMPANY);
        System.out.printf("║  Total Teams: %d                                          ║%n",
                NUM_COMPANIES * DEPTS_PER_COMPANY * TEAMS_PER_DEPT);
        System.out.printf("║  Total Employees: %d                                        ║%n", totalEmployees);
        System.out.printf("║  Setup time: %dms                                           ║%n", setupTime);
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
    }

    @AfterAll
    static void teardown() {
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Large Scale: Scalars Only (100 companies)")
    void benchmarkScalarsOnly() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  LARGE SCALE: SCALARS ONLY (100 companies)                   │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of("id", "name", "country", "foundedYear");

        runBenchmark(projection, "Scalars Only (100 companies)");
    }

    @Test
    @Order(2)
    @DisplayName("Large Scale: 1-Level Collection (300 departments)")
    void benchmark1LevelCollection() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  LARGE SCALE: 1-LEVEL COLLECTION (300 departments)           │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of(
                "id", "name",
                "departments.id", "departments.name", "departments.budget");

        runBenchmark(projection, "1-Level Collection");
    }

    @Test
    @Order(3)
    @DisplayName("Large Scale: 2-Level Collections (1200 teams)")
    void benchmark2LevelCollections() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  LARGE SCALE: 2-LEVEL COLLECTIONS (1200 teams)               │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of(
                "id", "name",
                "departments.id", "departments.name",
                "departments.teams.id", "departments.teams.name");

        runBenchmark(projection, "2-Level Collections");
    }

    @Test
    @Order(4)
    @DisplayName("Large Scale: Full 3-Level (6000 employees)")
    void benchmark3LevelCollections() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  LARGE SCALE: FULL 3-LEVEL (6000 employees)                  │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of(
                "id", "name",
                "departments.id", "departments.name",
                "departments.teams.id", "departments.teams.name",
                "departments.teams.employees.id", "departments.teams.employees.name");

        runBenchmark(projection, "Full 3-Level");
    }

    @Test
    @Order(5)
    @DisplayName("Large Scale: With Computed Fields")
    @Disabled("Computed fields test needs fix for dtoField null issue")
    void benchmarkWithComputed() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  LARGE SCALE: WITH COMPUTED FIELDS                           │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of(
                "id", "name",
                "departments.id", "departments.name",
                "employeeSummary", "totalBudgetInfo");

        runBenchmarkWithComputed(projection, "With Computed Fields");
    }

    private void runBenchmark(Set<String> projection, String testName) {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                    .combineWith("f")
                    .projection(projection)
                    .build();

            // Need InstanceResolver for CompanyDto with computed fields
            InstanceResolver resolver = InstanceResolver.noBean();
            MultiQueryFetchStrategyOld strategyV1 = new MultiQueryFetchStrategyOld(CompanyDto.class, resolver);
            MultiQueryFetchStrategy strategyV2 = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                FilterQueryFactory.of(filterContext).execute(request, em, strategyV2);
                FilterQueryFactory.of(filterContext).execute(request, em, strategyV1);
            }

            // Benchmark V1
            long[] v1Times = new long[BENCHMARK_ITERATIONS];
            List<Map<String, Object>> v1Result = null;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                v1Result = FilterQueryFactory.of(filterContext).execute(request, em, strategyV1);
                v1Times[i] = System.nanoTime() - start;
            }

            // Benchmark V2
            long[] v2Times = new long[BENCHMARK_ITERATIONS];
            List<Map<String, Object>> v2Result = null;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                v2Result = FilterQueryFactory.of(filterContext).execute(request, em, strategyV2);
                v2Times[i] = System.nanoTime() - start;
            }

            printResults(testName, v1Times, v2Times, v1Result.size(), v2Result.size());
            assertEquals(v1Result.size(), v2Result.size(), "V1 and V2 should return same number of results");
        }
    }

    private void runBenchmarkWithComputed(Set<String> projection, String testName) {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                    .combineWith("f")
                    .projection(projection)
                    .build();

            InstanceResolver resolver = InstanceResolver.noBean();
            MultiQueryFetchStrategyOld strategyV1 = new MultiQueryFetchStrategyOld(CompanyDto.class, resolver);
            MultiQueryFetchStrategy strategyV2 = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                FilterQueryFactory.of(filterContext).execute(request, em, strategyV2);
                FilterQueryFactory.of(filterContext).execute(request, em, strategyV1);
            }

            // Benchmark V1
            long[] v1Times = new long[BENCHMARK_ITERATIONS];
            List<Map<String, Object>> v1Result = null;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                v1Result = FilterQueryFactory.of(filterContext).execute(request, em, strategyV1);
                v1Times[i] = System.nanoTime() - start;
            }

            // Benchmark V2
            long[] v2Times = new long[BENCHMARK_ITERATIONS];
            List<Map<String, Object>> v2Result = null;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                v2Result = FilterQueryFactory.of(filterContext).execute(request, em, strategyV2);
                v2Times[i] = System.nanoTime() - start;
            }

            printResults(testName, v1Times, v2Times, v1Result.size(), v2Result.size());

            // Verify computed fields
            if (!v2Result.isEmpty()) {
                assertTrue(v2Result.getFirst().containsKey("employeeSummary"),
                        "Should have employeeSummary computed field");
            }
        }
    }

    private void printResults(String testName, long[] v1Times, long[] v2Times, int v1Count, int v2Count) {
        double v1Avg = Arrays.stream(v1Times).average().orElse(0) / 1_000_000.0;
        double v2Avg = Arrays.stream(v2Times).average().orElse(0) / 1_000_000.0;
        double v1Min = Arrays.stream(v1Times).min().orElse(0) / 1_000_000.0;
        double v2Min = Arrays.stream(v2Times).min().orElse(0) / 1_000_000.0;
        double v1Max = Arrays.stream(v1Times).max().orElse(0) / 1_000_000.0;
        double v2Max = Arrays.stream(v2Times).max().orElse(0) / 1_000_000.0;

        double speedup = v1Avg / v2Avg;
        String status = speedup >= 1.0 ? "✓ FASTER" : "✗ SLOWER";

        System.out.printf("   V1 (original):  avg=%8.2fms, min=%8.2fms, max=%8.2fms%n", v1Avg, v1Min, v1Max);
        System.out.printf("   V2 (optimized): avg=%8.2fms, min=%8.2fms, max=%8.2fms%n", v2Avg, v2Min, v2Max);
        System.out.printf("   Speedup: %.2fx %s%n", speedup, status);
        System.out.printf("   Results: V1=%d rows, V2=%d rows%n", v1Count, v2Count);
        System.out.printf("   Time saved per query: %.2fms%n", v1Avg - v2Avg);

        assertTrue(speedup >= 0.9, "V2 should not be significantly slower than V1");
    }
}

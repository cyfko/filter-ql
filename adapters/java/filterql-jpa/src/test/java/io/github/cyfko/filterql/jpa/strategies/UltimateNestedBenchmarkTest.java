package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._4.*;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ultimate benchmark test comparing V1 vs V2 performance with:
 * - Scalar field projections
 * - Computed fields with 3-level collection dependencies
 * - 3 levels of nested collections (Company -> Department -> Team -> Employee)
 */
@DisplayName("Ultimate 3-Level Nested Collection Benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UltimateNestedBenchmarkTest {

    private static EntityManagerFactory emf;
    private static FilterContext filterContext;

    private static final int NUM_COMPANIES = 10;
    private static final int DEPTS_PER_COMPANY = 3;
    private static final int TEAMS_PER_DEPT = 4;
    private static final int EMPLOYEES_PER_TEAM = 5;

    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 10;

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");
        filterContext = new JpaFilterContext<>(CompanyProperty.class, ref -> switch (ref) {
            case CompanyProperty.ID -> "id";
            case CompanyProperty.NAME -> "name";
            case CompanyProperty.COUNTRY -> "country";
            case CompanyProperty.FOUNDED_YEAR -> "foundedYear";
            case CompanyProperty.DEPARTMENTS -> "departments";
        });

        // Populate test data
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();

            for (int c = 0; c < NUM_COMPANIES; c++) {
                Company company = new Company(
                        "Company-" + c,
                        c % 2 == 0 ? "USA" : "France",
                        2000 + c);

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
            }

            em.getTransaction().commit();
        }

        int totalEmployees = NUM_COMPANIES * DEPTS_PER_COMPANY * TEAMS_PER_DEPT * EMPLOYEES_PER_TEAM;
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              ULTIMATE NESTED BENCHMARK SETUP                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Companies: %d                                               ║%n", NUM_COMPANIES);
        System.out.printf("║  Departments per company: %d                                  ║%n", DEPTS_PER_COMPANY);
        System.out.printf("║  Teams per department: %d                                     ║%n", TEAMS_PER_DEPT);
        System.out.printf("║  Employees per team: %d                                       ║%n", EMPLOYEES_PER_TEAM);
        System.out.printf("║  Total employees: %d                                        ║%n", totalEmployees);
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
    }

    @AfterAll
    static void teardown() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Benchmark: Scalars + 3-Level Collections + Computed Fields")
    void benchmarkFullProjectionWithComputed() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  FULL PROJECTION: Scalars + 3-Level Collections + Computed   │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of(
                "id", "name", "country", "foundedYear", // Scalars
                "departments.id", "departments.name", "departments.budget", // Level 1
                "departments.teams.id", "departments.teams.name", "departments.teams.focus", // Level 2
                "departments.teams.employees.id", "departments.teams.employees.name",
                "departments.teams.employees.role", "departments.teams.employees.salary", // Level 3
                "employeeSummary", "totalBudgetInfo" // Computed fields
        );

        runComparison(projection, "Full + Computed");
    }

    @Test
    @Order(2)
    @DisplayName("Benchmark: Scalars + 3-Level Collections (No Computed)")
    void benchmarkFullProjectionNoComputed() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  FULL PROJECTION: Scalars + 3-Level Collections (No Computed)│");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of(
                "id", "name", "country", "foundedYear",
                "departments.id", "departments.name", "departments.budget",
                "departments.teams.id", "departments.teams.name", "departments.teams.focus",
                "departments.teams.employees.id", "departments.teams.employees.name",
                "departments.teams.employees.role", "departments.teams.employees.salary");

        runComparisonNoComputed(projection, "Full No Computed");
    }

    @Test
    @Order(3)
    @DisplayName("Benchmark: Scalars Only")
    void benchmarkScalarsOnly() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  SCALARS ONLY                                                 │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of("id", "name", "country", "foundedYear");

        runComparisonNoComputed(projection, "Scalars Only");
    }

    @Test
    @Order(4)
    @DisplayName("Benchmark: 2-Level Collections")
    void benchmark2LevelCollections() {
        System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  2-LEVEL COLLECTIONS                                          │");
        System.out.println("└──────────────────────────────────────────────────────────────┘");

        Set<String> projection = Set.of(
                "id", "name",
                "departments.id", "departments.name",
                "departments.teams.id", "departments.teams.name");

        runComparisonNoComputed(projection, "2-Level Collections");
    }

    private void runComparison(Set<String> projection, String testName) {
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

            // Verify computed fields exist
            if (!v2Result.isEmpty()) {
                assertTrue(v2Result.getFirst().containsKey("employeeSummary"),
                        "Should have employeeSummary computed field");
            }
        }
    }

    private void runComparisonNoComputed(Set<String> projection, String testName) {
        try (EntityManager em = emf.createEntityManager()) {

            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                    .combineWith("f")
                    .projection(projection)
                    .build();

            // CompanyDto has computed fields, so we need an InstanceResolver
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

    private void printResults(String testName, long[] v1Times, long[] v2Times, int v1Count, int v2Count) {
        double v1Avg = Arrays.stream(v1Times).average().orElse(0) / 1_000_000.0;
        double v2Avg = Arrays.stream(v2Times).average().orElse(0) / 1_000_000.0;
        double v1Min = Arrays.stream(v1Times).min().orElse(0) / 1_000_000.0;
        double v2Min = Arrays.stream(v2Times).min().orElse(0) / 1_000_000.0;

        double speedup = v1Avg / v2Avg;
        String status = speedup >= 1.0 ? "✓ FASTER" : "✗ SLOWER";

        System.out.printf("   V1 (original):  avg=%6.2fms, min=%6.2fms%n", v1Avg, v1Min);
        System.out.printf("   V2 (optimized): avg=%6.2fms, min=%6.2fms%n", v2Avg, v2Min);
        System.out.printf("   Speedup: %.2fx %s%n", speedup, status);
        System.out.printf("   Results: V1=%d rows, V2=%d rows%n", v1Count, v2Count);

        assertTrue(speedup >= 0.9, "V2 should not be significantly slower than V1");
    }
}

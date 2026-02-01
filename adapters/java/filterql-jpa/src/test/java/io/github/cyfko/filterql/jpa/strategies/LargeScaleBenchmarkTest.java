package io.github.cyfko.filterql.jpa.strategies;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._4.*;
import io.github.cyfko.filterql.jpa.projection.InstanceResolver;
import io.github.cyfko.filterql.jpa.projection.RowBuffer;
import org.junit.jupiter.api.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Large-scale benchmark test to stress test MultiQueryFetchStrategy V2 performance with 1000+ entities.
 * Tests include:
 * - Performance benchmarks on large datasets
 * - Multi-level nested collections (3 levels deep)
 * - Data correctness verification
 * - Computed fields with aggregations
 *
 * <h2>Data Structure</h2>
 * <pre>
 * 100 Companies
 *   └── 3 Departments each (300 total)
 *         └── 4 Teams each (1200 total)
 *               └── 5 Employees each (6000 total)
 * </pre>
 *
 * @author Frank KOSSI
 * @since 2.0.0
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

    // ==================== Performance Benchmarks ====================

    @Nested
    @DisplayName("Performance Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PerformanceBenchmarks {

        @Test
        @Order(1)
        @DisplayName("Large Scale: Scalars Only (100 companies)")
        void benchmarkScalarsOnly() {
            System.out.println("\n┌──────────────────────────────────────────────────────────────┐");
            System.out.println("│  LARGE SCALE: SCALARS ONLY (100 companies)                   │");
            System.out.println("└──────────────────────────────────────────────────────────────┘");

            Set<String> projection = Set.of("id", "name", "country", "foundedYear");

            BenchmarkResult result = runBenchmark(projection, "Scalars Only (100 companies)");

            assertEquals(NUM_COMPANIES, result.results.size(),
                    "Should return exactly " + NUM_COMPANIES + " companies");
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

            BenchmarkResult result = runBenchmark(projection, "1-Level Collection");

            assertEquals(NUM_COMPANIES, result.results.size(),
                    "Should return exactly " + NUM_COMPANIES + " companies");
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

            BenchmarkResult result = runBenchmark(projection, "2-Level Collections");

            assertEquals(NUM_COMPANIES, result.results.size(),
                    "Should return exactly " + NUM_COMPANIES + " companies");
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

            BenchmarkResult result = runBenchmark(projection, "Full 3-Level");

            assertEquals(NUM_COMPANIES, result.results.size(),
                    "Should return exactly " + NUM_COMPANIES + " companies");
        }

        @Test
        @Order(5)
        @DisplayName("Large Scale: With Computed Fields")
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
    }

    // ==================== Data Correctness Tests ====================

    @Nested
    @DisplayName("Data Correctness Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CorrectnessTests {

        @Test
        @Order(10)
        @DisplayName("Should return exact scalar field values")
        void shouldReturnExactScalarValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection(Set.of("id", "name", "country", "foundedYear"))
                        .pagination(0, 10)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(10, results.size(), "Should return 10 companies");

                for (RowBuffer row : results) {
                    String name = row.get("name").toString();
                    int companyIndex = Integer.parseInt(name.substring(8)); // "Company-X"

                    // Verify exact values match insertion logic
                    assertEquals("Company-" + companyIndex, name, "Name should match");
                    assertEquals(companyIndex % 2 == 0 ? "USA" : "France", row.get("country").toString(),
                            "Country should match for company " + companyIndex);
                    assertEquals(2000 + (companyIndex % 25), row.get("foundedYear"),
                            "Founded year should match for company " + companyIndex);
                }
            }
        }

        @Test
        @Order(11)
        @DisplayName("Should return correct number of departments per company")
        void shouldReturnCorrectDepartmentsPerCompany() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection(Set.of("name", "departments.id", "departments.name"))
                        .pagination(0, 20)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(20, results.size(), "Should return 20 companies");

                for (RowBuffer row : results) {
                    String name = row.get("name").toString();

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> departments = (List<RowBuffer>) row.get("departments");

                    assertNotNull(departments, "Departments should not be null for company: " + name);
                    assertEquals(DEPTS_PER_COMPANY, departments.size(),
                            "Company should have exactly " + DEPTS_PER_COMPANY + " departments: " + name);
                }
            }
        }

        @Test
        @Order(12)
        @DisplayName("Should return exact department values")
        void shouldReturnExactDepartmentValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection(Set.of("name", "departments.name", "departments.budget"))
                        .pagination(0, 5)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(5, results.size(), "Should return 5 companies");

                for (RowBuffer row : results) {
                    String companyName = row.get("name").toString();
                    int companyIndex = Integer.parseInt(companyName.substring(8));

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> departments = (List<RowBuffer>) row.get("departments");

                    for (int d = 0; d < departments.size(); d++) {
                        RowBuffer dept = departments.get(d);
                        String deptName = dept.get("name").toString();
                        Long budget = (Long) dept.get("budget");

                        // Verify exact values
                        String expectedDeptName = "Dept-" + companyIndex + "-" + d;
                        long expectedBudget = 100000L + d * 10000;

                        assertEquals(expectedDeptName, deptName,
                                "Department name should match for company " + companyIndex);
                        assertEquals(expectedBudget, budget,
                                "Budget should match for " + expectedDeptName);
                    }
                }
            }
        }

        @Test
        @Order(13)
        @DisplayName("Should return correct number of teams per department")
        void shouldReturnCorrectTeamsPerDepartment() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection(Set.of("name", "departments.name", "departments.teams.id"))
                        .pagination(0, 10)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(10, results.size(), "Should return 10 companies");

                for (RowBuffer row : results) {
                    @SuppressWarnings("unchecked")
                    List<RowBuffer> departments = (List<RowBuffer>) row.get("departments");

                    for (RowBuffer dept : departments) {
                        @SuppressWarnings("unchecked")
                        List<RowBuffer> teams = (List<RowBuffer>) dept.get("teams");

                        assertNotNull(teams, "Teams should not be null");
                        assertEquals(TEAMS_PER_DEPT, teams.size(),
                                "Department should have exactly " + TEAMS_PER_DEPT + " teams");
                    }
                }
            }
        }

        @Test
        @Order(14)
        @DisplayName("Should return exact team values")
        void shouldReturnExactTeamValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection(Set.of("name", "departments.name", "departments.teams.name",
                                "departments.teams.specialty"))
                        .pagination(0, 3)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(3, results.size(), "Should return 3 companies");

                for (RowBuffer row : results) {
                    String companyName = row.get("name").toString();
                    int companyIndex = Integer.parseInt(companyName.substring(8));

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> departments = (List<RowBuffer>) row.get("departments");

                    for (int d = 0; d < departments.size(); d++) {
                        @SuppressWarnings("unchecked")
                        List<RowBuffer> teams = (List<RowBuffer>) departments.get(d).get("teams");

                        for (int t = 0; t < teams.size(); t++) {
                            RowBuffer team = teams.get(t);
                            String teamName = team.get("name").toString();
                            String specialty = team.get("specialty").toString();

                            // Verify exact values
                            String expectedTeamName = "Team-" + companyIndex + "-" + d + "-" + t;
                            String expectedSpecialty = t % 2 == 0 ? "Backend" : "Frontend";

                            assertEquals(expectedTeamName, teamName, "Team name should match");
                            assertEquals(expectedSpecialty, specialty,
                                    "Specialty should match for " + expectedTeamName);
                        }
                    }
                }
            }
        }

        @Test
        @Order(15)
        @DisplayName("Should return correct number of employees per team")
        void shouldReturnCorrectEmployeesPerTeam() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection(Set.of("name",
                                "departments.teams.name",
                                "departments.teams.employees.id"))
                        .pagination(0, 5)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(5, results.size(), "Should return 5 companies");

                for (RowBuffer row : results) {
                    @SuppressWarnings("unchecked")
                    List<RowBuffer> departments = (List<RowBuffer>) row.get("departments");

                    for (RowBuffer dept : departments) {
                        @SuppressWarnings("unchecked")
                        List<RowBuffer> teams = (List<RowBuffer>) dept.get("teams");

                        for (RowBuffer team : teams) {
                            @SuppressWarnings("unchecked")
                            List<RowBuffer> employees = (List<RowBuffer>) team.get("employees");

                            assertNotNull(employees, "Employees should not be null");
                            assertEquals(EMPLOYEES_PER_TEAM, employees.size(),
                                    "Team should have exactly " + EMPLOYEES_PER_TEAM + " employees");
                        }
                    }
                }
            }
        }

        @Test
        @Order(16)
        @DisplayName("Should return exact employee values (3-level deep)")
        void shouldReturnExactEmployeeValues() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection(Set.of("name",
                                "departments.teams.employees.name",
                                "departments.teams.employees.level",
                                "departments.teams.employees.salary",
                                "departments.teams.employees.yearsExperience"))
                        .pagination(0, 2)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(2, results.size(), "Should return 2 companies");

                for (RowBuffer row : results) {
                    String companyName = row.get("name").toString();
                    int companyIndex = Integer.parseInt(companyName.substring(8));

                    @SuppressWarnings("unchecked")
                    List<RowBuffer> departments = (List<RowBuffer>) row.get("departments");

                    for (int d = 0; d < departments.size(); d++) {
                        @SuppressWarnings("unchecked")
                        List<RowBuffer> teams = (List<RowBuffer>) departments.get(d).get("teams");

                        for (int t = 0; t < teams.size(); t++) {
                            @SuppressWarnings("unchecked")
                            List<RowBuffer> employees = (List<RowBuffer>) teams.get(t).get("employees");

                            for (int e = 0; e < employees.size(); e++) {
                                RowBuffer emp = employees.get(e);

                                String empName = emp.get("name").toString();
                                String level = emp.get("level").toString();
                                BigDecimal salary = (BigDecimal) emp.get("salary");
                                Integer yearsExp = (Integer) emp.get("yearsExperience");

                                // Verify exact values
                                String expectedName = "Employee-" + companyIndex + "-" + d + "-" + t + "-" + e;
                                String expectedLevel = e % 3 == 0 ? "Senior" : (e % 3 == 1 ? "Mid" : "Junior");
                                BigDecimal expectedSalary = BigDecimal.valueOf(50000 + e * 5000);
                                int expectedYearsExp = e + 1;

                                assertEquals(expectedName, empName, "Employee name should match");
                                assertEquals(expectedLevel, level, "Level should match for " + expectedName);
                                assertEquals(expectedSalary, salary, "Salary should match for " + expectedName);
                                assertEquals(expectedYearsExp, yearsExp,
                                        "Years experience should match for " + expectedName);
                            }
                        }
                    }
                }
            }
        }

        @Test
        @Order(17)
        @DisplayName("Should maintain data integrity with pagination")
        void shouldMaintainDataIntegrityWithPagination() {
            try (EntityManager em = emf.createEntityManager()) {
                // Get companies 50-54
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                        .combineWith("f")
                        .projection("name", "country", "foundedYear", "departments.name")
                        .pagination(10, 5)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(5, results.size(), "Should return 5 companies");

                for (int i = 0; i < results.size(); i++) {
                    RowBuffer row = results.get(i);
                    int expectedCompanyIndex = 50 + i;

                    // Verify scalar fields
                    assertEquals("Company-" + expectedCompanyIndex, row.get("name").toString());
                    assertEquals(expectedCompanyIndex % 2 == 0 ? "USA" : "France", row.get("country").toString());
                    assertEquals(2000 + (expectedCompanyIndex % 25), row.get("foundedYear"));

                    // Verify collection
                    @SuppressWarnings("unchecked")
                    List<RowBuffer> departments = (List<RowBuffer>) row.get("departments");
                    assertEquals(DEPTS_PER_COMPANY, departments.size());
                }
            }
        }

        @Test
        @Order(18)
        @DisplayName("Should verify computed fields contain valid data")
        void shouldVerifyComputedFieldsContainValidData() {
            try (EntityManager em = emf.createEntityManager()) {
                FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                        .filter("f", CompanyProperty.NAME, "MATCHES", "Company%")
                        .combineWith("f")
                        .projection(Set.of("name", "employeeSummary", "totalBudgetInfo"))
                        .pagination(0, 5)
                        .build();

                InstanceResolver resolver = InstanceResolver.noBean();
                MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

                List<RowBuffer> results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);

                assertEquals(5, results.size(), "Should return 5 companies");

                for (RowBuffer row : results) {
                    String companyName = row.get("name").toString();

                    // Verify computed fields are present
                    assertTrue(row.contains("employeeSummary"),
                            "Should have employeeSummary computed field for " + companyName);
                    assertTrue(row.contains("totalBudgetInfo"),
                            "Should have totalBudgetInfo computed field for " + companyName);

                    // Verify computed fields are not null
                    assertNotNull(row.get("employeeSummary"),
                            "employeeSummary should not be null for " + companyName);
                    assertNotNull(row.get("totalBudgetInfo"),
                            "totalBudgetInfo should not be null for " + companyName);

                    System.out.printf("   %s: employeeSummary=%s, totalBudgetInfo=%s%n",
                            companyName, row.get("employeeSummary"), row.get("totalBudgetInfo"));
                }
            }
        }
    }

    // ==================== Benchmark Utilities ====================

    private BenchmarkResult runBenchmark(Set<String> projection, String testName) {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "LIKE", "Company%")
                    .combineWith("f")
                    .projection(projection)
                    .pagination(0, NUM_COMPANIES)
                    .build();

            InstanceResolver resolver = InstanceResolver.noBean();
            MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                FilterQueryFactory.of(filterContext).execute(request, em, strategy);
            }

            // Benchmark
            long[] times = new long[BENCHMARK_ITERATIONS];
            List<RowBuffer> results = null;

            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);
                times[i] = System.nanoTime() - start;
            }

            printResults(testName, times, results.size());

            return new BenchmarkResult(results, Arrays.stream(times).average().orElse(0));
        }
    }

    private void runBenchmarkWithComputed(Set<String> projection, String testName) {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "MATCHES", "Company%")
                    .combineWith("f")
                    .projection(projection)
                    .pagination(0, NUM_COMPANIES)
                    .build();

            InstanceResolver resolver = InstanceResolver.noBean();
            MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(CompanyDto.class, resolver);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                FilterQueryFactory.of(filterContext).execute(request, em, strategy);
            }

            // Benchmark
            long[] times = new long[BENCHMARK_ITERATIONS];
            List<RowBuffer> results = null;

            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                results = FilterQueryFactory.of(filterContext).execute(request, em, strategy);
                times[i] = System.nanoTime() - start;
            }

            // Print results
            double avg = Arrays.stream(times).average().orElse(0) / 1_000_000.0;
            double min = Arrays.stream(times).min().orElse(0) / 1_000_000.0;
            double max = Arrays.stream(times).max().orElse(0) / 1_000_000.0;

            System.out.printf("   Results with aggregates: avg=%8.2fms, min=%8.2fms, max=%8.2fms%n", avg, min, max);
            System.out.printf("   Results: %d companies with computed fields%n", results.size());

            // Verify computed fields are present and contain values
            assertFalse(results.isEmpty(), "Should have results");
            RowBuffer first = results.getFirst();
            assertTrue(first.contains("employeeSummary"), "Should have employeeSummary computed field");
            assertTrue(first.contains("totalBudgetInfo"), "Should have totalBudgetInfo computed field");

            // Print sample computed values
            System.out.printf("   Sample: employeeSummary=%s, totalBudgetInfo=%s%n",
                    first.get("employeeSummary"), first.get("totalBudgetInfo"));
        }
    }

    private void printResults(String testName, long[] times, int resultCount) {
        double avg = Arrays.stream(times).average().orElse(0) / 1_000_000.0;
        double min = Arrays.stream(times).min().orElse(0) / 1_000_000.0;
        double max = Arrays.stream(times).max().orElse(0) / 1_000_000.0;

        System.out.printf("   Results stats: avg=%8.2fms, min=%8.2fms, max=%8.2fms%n", avg, min, max);
        System.out.printf("   Results: %d rows%n", resultCount);
    }

    private record BenchmarkResult(
            List<RowBuffer> results,
            double avgNanos) {
    }
}
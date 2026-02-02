package io.github.cyfko.filterql.jpa.projection;

import io.github.cyfko.filterql.core.FilterQueryFactory;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.utils.OperatorUtils;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.JpaFilterContext;
import io.github.cyfko.filterql.jpa.entities.projection._4.*;
import io.github.cyfko.filterql.jpa.strategies.MultiQueryFetchStrategy;
import io.github.cyfko.filterql.jpa.spi.InstanceResolver;
import io.github.cyfko.filterql.jpa.strategies.helper.RowBuffer;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that computed fields with reducers aggregate PER ROW (GROUP BY parent
 * ID)
 * rather than globally across all results.
 *
 * This is a critical correctness test to ensure that:
 * - Company A with 5 employees → employeeSummary shows "5 employees"
 * - Company B with 3 employees → employeeSummary shows "3 employees"
 *
 * And NOT the same global count for all rows.
 */
@DisplayName("Per-Row Aggregation Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PerRowAggregationTest {

    private static EntityManagerFactory emf;
    private static JpaFilterContext<CompanyProperty> filterContext;

    // Expected data - deterministic for verification
    private static final Map<String, Integer> EXPECTED_EMPLOYEE_COUNTS = new LinkedHashMap<>();
    private static final Map<String, Integer> EXPECTED_BUDGET_SUMS = new LinkedHashMap<>();

    enum CompanyProperty implements PropertyReference {
        NAME, COUNTRY;

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
            return Company.class;
        }
    }

    @BeforeAll
    static void setup() {
        emf = Persistence.createEntityManagerFactory("testPU");
        filterContext = new JpaFilterContext<>(CompanyProperty.class, prop -> switch (prop) {
            case NAME -> "name";
            case COUNTRY -> "country";
        });

        // Setup expected values
        EXPECTED_EMPLOYEE_COUNTS.put("SmallCorp", 2); // 1 dept × 1 team × 2 employees
        EXPECTED_EMPLOYEE_COUNTS.put("MediumCorp", 6); // 2 dept × 1 team × 3 employees
        EXPECTED_EMPLOYEE_COUNTS.put("BigCorp", 12); // 3 dept × 2 teams × 2 employees

        EXPECTED_BUDGET_SUMS.put("SmallCorp", 100); // 1 dept × budget=100
        EXPECTED_BUDGET_SUMS.put("MediumCorp", 500); // 2 depts × budget=250
        EXPECTED_BUDGET_SUMS.put("BigCorp", 900); // 3 depts × budget=300

        insertTestData();
    }

    @AfterAll
    static void teardown() {
        if (emf != null) {
            cleanupTestData();
            emf.close();
        }
    }

    /**
     * Inserts test data with DIFFERENT numbers of employees and budgets per
     * company.
     */
    private static void insertTestData() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        // Company 1: SmallCorp - 1 department, 1 team, 2 employees, budget=100
        Company small = new Company("SmallCorp", "France", 2020);
        Department smallDept = new Department("SmallDept", 100L);
        Team smallTeam = new Team("SmallTeam", "Engineering");
        smallTeam.addEmployee(new Employee("Alice", "Junior", BigDecimal.valueOf(50000), 1));
        smallTeam.addEmployee(new Employee("Bob", "Senior", BigDecimal.valueOf(55000), 5));
        smallDept.addTeam(smallTeam);
        small.addDepartment(smallDept);
        em.persist(small);

        // Company 2: MediumCorp - 2 departments, 1 team each, 3 employees each,
        // budget=250 each
        Company medium = new Company("MediumCorp", "Germany", 2015);
        for (int d = 0; d < 2; d++) {
            Department dept = new Department("MedDept" + d, 250L);
            Team team = new Team("MedTeam" + d, "Development");
            for (int e = 0; e < 3; e++) {
                team.addEmployee(new Employee("MedEmp" + d + "_" + e, "Mid", BigDecimal.valueOf(60000), 3));
            }
            dept.addTeam(team);
            medium.addDepartment(dept);
        }
        em.persist(medium);

        // Company 3: BigCorp - 3 departments, 2 teams each, 2 employees each,
        // budget=300 each
        Company big = new Company("BigCorp", "USA", 2000);
        for (int d = 0; d < 3; d++) {
            Department dept = new Department("BigDept" + d, 300L);
            for (int t = 0; t < 2; t++) {
                Team team = new Team("BigTeam" + d + "_" + t, "Production");
                for (int e = 0; e < 2; e++) {
                    team.addEmployee(new Employee("BigEmp" + d + "_" + t + "_" + e,
                            "Senior", BigDecimal.valueOf(70000), 7));
                }
                dept.addTeam(team);
            }
            big.addDepartment(dept);
        }
        em.persist(big);

        em.getTransaction().commit();
        em.close();

        System.out.println("✅ Inserted 3 companies with different employee counts: " + EXPECTED_EMPLOYEE_COUNTS);
    }

    private static void cleanupTestData() {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.createQuery("DELETE FROM Employee").executeUpdate();
            em.createQuery("DELETE FROM Team").executeUpdate();
            em.createQuery("DELETE FROM Department").executeUpdate();
            em.createQuery("DELETE FROM Company").executeUpdate();
            em.getTransaction().commit();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Each company should have its OWN employee count, not a global sum")
    void shouldAggregateEmployeeCountPerCompany() {
        try (EntityManager em = emf.createEntityManager()) {
            // Query all companies with employeeSummary computed field
            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "LIKE", "%Corp")
                    .combineWith("f")
                    .projection("id", "name", "employeeSummary")
                    .build();

            MultiQueryFetchStrategy fetchStrategy = new MultiQueryFetchStrategy(
                    CompanyDto.class, InstanceResolver.noBean());

            List<RowBuffer> results = FilterQueryFactory.of(filterContext)
                    .execute(request, em, fetchStrategy);

            assertEquals(3, results.size(), "Should have 3 companies");

            // Verify each company has its OWN count
            for (RowBuffer row : results) {
                String name = (String) row.get("name");
                String summary = (String) row.get("employeeSummary");

                assertNotNull(summary, "Employee summary should not be null for " + name);

                // Extract employee count from summary format: "id-name[X employees]"
                int actualCount = extractEmployeeCount(summary);
                int expectedCount = EXPECTED_EMPLOYEE_COUNTS.get(name);

                assertEquals(expectedCount, actualCount,
                        "Company " + name + " should have " + expectedCount + " employees, " +
                                "but summary shows: " + summary);

                System.out.println("✅ " + name + " → " + actualCount + " employees (expected: " + expectedCount + ")");
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Each company should have its OWN budget sum, not a global sum")
    void shouldAggregateBudgetSumPerCompany() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "LIKE", "%Corp")
                    .combineWith("f")
                    .projection("id", "name", "totalBudgetInfo")
                    .build();

            MultiQueryFetchStrategy fetchStrategy = new MultiQueryFetchStrategy(
                    CompanyDto.class, InstanceResolver.noBean());

            List<RowBuffer> results = FilterQueryFactory.of(filterContext)
                    .execute(request, em, fetchStrategy);

            assertEquals(3, results.size(), "Should have 3 companies");

            for (RowBuffer row : results) {
                String name = (String) row.get("name");
                String budgetInfo = (String) row.get("totalBudgetInfo");

                assertNotNull(budgetInfo, "Budget info should not be null for " + name);

                // Extract budget from format: "Company#id: budget=X"
                int actualBudget = extractBudget(budgetInfo);
                int expectedBudget = EXPECTED_BUDGET_SUMS.get(name);

                assertEquals(expectedBudget, actualBudget,
                        "Company " + name + " should have budget " + expectedBudget + ", " +
                                "but budgetInfo shows: " + budgetInfo);

                System.out.println("✅ " + name + " → budget=" + actualBudget + " (expected: " + expectedBudget + ")");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Aggregations should NOT be the same for all rows (global aggregate)")
    void shouldNotHaveGlobalAggregateForAllRows() {
        try (EntityManager em = emf.createEntityManager()) {
            FilterRequest<CompanyProperty> request = FilterRequest.<CompanyProperty>builder()
                    .filter("f", CompanyProperty.NAME, "LIKE", "%Corp")
                    .combineWith("f")
                    .projection("id", "name", "employeeSummary", "totalBudgetInfo")
                    .build();

            MultiQueryFetchStrategy fetchStrategy = new MultiQueryFetchStrategy(
                    CompanyDto.class, InstanceResolver.noBean());

            List<RowBuffer> results = FilterQueryFactory.of(filterContext)
                    .execute(request, em, fetchStrategy);

            // Collect all employee counts and budgets
            Set<Integer> uniqueEmployeeCounts = new HashSet<>();
            Set<Integer> uniqueBudgets = new HashSet<>();

            for (RowBuffer row : results) {
                String name = (String) row.get("name");
                String summary = (String) row.get("employeeSummary");
                String budgetInfo = (String) row.get("totalBudgetInfo");

                System.out.println("DEBUG Test3: " + name +
                        " -> employeeSummary = " + summary +
                        ", totalBudgetInfo = " + budgetInfo);

                uniqueEmployeeCounts.add(extractEmployeeCount(summary));
                uniqueBudgets.add(extractBudget(budgetInfo));
            }

            // If aggregation was global, all companies would have the same value
            assertTrue(uniqueEmployeeCounts.size() > 1,
                    "Employee counts should differ between companies! " +
                            "If all are the same, aggregation might be global instead of per-row. " +
                            "Found: " + uniqueEmployeeCounts);

            assertTrue(uniqueBudgets.size() > 1,
                    "Budget sums should differ between companies! " +
                            "If all are the same, aggregation might be global instead of per-row. " +
                            "Found: " + uniqueBudgets);

            System.out.println("✅ Unique employee counts: " + uniqueEmployeeCounts);
            System.out.println("✅ Unique budgets: " + uniqueBudgets);
        }
    }

    // Helper to extract employee count from "id-name[X employees]"
    private int extractEmployeeCount(String summary) {
        int start = summary.indexOf('[') + 1;
        int end = summary.indexOf(' ', start);
        return Integer.parseInt(summary.substring(start, end));
    }

    // Helper to extract budget from "Company#id: budget=X"
    private int extractBudget(String budgetInfo) {
        int start = budgetInfo.indexOf("budget=") + 7;
        return Integer.parseInt(budgetInfo.substring(start));
    }
}

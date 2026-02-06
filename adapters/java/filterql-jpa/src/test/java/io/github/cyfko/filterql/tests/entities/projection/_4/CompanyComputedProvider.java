package io.github.cyfko.filterql.tests.entities.projection._4;

/**
 * Provider class for computed fields in CompanyDto.
 * 
 * Methods receive aggregated scalar values from SQL reducers:
 * - COUNT returns Long
 * - SUM returns Long (regardless of source field type)
 * 
 * IMPORTANT: Use boxed types (Long, Integer) instead of primitives
 * to handle null values from aggregations.
 */
public class CompanyComputedProvider {

    /**
     * Computes employee summary from aggregated count.
     *
     * @param id            company id
     * @param name          company name
     * @param employeeCount aggregated COUNT of employees.id (may be null if no
     *                      results)
     * @return summary string
     */
    public static String getEmployeeSummary(Long id, String name, Long employeeCount) {
        long count = employeeCount != null ? employeeCount : 0L;
        return String.format("%d-%s[%d employees]", id, name, count);
    }

    /**
     * Computes total budget info from aggregated sum.
     * SUM on integer fields returns Long in JPA/SQL.
     *
     * @param id          company id
     * @param totalBudget aggregated SUM of departments.budget (may be null)
     * @return formatted budget string
     */
    public static String getTotalBudgetInfo(Long id, Long totalBudget) {
        long budget = totalBudget != null ? totalBudget : 0L;
        return String.format("Company#%d: budget=%d", id, budget);
    }
}

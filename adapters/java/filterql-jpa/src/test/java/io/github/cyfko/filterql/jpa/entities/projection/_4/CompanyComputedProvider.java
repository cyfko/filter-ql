package io.github.cyfko.filterql.jpa.entities.projection._4;

import java.util.List;

/**
 * Provider class for computed fields in CompanyDto.
 * Contains methods that compute values from nested collection data.
 */
public class CompanyComputedProvider {

    /**
     * Computes total employee count across all departments, teams.
     * This is computed from Level 3 collection data.
     *
     * @param id          company id
     * @param name        company name
     * @param departments list of departments (contains nested teams/employees)
     * @return summary string with total counts
     */
    public String getEmployeeSummary(Long id, String name, List<Department> departments) {
        // In a real scenario, we'd traverse the nested structure
        // Here we just count the top-level departments
        int deptCount = departments != null ? departments.size() : 0;
        return String.format("%d-%s[%d depts]", id, name, deptCount);
    }

    /**
     * Computes total budget across all departments.
     *
     * @param id          company id
     * @param departments list of department data
     * @return formatted budget string
     */
    public String getTotalBudgetInfo(Long id, List<Department> departments) {
        // Budget would be computed from department data
        int deptCount = departments != null ? departments.size() : 0;
        return String.format("Company#%d: %d departments", id, deptCount);
    }
}

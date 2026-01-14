package io.github.cyfko.filterql.jpa.entities.projection._4;

import io.github.cyfko.projection.*;
import java.util.List;

/**
 * DTO for Company with 3-level nested collections and computed fields.
 * 
 * Structure:
 * - Company (Level 0) -> scalars + computed
 * - Department (Level 1) -> scalars
 * - Team (Level 2) -> scalars
 * - Employee (Level 3) -> scalars
 */
@Projection(from = Company.class, providers = @Provider(CompanyComputedProvider.class))
public class CompanyDto {

    // Scalar fields
    @Projected(from = "id")
    private Long id;

    @Projected(from = "name")
    private String name;

    @Projected(from = "country")
    private String country;

    @Projected(from = "foundedYear")
    private int foundedYear;

    // Level 1 nested collection
    @Projected(from = "departments")
    private List<DepartmentDto> departments;

    // Computed field using COUNT reducer on nested collection
    @Computed(dependsOn = { "id", "name", "departments.teams.employees.id" }, reducers = { Computed.Reduce.COUNT })
    private String employeeSummary;

    // Another computed field using SUM reducer
    @Computed(dependsOn = { "id", "departments.budget" }, reducers = { Computed.Reduce.SUM })
    private String totalBudgetInfo;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getFoundedYear() {
        return foundedYear;
    }

    public void setFoundedYear(int foundedYear) {
        this.foundedYear = foundedYear;
    }

    public List<DepartmentDto> getDepartments() {
        return departments;
    }

    public void setDepartments(List<DepartmentDto> departments) {
        this.departments = departments;
    }

    public String getEmployeeSummary() {
        return employeeSummary;
    }

    public void setEmployeeSummary(String employeeSummary) {
        this.employeeSummary = employeeSummary;
    }

    public String getTotalBudgetInfo() {
        return totalBudgetInfo;
    }

    public void setTotalBudgetInfo(String totalBudgetInfo) {
        this.totalBudgetInfo = totalBudgetInfo;
    }
}

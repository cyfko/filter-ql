package io.github.cyfko.filterql.tests.entities.projection._4;

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
public interface CompanyDto {

    // Scalar fields
    @Projected(from = "id")
    Long getId();

    @Projected(from = "name")
    String getName();

    @Projected(from = "country")
    String getCountry();

    @Projected(from = "foundedYear")
    int getFoundedYear();

    // Level 1 nested collection
    @Projected(from = "departments")
    List<DepartmentDto> getDepartments();

    // Computed field using COUNT reducer on nested collection
    @Computed(dependsOn = { "id", "name", "departments.teams.employees.id" }, reducers = { Computed.Reduce.COUNT })
    String getEmployeeSummary();

    // Another computed field using SUM reducer
    @Computed(dependsOn = { "id", "departments.budget" }, reducers = { Computed.Reduce.SUM })
    String getTotalBudgetInfo();
}

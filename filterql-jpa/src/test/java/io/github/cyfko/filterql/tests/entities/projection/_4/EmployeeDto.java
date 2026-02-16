package io.github.cyfko.filterql.tests.entities.projection._4;

import io.github.cyfko.projection.*;
import java.math.BigDecimal;

/**
 * DTO for Employee - Level 3 (Leaf).
 */
@Projection(from = Employee.class)
public interface EmployeeDto {

    @Projected(from = "id")
    Long getId();

    @Projected(from = "name")
    String getName();

    @Projected(from = "role")
    String getRole();

    @Projected(from = "salary")
    BigDecimal getSalary();

    @Projected(from = "yearsOfExperience")
    int getYearsOfExperience();
}

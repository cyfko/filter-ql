package io.github.cyfko.filterql.tests.entities.projection._4;

import io.github.cyfko.projection.*;
import java.util.List;

/**
 * DTO for Team - Level 2.
 */
@Projection(from = Team.class)
public interface TeamDto {

    @Projected(from = "id")
    Long getId();

    @Projected(from = "name")
    String getName();

    @Projected(from = "focus")
    String getFocus();

    // Level 3 nested collection (deepest level)
    @Projected(from = "employees")
    List<EmployeeDto> getEmployees();
}

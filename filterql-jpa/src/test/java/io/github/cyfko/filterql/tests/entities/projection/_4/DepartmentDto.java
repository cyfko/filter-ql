package io.github.cyfko.filterql.tests.entities.projection._4;

import io.github.cyfko.projection.*;
import java.util.List;

/**
 * DTO for Department - Level 1.
 */
@Projection(from = Department.class)
public interface DepartmentDto {

    @Projected(from = "id")
    Long getId();

    @Projected(from = "name")
    String getName();

    @Projected(from = "budget")
    int getBudget();

    // Level 2 nested collection
    @Projected(from = "teams")
    List<TeamDto> getTeams();
}

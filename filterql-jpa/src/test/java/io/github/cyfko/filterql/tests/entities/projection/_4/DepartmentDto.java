package io.github.cyfko.filterql.tests.entities.projection._4;

import io.github.cyfko.projection.*;
import java.util.List;

/**
 * DTO for Department - Level 1.
 */
@Projection(from = Department.class)
public class DepartmentDto {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "name")
    private String name;

    @Projected(from = "budget")
    private int budget;

    // Level 2 nested collection
    @Projected(from = "teams")
    private List<TeamDto> teams;

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

    public int getBudget() {
        return budget;
    }

    public void setBudget(int budget) {
        this.budget = budget;
    }

    public List<TeamDto> getTeams() {
        return teams;
    }

    public void setTeams(List<TeamDto> teams) {
        this.teams = teams;
    }
}

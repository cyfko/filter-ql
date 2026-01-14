package io.github.cyfko.filterql.jpa.entities.projection._4;

import io.github.cyfko.projection.*;
import java.util.List;

/**
 * DTO for Team - Level 2.
 */
@Projection(from = Team.class)
public class TeamDto {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "name")
    private String name;

    @Projected(from = "focus")
    private String focus;

    // Level 3 nested collection (deepest level)
    @Projected(from = "employees")
    private List<EmployeeDto> employees;

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

    public String getFocus() {
        return focus;
    }

    public void setFocus(String focus) {
        this.focus = focus;
    }

    public List<EmployeeDto> getEmployees() {
        return employees;
    }

    public void setEmployees(List<EmployeeDto> employees) {
        this.employees = employees;
    }
}

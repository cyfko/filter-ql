package io.github.cyfko.filterql.jpa.entities.projection._4;

import io.github.cyfko.projection.*;
import java.math.BigDecimal;

/**
 * DTO for Employee - Level 3 (Leaf).
 */
@Projection(from = Employee.class)
public class EmployeeDto {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "name")
    private String name;

    @Projected(from = "role")
    private String role;

    @Projected(from = "salary")
    private BigDecimal salary;

    @Projected(from = "yearsOfExperience")
    private int yearsOfExperience;

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public int getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(int yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }
}

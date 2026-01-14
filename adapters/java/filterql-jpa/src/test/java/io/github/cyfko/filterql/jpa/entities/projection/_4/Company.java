package io.github.cyfko.filterql.jpa.entities.projection._4;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Company entity - Level 0 (Root)
 * Has departments (Level 1) -> teams (Level 2) -> employees (Level 3)
 */
@Entity
@Table(name = "company_benchmark")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String country;

    private int foundedYear;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Department> departments = new ArrayList<>();

    public Company() {
    }

    public Company(String name, String country, int foundedYear) {
        this.name = name;
        this.country = country;
        this.foundedYear = foundedYear;
    }

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

    public List<Department> getDepartments() {
        return departments;
    }

    public void setDepartments(List<Department> departments) {
        this.departments = departments;
    }

    public void addDepartment(Department dept) {
        departments.add(dept);
        dept.setCompany(this);
    }
}

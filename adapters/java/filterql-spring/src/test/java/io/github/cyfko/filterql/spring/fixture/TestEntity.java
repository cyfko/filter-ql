package io.github.cyfko.filterql.spring.fixture;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Simple test entity for integration testing.
 * <p>
 * This entity is used to verify:
 * - PropertyRef enum generation
 * - FilterContext bean registration
 * - Controller generation (when exposed = true)
 * </p>
 */
@Entity
public class TestEntity {

    @Id
    private Long id;

    private String name;

    private Integer age;

    private Boolean active;

    // Constructors
    public TestEntity() {}

    public TestEntity(Long id, String name, Integer age, Boolean active) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.active = active;
    }

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

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

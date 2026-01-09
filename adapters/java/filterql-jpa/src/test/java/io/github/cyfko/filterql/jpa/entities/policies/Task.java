package io.github.cyfko.filterql.jpa.entities.policies;


import jakarta.persistence.*;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    public Task() {}

    public Task(String name, Priority priority) {
        this.name = name;
        this.priority = priority;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Priority getPriority() { return priority; }


    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
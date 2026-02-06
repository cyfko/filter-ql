package io.github.cyfko.filterql.tests.entities.policies;

import jakarta.persistence.*;

@Entity
@Table(name = "test_items")
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Integer quantity;

    public Item() {}
    public Item(String name, Integer quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Integer getQuantity() { return quantity; }
}
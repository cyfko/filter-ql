package io.github.cyfko.filterql.jpa.entities.basic_operators;

import jakarta.persistence.*;

@Entity
@Table(name = "products_b")
public class ProductB {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Integer price;

    public ProductB() {}
    public ProductB(String name, Integer price) {
        this.name = name;
        this.price = price;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
}
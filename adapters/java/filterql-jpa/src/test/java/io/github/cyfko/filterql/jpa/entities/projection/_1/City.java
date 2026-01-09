package io.github.cyfko.filterql.jpa.entities.projection._1;

import jakarta.persistence.*;

@Entity
@Table(name = "test_projection_cities")
public class City {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String zipCode;

    public City() {}

    public City(String name, String zipCode) {
        this.name = name;
        this.zipCode = zipCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
}
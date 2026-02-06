package io.github.cyfko.filterql.tests.entities.projection._1;

import jakarta.persistence.*;

@Entity
@Table(name = "test_projection_addresses")
public class AddressB {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "city_id")
    private City city;

    public AddressB() {}

    public AddressB(City city) {
        this.city = city;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }
}
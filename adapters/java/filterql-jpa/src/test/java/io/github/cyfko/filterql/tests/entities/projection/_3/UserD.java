package io.github.cyfko.filterql.tests.entities.projection._3;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_users_phase2")
public class UserD {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderD> orders = new ArrayList<>();

    public UserD() {}

    public UserD(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<OrderD> getOrders() { return orders; }
    public void setOrders(List<OrderD> orders) { this.orders = orders; }

    public void addOrder(OrderD order) {
        orders.add(order);
        order.setUser(this);
    }
}
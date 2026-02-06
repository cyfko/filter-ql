package io.github.cyfko.filterql.tests.entities.projection._2;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_users_c")
public class UserC {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderC> orders = new ArrayList<>();

    public UserC() {}
    public UserC(String name, String email) { this.name = name; this.email = email; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public List<OrderC> getOrders() { return orders; }
    public void addOrder(OrderC order) { orders.add(order); order.setUser(this); }
}
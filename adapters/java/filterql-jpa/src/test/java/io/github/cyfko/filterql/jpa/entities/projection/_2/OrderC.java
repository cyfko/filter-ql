package io.github.cyfko.filterql.jpa.entities.projection._2;

import jakarta.persistence.*;

@Entity
@Table(name = "test_orders_c")
public class OrderC {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderNumber;
    private double amount;

    @ManyToOne
    private UserC user;

    public OrderC() {}
    public OrderC(String orderNumber, double amount) { this.orderNumber = orderNumber; this.amount = amount; }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public double getAmount() { return amount; }
    public UserC getUser() { return user; }
    public void setUser(UserC user) { this.user = user; }
}
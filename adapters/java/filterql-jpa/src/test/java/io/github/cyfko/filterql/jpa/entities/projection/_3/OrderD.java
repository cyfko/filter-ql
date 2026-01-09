package io.github.cyfko.filterql.jpa.entities.projection._3;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_orders_phase2")
public class OrderD {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNumber;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserD user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItemD> items = new ArrayList<>();

    public OrderD() {}

    public OrderD(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public UserD getUser() { return user; }
    public void setUser(UserD user) { this.user = user; }
    public List<OrderItemD> getItems() { return items; }
    public void setItems(List<OrderItemD> items) { this.items = items; }

    public void addItem(OrderItemD item) {
        items.add(item);
        item.setOrder(this);
    }
}
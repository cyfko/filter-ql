package io.github.cyfko.filterql.jpa.entities.projection._3;

import jakarta.persistence.*;

@Entity
@Table(name = "test_order_items_phase2")
public class OrderItemD {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;
    private int quantity;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private OrderD order;

    public OrderItemD() {}

    public OrderItemD(String productName, int quantity) {
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public OrderD getOrder() { return order; }
    public void setOrder(OrderD order) { this.order = order; }
}
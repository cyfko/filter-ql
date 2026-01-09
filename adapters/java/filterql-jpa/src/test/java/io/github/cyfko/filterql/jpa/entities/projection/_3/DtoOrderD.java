package io.github.cyfko.filterql.jpa.entities.projection._3;

import io.github.cyfko.projection.*;

import java.util.ArrayList;
import java.util.List;

@Projection(entity = OrderD.class)
public class DtoOrderD {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "orderNumber")
    private String number;

    @Projected(from = "items")
    private List<DtoOrderItemD> orderItems = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public List<DtoOrderItemD> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<DtoOrderItemD> orderItems) {
        this.orderItems = orderItems;
    }
}

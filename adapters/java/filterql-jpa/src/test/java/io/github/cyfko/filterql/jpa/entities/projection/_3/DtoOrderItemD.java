package io.github.cyfko.filterql.jpa.entities.projection._3;

import io.github.cyfko.projection.Projection;

@Projection(entity = OrderItemD.class)
public class DtoOrderItemD {

    private String productName;

    private int quantity;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

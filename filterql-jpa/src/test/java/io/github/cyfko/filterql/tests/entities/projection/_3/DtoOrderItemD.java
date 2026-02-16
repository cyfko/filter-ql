package io.github.cyfko.filterql.tests.entities.projection._3;

import io.github.cyfko.projection.Projection;

@Projection(from = OrderItemD.class)
public interface DtoOrderItemD {

    String getProductName();

    int getQuantity();
}

package io.github.cyfko.filterql.tests.entities.projection._3;

import io.github.cyfko.projection.*;

import java.util.ArrayList;
import java.util.List;

@Projection(from = OrderD.class)
public interface DtoOrderD {

    @Projected(from = "id")
    Long getId();

    @Projected(from = "orderNumber")
    String getNumber();

    @Projected(from = "items")
    List<DtoOrderItemD> getOrderItems();
}

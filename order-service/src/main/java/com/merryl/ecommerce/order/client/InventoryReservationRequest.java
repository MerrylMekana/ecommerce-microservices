package com.merryl.ecommerce.order.client;

import java.util.List;

public record InventoryReservationRequest(
        String reservationId,
        String skuCode,
        Integer quantity,
        List<InventoryReservationItemRequest> items) {

    public InventoryReservationRequest(String reservationId, String skuCode, Integer quantity) {
        this(reservationId, skuCode, quantity, null);
    }

    public InventoryReservationRequest(String reservationId, List<InventoryReservationItemRequest> items) {
        this(reservationId, null, null, items);
    }
}

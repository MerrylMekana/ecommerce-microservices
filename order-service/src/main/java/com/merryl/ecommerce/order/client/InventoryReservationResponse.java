package com.merryl.ecommerce.order.client;

public record InventoryReservationResponse(
        String reservationId,
        String status,
        String code,
        String message) {
}

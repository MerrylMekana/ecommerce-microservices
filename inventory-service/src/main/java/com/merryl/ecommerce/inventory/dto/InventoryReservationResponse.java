package com.merryl.ecommerce.inventory.dto;

public record InventoryReservationResponse(
        String reservationId,
        String status,
        String code,
        String message) {
}

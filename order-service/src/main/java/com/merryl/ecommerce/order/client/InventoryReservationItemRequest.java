package com.merryl.ecommerce.order.client;

public record InventoryReservationItemRequest(String skuCode, Integer quantity) {
}

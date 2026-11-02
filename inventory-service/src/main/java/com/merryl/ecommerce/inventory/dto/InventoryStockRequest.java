package com.merryl.ecommerce.inventory.dto;

public record InventoryStockRequest(String skuCode, Integer quantity) {
}

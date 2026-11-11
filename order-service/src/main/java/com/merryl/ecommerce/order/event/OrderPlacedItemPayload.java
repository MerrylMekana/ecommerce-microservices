package com.merryl.ecommerce.order.event;

public record OrderPlacedItemPayload(String skuCode, String unitPrice, int quantity) {
}

package com.merryl.ecommerce.order.dto;

import java.math.BigDecimal;

public record OrderItemRequest(String skuCode, BigDecimal price, Integer quantity) {
}

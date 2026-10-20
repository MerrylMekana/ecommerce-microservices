package com.merryl.ecommerce.order.dto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record OrderRequest(Long id, String orderNumber, String skuCode,
                           BigDecimal price, Integer quantity, UserDetails userDetails,
                           List<OrderItemRequest> items) {

    public OrderRequest(Long id, String orderNumber, String skuCode, BigDecimal price,
                        Integer quantity, UserDetails userDetails) {
        this(id, orderNumber, skuCode, price, quantity, userDetails, null);
    }

    public List<OrderItemRequest> canonicalItems() {
        boolean hasLegacyItem = skuCode != null || price != null || quantity != null;
        boolean hasItemList = items != null;
        if (hasLegacyItem == hasItemList) {
            throw new IllegalArgumentException("Provide exactly one of legacy item fields or items");
        }

        List<OrderItemRequest> normalized = hasItemList
                ? new ArrayList<>(items)
                : List.of(new OrderItemRequest(skuCode, price, quantity));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        if (normalized.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("items must not contain null entries");
        }
        if (normalized.stream().anyMatch(item -> item.skuCode() == null || item.skuCode().isBlank())) {
            throw new IllegalArgumentException("skuCode must not be blank");
        }
        if (normalized.stream().anyMatch(item -> item.skuCode().length() > 255)) {
            throw new IllegalArgumentException("skuCode must not exceed 255 characters");
        }
        if (normalized.stream().anyMatch(item -> item.quantity() == null || item.quantity() <= 0)) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (normalized.stream().anyMatch(item -> item.price() == null || item.price().signum() <= 0)) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        var skuCodes = new HashSet<String>();
        if (normalized.stream().anyMatch(item ->
                !skuCodes.add(item.skuCode().toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("duplicate SKU lines are not allowed");
        }
        return normalized.stream()
                .sorted(Comparator.comparing(OrderItemRequest::skuCode))
                .toList();
    }

    public record UserDetails(String firstName, String lastName, String email) {
    }

}

package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.dto.OrderItemRequest;
import com.merryl.ecommerce.order.dto.OrderRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderRequestFingerprintTests {

    private final OrderRequest.UserDetails customer =
            new OrderRequest.UserDetails("Taylor", "Customer", "buyer@example.com");

    @Test
    void reorderedItemsHaveTheSameFingerprint() {
        var requestA = request(List.of(
                new OrderItemRequest("IPHONE-15", new BigDecimal("799.00"), 2),
                new OrderItemRequest("AIRPODS-PRO", new BigDecimal("249"), 1)));
        var requestB = request(List.of(
                new OrderItemRequest("AIRPODS-PRO", new BigDecimal("249.00"), 1),
                new OrderItemRequest("IPHONE-15", new BigDecimal("799"), 2)));

        assertEquals(OrderRequestFingerprint.from(requestA), OrderRequestFingerprint.from(requestB));
    }

    @Test
    void legacySingleItemHashIsEquivalentToOneItemListHash() {
        var legacy = new OrderRequest(null, null, "IPHONE-15",
                new BigDecimal("799.00"), 2, customer);
        var itemList = request(List.of(new OrderItemRequest(
                "IPHONE-15", new BigDecimal("799"), 2)));

        assertEquals(OrderRequestFingerprint.from(legacy), OrderRequestFingerprint.from(itemList));
    }

    @Test
    void changedItemDetailsChangeTheFingerprint() {
        var original = request(List.of(new OrderItemRequest("IPHONE-15", new BigDecimal("799"), 2)));
        var changed = request(List.of(new OrderItemRequest("IPHONE-15", new BigDecimal("799"), 3)));

        assertNotEquals(OrderRequestFingerprint.from(original), OrderRequestFingerprint.from(changed));
    }

    @Test
    void changingPriceSkuOrCustomerDetailsChangesTheFingerprint() {
        var original = request(List.of(new OrderItemRequest("IPHONE-15", new BigDecimal("799"), 2)));
        var changedPrice = request(List.of(new OrderItemRequest("IPHONE-15", new BigDecimal("800"), 2)));
        var changedSku = request(List.of(new OrderItemRequest("IPHONE-16", new BigDecimal("799"), 2)));
        var changedCustomer = new OrderRequest(null, null, null, null, null,
                new OrderRequest.UserDetails("Alex", "Customer", "buyer@example.com"),
                List.of(new OrderItemRequest("IPHONE-15", new BigDecimal("799"), 2)));

        assertNotEquals(OrderRequestFingerprint.from(original), OrderRequestFingerprint.from(changedPrice));
        assertNotEquals(OrderRequestFingerprint.from(original), OrderRequestFingerprint.from(changedSku));
        assertNotEquals(OrderRequestFingerprint.from(original), OrderRequestFingerprint.from(changedCustomer));
    }

    @Test
    void canonicalItemsRejectMissingOrMultipleRepresentations() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderRequest(null, null, null, null, null, customer, null).canonicalItems());
        assertThrows(IllegalArgumentException.class,
                () -> new OrderRequest(null, null, "SKU", BigDecimal.ONE, 1, customer,
                        List.of(new OrderItemRequest("OTHER", BigDecimal.ONE, 1))).canonicalItems());
    }

    @Test
    void canonicalItemsRejectEmptyInvalidAndDuplicateLines() {
        assertThrows(IllegalArgumentException.class, () -> request(List.of()).canonicalItems());
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(new OrderItemRequest("SKU", BigDecimal.ONE, 1),
                        new OrderItemRequest("SKU", BigDecimal.TEN, 2))).canonicalItems());
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(new OrderItemRequest("SKU", BigDecimal.ONE, 1),
                        new OrderItemRequest("sku", BigDecimal.ONE, 1))).canonicalItems());
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(new OrderItemRequest("SKU", BigDecimal.ONE, 0))).canonicalItems());
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(new OrderItemRequest("SKU", BigDecimal.ZERO, 1))).canonicalItems());
    }

    private OrderRequest request(List<OrderItemRequest> items) {
        return new OrderRequest(null, null, null, null, null, customer, items);
    }
}

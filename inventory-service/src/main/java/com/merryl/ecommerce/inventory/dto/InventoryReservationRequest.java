package com.merryl.ecommerce.inventory.dto;

import java.util.List;
import java.util.ArrayList;

public record InventoryReservationRequest(
        String reservationId,
        String skuCode,
        Integer quantity,
        List<InventoryReservationItemRequest> items) {

    public InventoryReservationRequest(String reservationId, String skuCode, Integer quantity) {
        this(reservationId, skuCode, quantity, null);
    }

    public InventoryReservationRequest(
            String reservationId, List<InventoryReservationItemRequest> items) {
        this(reservationId, null, null, items);
    }

    public List<InventoryReservationItemRequest> canonicalItems() {
        boolean hasLegacyItem = skuCode != null || quantity != null;
        boolean hasItemList = items != null;
        if (hasLegacyItem == hasItemList) {
            throw new IllegalArgumentException("Provide exactly one of legacy item fields or items");
        }
        return hasItemList ? new ArrayList<>(items)
                : List.of(new InventoryReservationItemRequest(skuCode, quantity));
    }
}

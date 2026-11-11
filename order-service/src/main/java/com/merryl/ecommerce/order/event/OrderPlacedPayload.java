package com.merryl.ecommerce.order.event;

import java.util.List;

public record OrderPlacedPayload(
        String eventId,
        String orderNumber,
        String email,
        String firstName,
        String lastName,
        List<OrderPlacedItemPayload> items) {
}

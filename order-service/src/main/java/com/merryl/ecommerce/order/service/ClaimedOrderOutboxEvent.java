package com.merryl.ecommerce.order.service;

public record ClaimedOrderOutboxEvent(
        String eventId,
        String eventType,
        String aggregateId,
        String payload,
        int attemptCount) {
}

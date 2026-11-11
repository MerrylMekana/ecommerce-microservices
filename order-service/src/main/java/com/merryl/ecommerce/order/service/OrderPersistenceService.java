package com.merryl.ecommerce.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merryl.ecommerce.order.event.OrderPlacedPayload;
import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.model.OrderOutboxEvent;
import com.merryl.ecommerce.order.model.OrderOutboxStatus;
import com.merryl.ecommerce.order.repository.OrderIdempotencyRepository;
import com.merryl.ecommerce.order.repository.OrderOutboxRepository;
import com.merryl.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderIdempotencyRepository idempotencyRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean saveAndComplete(Order order, OrderPlacedPayload eventPayload) {
        boolean created = orderRepository.findByOrderNumber(order.getOrderNumber()).isEmpty();
        if (created) {
            orderRepository.saveAndFlush(order);
        }

        int transitioned = idempotencyRepository.transitionStatus(order.getOrderNumber(),
                OrderIdempotencyStatus.PROCESSING, OrderIdempotencyStatus.SUCCEEDED, null, null);
        if (transitioned != 1) {
            throw new OrderStateTransitionException(
                    "Order " + order.getOrderNumber() + " lost the transition to SUCCEEDED");
        }
        saveOutboxEvent(eventPayload);
        return created;
    }

    @Transactional
    public boolean ensureExistingOrderCompleted(Order order, OrderPlacedPayload eventPayload) {
        if (orderRepository.findByOrderNumberForUpdate(order.getOrderNumber()).isEmpty()) {
            throw new IllegalStateException("Cannot complete an order that is not persisted");
        }

        int transitioned = idempotencyRepository.transitionStatus(order.getOrderNumber(),
                OrderIdempotencyStatus.PROCESSING, OrderIdempotencyStatus.SUCCEEDED, null, null);
        if (transitioned != 1) {
            OrderIdempotencyStatus currentStatus = idempotencyRepository.findByOrderNumber(order.getOrderNumber())
                    .map(idempotency -> idempotency.getStatus())
                    .orElseThrow(() -> new OrderStateTransitionException(
                            "Order idempotency state disappeared for " + order.getOrderNumber()));
            if (currentStatus != OrderIdempotencyStatus.SUCCEEDED) {
                throw new OrderStateTransitionException(
                        "Order " + order.getOrderNumber() + " cannot be completed from " + currentStatus);
            }
        }
        saveOutboxEvent(eventPayload);
        return transitioned == 1;
    }

    private void saveOutboxEvent(OrderPlacedPayload payload) {
        if (payload.eventId() == null || payload.eventId().isBlank()
                || payload.orderNumber() == null || payload.orderNumber().isBlank()) {
            throw new IllegalArgumentException("Outbox event ID and aggregate ID are required");
        }
        if (outboxRepository.existsByEventTypeAndAggregateId("ORDER_PLACED", payload.orderNumber())) {
            return;
        }

        OrderOutboxEvent event = new OrderOutboxEvent();
        event.setEventId(payload.eventId());
        event.setEventType("ORDER_PLACED");
        event.setAggregateId(payload.orderNumber());
        event.setPayload(serialize(payload));
        event.setStatus(OrderOutboxStatus.PENDING);
        event.setCreatedAt(LocalDateTime.now());
        outboxRepository.saveAndFlush(event);
    }

    private String serialize(OrderPlacedPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize OrderPlacedEvent for the outbox", exception);
        }
    }
}

package com.merryl.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import com.merryl.ecommerce.order.event.OrderPlacedItemPayload;
import com.merryl.ecommerce.order.event.OrderPlacedPayload;
import com.merryl.ecommerce.order.metrics.OrderBusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderOutboxPublisherTests {

    private OrderOutboxClaimService claimService;
    private OrderOutboxStateService stateService;
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private OrderBusinessMetrics metrics;
    private OrderOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        claimService = mock(OrderOutboxClaimService.class);
        stateService = mock(OrderOutboxStateService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        metrics = mock(OrderBusinessMetrics.class);
        publisher = new OrderOutboxPublisher(
                claimService, stateService, kafkaTemplate, new ObjectMapper(), metrics);
        ReflectionTestUtils.setField(publisher, "topic", "order-placed");
        ReflectionTestUtils.setField(publisher, "publishTimeoutSeconds", 1L);
    }

    @Test
    void publishesExistingAvroEventAndMarksOutboxAsPublished() throws Exception {
        ClaimedOrderOutboxEvent claimed = claimedEvent();
        when(claimService.claimNext()).thenReturn(Optional.of(claimed), Optional.empty());
        when(kafkaTemplate.send(eq("order-placed"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafkaTemplate).send(eq("order-placed"), eq("order-123"), eventCaptor.capture());
        assertEquals("event-123", eventCaptor.getValue().getEventId().toString());
        assertEquals("order-123", eventCaptor.getValue().getOrderNumber().toString());
        verify(stateService).markPublished("event-123");
        verify(metrics).outboxPublished();
    }

    @Test
    void failedKafkaPublicationLeavesEventAvailableForRetry() throws Exception {
        ClaimedOrderOutboxEvent claimed = claimedEvent();
        when(claimService.claimNext()).thenReturn(Optional.of(claimed), Optional.empty());
        when(kafkaTemplate.send(eq("order-placed"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        publisher.publishPendingEvents();

        verify(stateService).markForRetry(eq("event-123"), eq(2), any(String.class));
        verify(metrics).outboxPublishFailed();
        verify(metrics).outboxRetried();
    }

    @Test
    void stableEventIdAllowsConsumersToIdentifyDuplicatePublication() throws Exception {
        ClaimedOrderOutboxEvent claimed = claimedEvent();
        when(claimService.claimNext()).thenReturn(Optional.of(claimed), Optional.of(claimed), Optional.empty());
        when(kafkaTemplate.send(eq("order-placed"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafkaTemplate, times(2)).send(eq("order-placed"), eq("order-123"), eventCaptor.capture());
        assertEquals("event-123", eventCaptor.getAllValues().get(0).getEventId().toString());
        assertEquals("event-123", eventCaptor.getAllValues().get(1).getEventId().toString());
        verify(stateService, times(2)).markPublished("event-123");
    }

    private ClaimedOrderOutboxEvent claimedEvent() throws Exception {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                "event-123",
                "order-123",
                "buyer@example.com",
                "Taylor",
                "Customer",
                List.of(new OrderPlacedItemPayload("SKU-1", "12", 1)));
        return new ClaimedOrderOutboxEvent(
                "event-123", "ORDER_PLACED", "order-123", new ObjectMapper().writeValueAsString(payload), 2);
    }
}

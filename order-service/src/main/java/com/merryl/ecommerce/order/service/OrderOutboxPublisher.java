package com.merryl.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import com.merryl.ecommerce.order.event.OrderPlacedItemPayload;
import com.merryl.ecommerce.order.event.OrderPlacedPayload;
import com.merryl.ecommerce.order.metrics.OrderBusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxPublisher {

    private static final String ORDER_PLACED = "ORDER_PLACED";
    private static final int BATCH_SIZE = 25;

    private final OrderOutboxClaimService claimService;
    private final OrderOutboxStateService stateService;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderBusinessMetrics metrics;

    @Value("${order.kafka.topic:order-placed}")
    private String topic;

    @Value("${order.outbox.publish-timeout-seconds:10}")
    private long publishTimeoutSeconds;

    @Scheduled(fixedDelayString = "${order.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        for (int published = 0; published < BATCH_SIZE; published++) {
            Optional<ClaimedOrderOutboxEvent> candidate = claimService.claimNext();
            if (candidate.isEmpty()) {
                return;
            }

            ClaimedOrderOutboxEvent claimed = candidate.get();
            try {
                OrderPlacedPayload payload = objectMapper.readValue(claimed.payload(), OrderPlacedPayload.class);
                if (!ORDER_PLACED.equals(claimed.eventType())
                        || !claimed.eventId().equals(payload.eventId())
                        || !claimed.aggregateId().equals(payload.orderNumber())) {
                    throw new IllegalStateException("Outbox event metadata does not match its payload");
                }

                OrderPlacedEvent event = toEvent(payload);
                kafkaTemplate.send(topic, payload.orderNumber(), event)
                        .get(publishTimeoutSeconds, TimeUnit.SECONDS);
                stateService.markPublished(claimed.eventId());
                metrics.outboxPublished();
                log.info("Published order event {} for order {}", claimed.eventId(), payload.orderNumber());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                metrics.outboxPublishFailed();
                metrics.outboxRetried();
                stateService.markForRetry(claimed.eventId(), claimed.attemptCount(), exception.toString());
                return;
            } catch (Exception exception) {
                metrics.outboxPublishFailed();
                metrics.outboxRetried();
                stateService.markForRetry(claimed.eventId(), claimed.attemptCount(), exception.toString());
                log.warn("Could not publish order event {}; it will be retried", claimed.eventId(), exception);
            }
        }
    }

    private OrderPlacedEvent toEvent(OrderPlacedPayload payload) {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setEventId(payload.eventId());
        event.setOrderNumber(payload.orderNumber());
        event.setEmail(payload.email());
        event.setFirstName(payload.firstName());
        event.setLastName(payload.lastName());
        event.setItems(payload.items().stream().map(this::toEventItem).toList());
        return event;
    }

    private com.merryl.ecommerce.order.event.OrderItem toEventItem(OrderPlacedItemPayload item) {
        return new com.merryl.ecommerce.order.event.OrderItem(
                item.skuCode(), item.unitPrice(), item.quantity());
    }
}

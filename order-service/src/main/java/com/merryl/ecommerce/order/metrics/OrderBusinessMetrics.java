package com.merryl.ecommerce.order.metrics;

import com.merryl.ecommerce.order.repository.OrderIdempotencyRepository;
import com.merryl.ecommerce.order.repository.OrderOutboxRepository;
import com.merryl.ecommerce.order.service.OrderIdempotencyStatus;
import com.merryl.ecommerce.order.model.OrderOutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderBusinessMetrics {

    private final Counter created;
    private final Counter succeeded;
    private final Counter failed;
    private final Counter idempotencyReplays;
    private final Counter outboxPublishSuccess;
    private final Counter outboxPublishFailure;
    private final Counter outboxRetries;

    public OrderBusinessMetrics(
            MeterRegistry registry,
            OrderIdempotencyRepository idempotencyRepository,
            OrderOutboxRepository outboxRepository) {
        created = registry.counter("orders_created_count");
        succeeded = registry.counter("orders_succeeded");
        failed = registry.counter("orders_failed");
        idempotencyReplays = registry.counter("orders_idempotency_replays");
        outboxPublishSuccess = registry.counter("order_outbox_publish_success");
        outboxPublishFailure = registry.counter("order_outbox_publish_failure");
        outboxRetries = registry.counter("order_outbox_retries");
        Gauge.builder("order_outbox_pending", outboxRepository,
                        repository -> repository.countByStatusNot(OrderOutboxStatus.PUBLISHED))
                .description("Unpublished order outbox events")
                .register(registry);
        Gauge.builder("inventory_compensation_pending", idempotencyRepository,
                        repository -> repository.countByStatus(OrderIdempotencyStatus.COMPENSATION_PENDING))
                .description("Order reservations still awaiting inventory compensation")
                .register(registry);
    }

    public void orderCreated() {
        created.increment();
    }

    public void orderSucceeded() {
        succeeded.increment();
    }

    public void orderFailed() {
        failed.increment();
    }

    public void idempotencyReplay() {
        idempotencyReplays.increment();
    }

    public void outboxPublished() {
        outboxPublishSuccess.increment();
    }

    public void outboxPublishFailed() {
        outboxPublishFailure.increment();
    }

    public void outboxRetried() {
        outboxRetries.increment();
    }
}

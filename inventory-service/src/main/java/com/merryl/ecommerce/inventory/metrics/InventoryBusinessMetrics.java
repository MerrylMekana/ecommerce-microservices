package com.merryl.ecommerce.inventory.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryBusinessMetrics {

    private final Counter reservations;
    private final Counter reservationFailures;
    private final Counter insufficientStock;
    private final Counter adjustmentReplays;

    public InventoryBusinessMetrics(MeterRegistry registry) {
        reservations = registry.counter("inventory_reservations");
        reservationFailures = registry.counter("inventory_reservation_failures");
        insufficientStock = registry.counter("inventory_insufficient_stock");
        adjustmentReplays = registry.counter("inventory_adjustment_replays");
    }

    public void reservationSucceeded() {
        reservations.increment();
    }

    public void reservationFailed() {
        reservationFailures.increment();
    }

    public void insufficientStock() {
        insufficientStock.increment();
    }

    public void adjustmentReplay() {
        adjustmentReplays.increment();
    }
}

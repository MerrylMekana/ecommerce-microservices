package com.merryl.ecommerce.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface InventoryClient {

    Logger log = LoggerFactory.getLogger(InventoryClient.class);

    @PostExchange("/api/inventory/reservations")
    @CircuitBreaker(name = "inventory", fallbackMethod = "reserveFallback")
    @Retry(name = "inventory")
    InventoryReservationResponse reserve(@RequestBody InventoryReservationRequest request);

    @PostExchange("/api/inventory/reservations/{reservationId}/release")
    @CircuitBreaker(name = "inventory", fallbackMethod = "releaseFallback")
    @Retry(name = "inventory")
    InventoryReservationResponse release(@PathVariable String reservationId);

    default InventoryReservationResponse reserveFallback(
            InventoryReservationRequest request, Throwable throwable) {
        log.warn("Inventory reservation failed for reservation {}", request.reservationId(),
                throwable);
        throw new InventoryUnavailableException("Inventory Service is unavailable", throwable);
    }

    default InventoryReservationResponse releaseFallback(String reservationId, Throwable throwable) {
        log.warn("Inventory release failed for reservation {}", reservationId, throwable);
        throw new InventoryUnavailableException("Inventory Service is unavailable during release", throwable);
    }
}

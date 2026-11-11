package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.model.OrderIdempotency;
import com.merryl.ecommerce.order.metrics.OrderBusinessMetrics;
import com.merryl.ecommerce.order.repository.OrderIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private final OrderIdempotencyRepository repository;
    private final OrderBusinessMetrics metrics;

    public OrderIdempotency claim(String key, String requestHash) {
        var existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return validateReplay(existing.get(), requestHash);
        }

        OrderIdempotency request = new OrderIdempotency();
        request.setIdempotencyKey(key);
        request.setRequestHash(requestHash);
        request.setOrderNumber(UUID.randomUUID().toString());
        request.setStatus(OrderIdempotencyStatus.PROCESSING);

        try {
            return repository.saveAndFlush(request);
        } catch (DataIntegrityViolationException concurrentClaim) {
            OrderIdempotency claimed = repository.findByIdempotencyKey(key)
                    .orElseThrow(() -> concurrentClaim);
            return validateReplay(claimed, requestHash);
        }
    }

    public boolean markSucceeded(String orderNumber) {
        return transition(orderNumber, OrderIdempotencyStatus.PROCESSING,
                OrderIdempotencyStatus.SUCCEEDED, null, null);
    }

    public void markInventoryRejected(String orderNumber, String code, String message) {
        transition(orderNumber, OrderIdempotencyStatus.PROCESSING,
                OrderIdempotencyStatus.INVENTORY_REJECTED, code, message);
    }

    public boolean markCompensationPending(String orderNumber, String message) {
        return transition(orderNumber, OrderIdempotencyStatus.PROCESSING,
                OrderIdempotencyStatus.COMPENSATION_PENDING, "COMPENSATION_PENDING", message);
    }

    public void markCompensated(String orderNumber, String message) {
        transition(orderNumber, OrderIdempotencyStatus.COMPENSATION_PENDING,
                OrderIdempotencyStatus.COMPENSATED, "ORDER_CREATION_FAILED", message);
    }

    private boolean transition(
            String orderNumber,
            OrderIdempotencyStatus expected,
            OrderIdempotencyStatus next,
            String errorCode,
            String errorMessage) {
        return repository.transitionStatus(orderNumber, expected, next, errorCode, errorMessage) == 1;
    }

    private OrderIdempotency validateReplay(OrderIdempotency existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used with a different request");
        }
        metrics.idempotencyReplay();
        return existing;
    }
}

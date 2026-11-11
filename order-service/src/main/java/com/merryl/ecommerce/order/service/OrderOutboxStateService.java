package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.model.OrderOutboxEvent;
import com.merryl.ecommerce.order.model.OrderOutboxStatus;
import com.merryl.ecommerce.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderOutboxStateService {

    private final OrderOutboxRepository outboxRepository;

    @Transactional
    public void markPublished(String eventId) {
        OrderOutboxEvent event = findForUpdate(eventId);
        event.setStatus(OrderOutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        event.setLeaseUntil(null);
        event.setNextAttemptAt(null);
        event.setLastError(null);
    }

    @Transactional
    public void markForRetry(String eventId, int attemptCount, String error) {
        OrderOutboxEvent event = findForUpdate(eventId);
        long delaySeconds = Math.min(60, 1L << Math.min(Math.max(attemptCount - 1, 0), 6));
        event.setStatus(OrderOutboxStatus.PENDING);
        event.setLeaseUntil(null);
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
        event.setLastError(error.length() <= 1000 ? error : error.substring(0, 1000));
    }

    private OrderOutboxEvent findForUpdate(String eventId) {
        return outboxRepository.findByEventIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event disappeared: " + eventId));
    }
}

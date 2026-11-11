package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.model.OrderOutboxEvent;
import com.merryl.ecommerce.order.model.OrderOutboxStatus;
import com.merryl.ecommerce.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderOutboxClaimService {

    private final OrderOutboxRepository outboxRepository;

    @Value("${order.outbox.lease-duration-seconds:60}")
    private long leaseDurationSeconds;

    @Transactional
    public Optional<ClaimedOrderOutboxEvent> claimNext() {
        Optional<OrderOutboxEvent> candidate = outboxRepository.findNextDispatchableForUpdate();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        OrderOutboxEvent event = candidate.get();
        event.setStatus(OrderOutboxStatus.IN_PROGRESS);
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLeaseUntil(LocalDateTime.now().plusSeconds(leaseDurationSeconds));
        outboxRepository.save(event);
        return Optional.of(new ClaimedOrderOutboxEvent(
                event.getEventId(),
                event.getEventType(),
                event.getAggregateId(),
                event.getPayload(),
                event.getAttemptCount()));
    }
}

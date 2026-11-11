package com.merryl.ecommerce.order.repository;

import com.merryl.ecommerce.order.model.OrderOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, String> {

    boolean existsByEventTypeAndAggregateId(String eventType, String aggregateId);

    long countByEventTypeAndAggregateId(String eventType, String aggregateId);

    long countByStatusNot(com.merryl.ecommerce.order.model.OrderOutboxStatus status);

    @Query(value = """
            SELECT *
            FROM table_order_outbox
            WHERE (status = 'PENDING'
                   AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP(6)))
               OR (status = 'IN_PROGRESS' AND lease_until <= CURRENT_TIMESTAMP(6))
            ORDER BY created_at, event_id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OrderOutboxEvent> findNextDispatchableForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OrderOutboxEvent event where event.eventId = :eventId")
    Optional<OrderOutboxEvent> findByEventIdForUpdate(@Param("eventId") String eventId);
}

package com.merryl.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "table_order_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_order_outbox_type_aggregate",
                columnNames = {"event_type", "aggregate_id"}),
        indexes = {
                @Index(name = "idx_order_outbox_dispatch",
                        columnList = "status,next_attempt_at,created_at"),
                @Index(name = "idx_order_outbox_expired_lease",
                        columnList = "status,lease_until")
        })
@Getter
@Setter
@NoArgsConstructor
public class OrderOutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}

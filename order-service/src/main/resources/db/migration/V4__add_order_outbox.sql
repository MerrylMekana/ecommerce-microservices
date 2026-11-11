CREATE TABLE table_order_outbox
(
    event_id varchar(36) NOT NULL,
    event_type varchar(100) NOT NULL,
    aggregate_id varchar(36) NOT NULL,
    payload json NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count int NOT NULL DEFAULT 0,
    next_attempt_at timestamp(6) NULL,
    lease_until timestamp(6) NULL,
    last_error varchar(1000) NULL,
    created_at timestamp(6) NOT NULL,
    published_at timestamp(6) NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT uq_order_outbox_type_aggregate UNIQUE (event_type, aggregate_id),
    CONSTRAINT chk_order_outbox_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED')),
    CONSTRAINT chk_order_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_order_outbox_dispatch
    ON table_order_outbox (status, next_attempt_at, created_at);

CREATE INDEX idx_order_outbox_expired_lease
    ON table_order_outbox (status, lease_until);

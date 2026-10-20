ALTER TABLE table_orders
    MODIFY order_number varchar(255) NOT NULL,
    ADD CONSTRAINT uq_table_orders_order_number UNIQUE (order_number);

CREATE TABLE table_order_idempotency
(
    id bigint NOT NULL AUTO_INCREMENT,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    order_number varchar(36) NOT NULL,
    status varchar(32) NOT NULL,
    error_code varchar(50),
    error_message varchar(255),
    created_at timestamp(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_order_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_order_idempotency_number UNIQUE (order_number)
);

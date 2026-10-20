ALTER TABLE table_inventory
    MODIFY sku_code varchar(255) NOT NULL,
    MODIFY quantity int NOT NULL,
    ADD CONSTRAINT uq_inventory_sku_code UNIQUE (sku_code),
    ADD CONSTRAINT chk_inventory_quantity_nonnegative CHECK (quantity >= 0);

CREATE TABLE table_inventory_reservations
(
    id bigint NOT NULL AUTO_INCREMENT,
    reservation_id varchar(64) NOT NULL,
    sku_code varchar(255) NOT NULL,
    quantity int NOT NULL,
    request_hash char(64) NOT NULL,
    status varchar(20) NOT NULL,
    failure_code varchar(50),
    failure_message varchar(255),
    PRIMARY KEY (id),
    CONSTRAINT uq_inventory_reservation_id UNIQUE (reservation_id),
    CONSTRAINT chk_inventory_reservation_quantity_positive CHECK (quantity > 0)
);

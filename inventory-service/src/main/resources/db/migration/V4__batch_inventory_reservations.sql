CREATE TABLE table_inventory_reservation_items
(
    reservation_id varchar(64) NOT NULL,
    sku_code varchar(255) NOT NULL,
    quantity int NOT NULL,
    PRIMARY KEY (reservation_id, sku_code),
    CONSTRAINT fk_inventory_reservation_item_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES table_inventory_reservations (reservation_id),
    CONSTRAINT chk_inventory_reservation_item_quantity_positive CHECK (quantity > 0)
);

INSERT INTO table_inventory_reservation_items (reservation_id, sku_code, quantity)
SELECT reservation_id, sku_code, quantity
FROM table_inventory_reservations;

ALTER TABLE table_inventory_reservations
    MODIFY sku_code varchar(255) NULL,
    MODIFY quantity int NULL;

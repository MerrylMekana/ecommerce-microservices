ALTER TABLE table_orders
    MODIFY sku_code varchar(255) NOT NULL,
    MODIFY price decimal(19,2) NOT NULL,
    MODIFY quantity int NOT NULL,
    ADD CONSTRAINT chk_table_order_quantity_positive CHECK (quantity > 0);

CREATE TABLE table_order_items
(
    id bigint NOT NULL AUTO_INCREMENT,
    order_id bigint NOT NULL,
    sku_code varchar(255) NOT NULL,
    quantity int NOT NULL,
    unit_price decimal(19,2),
    line_total decimal(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_table_order_items_order
        FOREIGN KEY (order_id) REFERENCES table_orders (id),
    CONSTRAINT uq_table_order_items_order_sku UNIQUE (order_id, sku_code),
    CONSTRAINT chk_table_order_item_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_table_order_items_sku_code ON table_order_items (sku_code);

INSERT INTO table_order_items (order_id, sku_code, quantity, unit_price, line_total)
SELECT id, sku_code, quantity, NULL, price
FROM table_orders;

ALTER TABLE table_orders
    MODIFY sku_code varchar(255) NULL,
    MODIFY price decimal(19,2) NULL,
    MODIFY quantity int NULL;

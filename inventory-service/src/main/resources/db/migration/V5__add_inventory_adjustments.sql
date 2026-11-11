CREATE TABLE table_inventory_adjustments
(
    id bigint NOT NULL AUTO_INCREMENT,
    operation_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    sku_code varchar(255) NOT NULL,
    operation varchar(8) NOT NULL,
    quantity int NOT NULL,
    status varchar(16) NOT NULL,
    result_quantity int,
    error_code varchar(50),
    created_at timestamp(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_inventory_adjustment_operation_key UNIQUE (operation_key),
    CONSTRAINT chk_inventory_adjustment_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_inventory_adjustment_operation CHECK (operation IN ('ADD', 'REMOVE')),
    CONSTRAINT chk_inventory_adjustment_status CHECK (status IN ('APPLIED', 'REJECTED'))
);

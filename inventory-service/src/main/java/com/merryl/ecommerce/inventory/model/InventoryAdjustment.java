package com.merryl.ecommerce.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "table_inventory_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_key", nullable = false, unique = true, length = 128)
    private String operationKey;

    @Column(name = "sku_code", nullable = false, length = 255)
    private String skuCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private InventoryAdjustmentType operation;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InventoryAdjustmentStatus status;

    @Column(name = "result_quantity")
    private Integer resultQuantity;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        createdAt = Instant.now();
    }
}

package com.merryl.ecommerce.inventory.service;

public class InventoryAdjustmentConflictException extends RuntimeException {
    public InventoryAdjustmentConflictException(String operationKey) {
        super("Idempotency-Key was already used for a different inventory adjustment: " + operationKey);
    }
}

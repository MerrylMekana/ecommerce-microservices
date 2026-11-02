package com.merryl.ecommerce.inventory.service;

public class InventoryConflictException extends RuntimeException {
    public InventoryConflictException(String message) {
        super(message);
    }

    public InventoryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

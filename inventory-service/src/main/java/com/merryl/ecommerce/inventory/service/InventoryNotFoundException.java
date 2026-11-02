package com.merryl.ecommerce.inventory.service;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(String skuCode) {
        super("No inventory record exists for SKU " + skuCode);
    }
}

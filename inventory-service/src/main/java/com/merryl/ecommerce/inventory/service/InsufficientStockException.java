package com.merryl.ecommerce.inventory.service;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String skuCode) {
        super("Cannot remove more stock than is available for SKU " + skuCode);
    }
}

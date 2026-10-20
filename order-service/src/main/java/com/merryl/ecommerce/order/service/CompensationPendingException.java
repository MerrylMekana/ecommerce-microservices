package com.merryl.ecommerce.order.service;

public class CompensationPendingException extends RuntimeException {
    public CompensationPendingException(String orderNumber, Throwable cause) {
        super("Order persistence failed and inventory release is pending for order " + orderNumber, cause);
    }
}

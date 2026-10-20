package com.merryl.ecommerce.order.service;

public class OrderStateTransitionException extends RuntimeException {
    public OrderStateTransitionException(String message) {
        super(message);
    }

    public OrderStateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

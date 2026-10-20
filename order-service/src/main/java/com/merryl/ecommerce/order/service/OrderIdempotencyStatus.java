package com.merryl.ecommerce.order.service;

public enum OrderIdempotencyStatus {
    PROCESSING,
    SUCCEEDED,
    INVENTORY_REJECTED,
    COMPENSATION_PENDING,
    COMPENSATED
}

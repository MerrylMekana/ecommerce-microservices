package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.repository.OrderIdempotencyRepository;
import com.merryl.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderIdempotencyRepository idempotencyRepository;

    @Transactional
    public boolean saveAndComplete(Order order) {
        if (orderRepository.findByOrderNumber(order.getOrderNumber()).isEmpty()) {
            orderRepository.saveAndFlush(order);
        }

        int transitioned = idempotencyRepository.transitionStatus(order.getOrderNumber(),
                OrderIdempotencyStatus.PROCESSING, OrderIdempotencyStatus.SUCCEEDED, null, null);
        if (transitioned != 1) {
            throw new OrderStateTransitionException(
                    "Order " + order.getOrderNumber() + " lost the transition to SUCCEEDED");
        }
        return true;
    }
}

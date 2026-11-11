package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.client.InventoryClient;
import com.merryl.ecommerce.order.client.InventoryReservationRequest;
import com.merryl.ecommerce.order.client.InventoryReservationItemRequest;
import com.merryl.ecommerce.order.client.InventoryReservationResponse;
import com.merryl.ecommerce.order.client.InventoryUnavailableException;
import com.merryl.ecommerce.order.dto.OrderRequest;
import com.merryl.ecommerce.order.dto.OrderItemRequest;
import com.merryl.ecommerce.order.event.OrderPlacedItemPayload;
import com.merryl.ecommerce.order.event.OrderPlacedPayload;
import com.merryl.ecommerce.order.metrics.OrderBusinessMetrics;
import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.model.OrderItem;
import com.merryl.ecommerce.order.model.OrderIdempotency;
import com.merryl.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final OrderIdempotencyService idempotencyService;
    private final OrderPersistenceService orderPersistenceService;
    private final OrderBusinessMetrics metrics;

    public void placeOrder(OrderRequest orderRequest, String idempotencyKey) {
        try {
            placeOrderInternal(orderRequest, idempotencyKey);
        } catch (RuntimeException failure) {
            metrics.orderFailed();
            throw failure;
        }
    }

    private void placeOrderInternal(OrderRequest orderRequest, String idempotencyKey) {
        List<OrderItemRequest> items = validate(orderRequest, idempotencyKey);

        OrderIdempotency idempotency = idempotencyService.claim(
                idempotencyKey, OrderRequestFingerprint.from(orderRequest));

        switch (idempotency.getStatus()) {
            case SUCCEEDED -> {
                orderRepository.findByOrderNumber(idempotency.getOrderNumber())
                        .ifPresent(order -> {
                            if (orderPersistenceService.ensureExistingOrderCompleted(
                                    order, toEventPayload(orderRequest, idempotency.getOrderNumber()))) {
                                metrics.orderSucceeded();
                            }
                        });
                return;
            }
            case INVENTORY_REJECTED -> throw new InsufficientInventoryException(
                    idempotency.getErrorCode(), idempotency.getErrorMessage());
            case COMPENSATION_PENDING -> throw new CompensationPendingException(
                    idempotency.getOrderNumber(), null);
            case COMPENSATED -> throw new OrderCreationException(
                    "The previous attempt was compensated; use a new Idempotency-Key to retry", null);
            case PROCESSING -> {
            }
        }

        if (orderRepository.findByOrderNumber(idempotency.getOrderNumber()).isPresent()) {
            if (orderPersistenceService.ensureExistingOrderCompleted(
                    toOrder(items, idempotency.getOrderNumber()),
                    toEventPayload(orderRequest, idempotency.getOrderNumber()))) {
                metrics.orderSucceeded();
            }
            return;
        }

        InventoryReservationResponse reservation = inventoryClient.reserve(inventoryReservationRequest(
                idempotency.getOrderNumber(), items));

        if (reservation == null) {
            throw new InventoryUnavailableException("Inventory Service returned an empty reservation response", null);
        }
        if (!"RESERVED".equals(reservation.status())) {
            if ("REJECTED".equals(reservation.status())) {
                idempotencyService.markInventoryRejected(
                        idempotency.getOrderNumber(), reservation.code(), reservation.message());
                throw new InsufficientInventoryException(reservation.code(), reservation.message());
            }
            throw new InventoryUnavailableException(
                    "Inventory reservation is not active for order " + idempotency.getOrderNumber(), null);
        }

        Order order = toOrder(items, idempotency.getOrderNumber());
        OrderPlacedPayload eventPayload = toEventPayload(orderRequest, idempotency.getOrderNumber());
        try {
            if (orderPersistenceService.saveAndComplete(order, eventPayload)) {
                metrics.orderCreated();
            }
            metrics.orderSucceeded();
        } catch (DataAccessException persistenceFailure) {
            var persistedOrder = orderRepository.findByOrderNumber(idempotency.getOrderNumber());
            if (persistedOrder.isPresent()) {
                if (orderPersistenceService.ensureExistingOrderCompleted(persistedOrder.get(), eventPayload)) {
                    metrics.orderSucceeded();
                }
            } else {
                compensateReservation(idempotency, persistenceFailure);
                return;
            }
        }
    }

    private void compensateReservation(OrderIdempotency idempotency, DataAccessException persistenceFailure) {
        boolean ownsCompensation = idempotencyService.markCompensationPending(idempotency.getOrderNumber(),
                "Order persistence failed; inventory release is pending");
        if (!ownsCompensation) {
            throw new OrderStateTransitionException(
                    "Order " + idempotency.getOrderNumber()
                            + " changed state before compensation could be claimed",
                    persistenceFailure);
        }
        try {
            releaseReservation(idempotency.getOrderNumber());
            idempotencyService.markCompensated(idempotency.getOrderNumber(),
                    "Order persistence failed and inventory was released");
        } catch (InventoryUnavailableException | RestClientException exception) {
            throw new CompensationPendingException(idempotency.getOrderNumber(), exception);
        }
        throw new OrderCreationException(
                "Order could not be saved; the inventory reservation was released", persistenceFailure);
    }

    private void releaseReservation(String reservationId) {
        InventoryReservationResponse response = inventoryClient.release(reservationId);
        if (response == null || !"RELEASED".equals(response.status())) {
            throw new InventoryUnavailableException(
                    "Inventory did not confirm release for reservation " + reservationId, null);
        }
    }

    private OrderPlacedPayload toEventPayload(OrderRequest orderRequest, String orderNumber) {
        OrderRequest.UserDetails userDetails = orderRequest.userDetails();
        return new OrderPlacedPayload(
                UUID.randomUUID().toString(),
                orderNumber,
                userDetails.email(),
                userDetails.firstName(),
                userDetails.lastName(),
                orderRequest.canonicalItems().stream()
                        .map(item -> new OrderPlacedItemPayload(
                                item.skuCode(), item.price().stripTrailingZeros().toPlainString(), item.quantity()))
                        .toList());
    }

    private Order toOrder(List<OrderItemRequest> items, String orderNumber) {
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : items) {
            BigDecimal lineTotal = itemRequest.price().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            total = total.add(lineTotal);

            OrderItem item = new OrderItem();
            item.setSkuCode(itemRequest.skuCode());
            item.setUnitPrice(itemRequest.price());
            item.setQuantity(itemRequest.quantity());
            item.setLineTotal(lineTotal);
            order.addItem(item);
        }
        order.setPrice(total);
        if (items.size() == 1) {
            order.setSkuCode(items.getFirst().skuCode());
            order.setQuantity(items.getFirst().quantity());
        }
        return order;
    }

    private InventoryReservationRequest inventoryReservationRequest(
            String reservationId, List<OrderItemRequest> items) {
        if (items.size() == 1) {
            OrderItemRequest item = items.getFirst();
            return new InventoryReservationRequest(reservationId, item.skuCode(), item.quantity());
        }
        return new InventoryReservationRequest(reservationId, items.stream()
                .map(item -> new InventoryReservationItemRequest(item.skuCode(), item.quantity()))
                .toList());
    }

    private List<OrderItemRequest> validate(OrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 1 and 128 characters");
        }
        if (request == null) {
            throw new IllegalArgumentException("order request is required");
        }
        if (request.userDetails() == null
                || isBlank(request.userDetails().firstName())
                || isBlank(request.userDetails().lastName())
                || isBlank(request.userDetails().email())) {
            throw new IllegalArgumentException("customer name and email are required");
        }
        return request.canonicalItems();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

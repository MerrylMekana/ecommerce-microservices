package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.client.InventoryClient;
import com.merryl.ecommerce.order.client.InventoryReservationRequest;
import com.merryl.ecommerce.order.client.InventoryReservationItemRequest;
import com.merryl.ecommerce.order.client.InventoryReservationResponse;
import com.merryl.ecommerce.order.client.InventoryUnavailableException;
import com.merryl.ecommerce.order.dto.OrderRequest;
import com.merryl.ecommerce.order.dto.OrderItemRequest;
import com.merryl.ecommerce.order.event.OrderPlacedEvent;
import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.model.OrderItem;
import com.merryl.ecommerce.order.model.OrderIdempotency;
import com.merryl.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final OrderIdempotencyService idempotencyService;
    private final OrderPersistenceService orderPersistenceService;

    public void placeOrder(OrderRequest orderRequest, String idempotencyKey) {
        List<OrderItemRequest> items = validate(orderRequest, idempotencyKey);

        OrderIdempotency idempotency = idempotencyService.claim(
                idempotencyKey, OrderRequestFingerprint.from(orderRequest));

        switch (idempotency.getStatus()) {
            case SUCCEEDED -> {
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
            publishIfNewlyCompleted(idempotency, orderRequest);
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
        boolean publishEvent;
        try {
            publishEvent = orderPersistenceService.saveAndComplete(order);
        } catch (DataAccessException persistenceFailure) {
            var persistedOrder = orderRepository.findByOrderNumber(idempotency.getOrderNumber());
            if (persistedOrder.isPresent()) {
                publishEvent = idempotencyService.markSucceeded(idempotency.getOrderNumber());
            } else {
                compensateReservation(idempotency, persistenceFailure);
                return;
            }
        }

        if (publishEvent) {
            publishOrderPlacedEvent(orderRequest, idempotency.getOrderNumber());
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

    private void publishIfNewlyCompleted(OrderIdempotency idempotency, OrderRequest orderRequest) {
        if (idempotencyService.markSucceeded(idempotency.getOrderNumber())) {
            publishOrderPlacedEvent(orderRequest, idempotency.getOrderNumber());
        }
    }

    private void publishOrderPlacedEvent(OrderRequest orderRequest, String orderNumber) {
        OrderRequest.UserDetails userDetails = orderRequest.userDetails();
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderNumber(orderNumber);
        event.setEmail(userDetails.email());
        event.setFirstName(userDetails.firstName());
        event.setLastName(userDetails.lastName());
        event.setItems(orderRequest.canonicalItems().stream()
                .map(item -> new com.merryl.ecommerce.order.event.OrderItem(
                        item.skuCode(), item.price().stripTrailingZeros().toPlainString(), item.quantity()))
                .toList());
        log.info("Sending OrderPlacedEvent to Kafka for order number: {}", orderNumber);
        kafkaTemplate.send("order-placed", event);
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

package com.merryl.ecommerce.order.service;

import com.merryl.ecommerce.order.client.InventoryClient;
import com.merryl.ecommerce.order.client.InventoryReservationRequest;
import com.merryl.ecommerce.order.client.InventoryReservationResponse;
import com.merryl.ecommerce.order.client.InventoryUnavailableException;
import com.merryl.ecommerce.order.dto.OrderItemRequest;
import com.merryl.ecommerce.order.dto.OrderRequest;
import com.merryl.ecommerce.order.event.OrderPlacedPayload;
import com.merryl.ecommerce.order.metrics.OrderBusinessMetrics;
import com.merryl.ecommerce.order.model.Order;
import com.merryl.ecommerce.order.model.OrderIdempotency;
import com.merryl.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderServiceTests {

    private OrderRepository orderRepository;
    private InventoryClient inventoryClient;
    private OrderIdempotencyService idempotencyService;
    private OrderPersistenceService orderPersistenceService;
    private OrderBusinessMetrics metrics;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        inventoryClient = mock(InventoryClient.class);
        idempotencyService = mock(OrderIdempotencyService.class);
        orderPersistenceService = mock(OrderPersistenceService.class);
        metrics = mock(OrderBusinessMetrics.class);
        orderService = new OrderService(
                orderRepository, inventoryClient, idempotencyService, orderPersistenceService, metrics);
        when(orderPersistenceService.saveAndComplete(any(), any())).thenReturn(true);
    }

    @Test
    void releasesReservationWhenOrderPersistenceFails() {
        OrderIdempotency idempotency = new OrderIdempotency();
        idempotency.setOrderNumber("order-123");
        idempotency.setStatus(OrderIdempotencyStatus.PROCESSING);
        when(idempotencyService.claim(eq("key-123"), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber("order-123")).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenReturn(new InventoryReservationResponse("order-123", "RESERVED", "RESERVED", "Reserved"));
        when(inventoryClient.release("order-123"))
                .thenReturn(new InventoryReservationResponse("order-123", "RELEASED", "RELEASED", "Released"));
        when(orderPersistenceService.saveAndComplete(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        when(idempotencyService.markCompensationPending(eq("order-123"), anyString())).thenReturn(true);

        assertThrows(OrderCreationException.class,
                () -> orderService.placeOrder(validRequest(), "key-123"));

        verify(inventoryClient).release("order-123");
        verify(idempotencyService).markCompensationPending(eq("order-123"), anyString());
        verify(idempotencyService).markCompensated(eq("order-123"), anyString());
    }

    @Test
    void doesNotCreateOrderOrPublishWhenInventoryRejectsReservation() {
        OrderIdempotency idempotency = new OrderIdempotency();
        idempotency.setOrderNumber("order-456");
        idempotency.setStatus(OrderIdempotencyStatus.PROCESSING);
        when(idempotencyService.claim(eq("key-456"), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber("order-456")).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenReturn(new InventoryReservationResponse(
                        "order-456", "REJECTED", "INSUFFICIENT_INVENTORY", "Insufficient stock"));

        assertThrows(InsufficientInventoryException.class,
                () -> orderService.placeOrder(validRequest(), "key-456"));

        verify(idempotencyService).markInventoryRejected(
                "order-456", "INSUFFICIENT_INVENTORY", "Insufficient stock");
        verifyNoInteractions(orderPersistenceService);
    }

    @Test
    void recordsCompensationAsPendingWhenInventoryReleaseIsUnavailable() {
        OrderIdempotency idempotency = new OrderIdempotency();
        idempotency.setOrderNumber("order-789");
        idempotency.setStatus(OrderIdempotencyStatus.PROCESSING);
        when(idempotencyService.claim(eq("key-789"), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber("order-789")).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenReturn(new InventoryReservationResponse("order-789", "RESERVED", "RESERVED", "Reserved"));
        when(orderPersistenceService.saveAndComplete(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        when(idempotencyService.markCompensationPending(eq("order-789"), anyString())).thenReturn(true);
        when(inventoryClient.release("order-789"))
                .thenThrow(new InventoryUnavailableException("inventory unavailable", null));

        assertThrows(CompensationPendingException.class,
                () -> orderService.placeOrder(validRequest(), "key-789"));

        verify(idempotencyService).markCompensationPending(eq("order-789"), anyString());
        verify(idempotencyService, never()).markCompensated(anyString(), anyString());
    }

    @Test
    void doesNotReleaseReservationWhenCompensationTransitionIsLost() {
        OrderIdempotency idempotency = processingRequest("order-race", "key-race");
        when(idempotencyService.claim(eq("key-race"), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber("order-race")).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenReturn(new InventoryReservationResponse("order-race", "RESERVED", "RESERVED", "Reserved"));
        when(orderPersistenceService.saveAndComplete(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        when(idempotencyService.markCompensationPending(eq("order-race"), anyString())).thenReturn(false);

        assertThrows(OrderStateTransitionException.class,
                () -> orderService.placeOrder(validRequest(), "key-race"));

        verify(inventoryClient, never()).release(anyString());
        verify(idempotencyService, never()).markCompensated(anyString(), anyString());
    }

    @Test
    void retryObservingPendingCompensationDoesNotReleaseReservation() {
        OrderIdempotency idempotency = processingRequest("order-pending", "key-pending");
        idempotency.setStatus(OrderIdempotencyStatus.COMPENSATION_PENDING);
        when(idempotencyService.claim(eq("key-pending"), anyString())).thenReturn(idempotency);

        assertThrows(CompensationPendingException.class,
                () -> orderService.placeOrder(validRequest(), "key-pending"));

        verifyNoInteractions(inventoryClient);
        verifyNoInteractions(orderPersistenceService);
    }

    @Test
    void successfulPersistenceWinsAgainstConcurrentFailedPersistenceAndCompensation() throws Exception {
        String orderNumber = "order-concurrent-" + UUID.randomUUID();
        String idempotencyKey = "key-concurrent-" + UUID.randomUUID();
        OrderIdempotency idempotency = processingRequest(orderNumber, idempotencyKey);
        when(idempotencyService.claim(eq(idempotencyKey), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber(orderNumber)).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenReturn(new InventoryReservationResponse(orderNumber, "RESERVED", "RESERVED", "Reserved"));

        AtomicReference<OrderIdempotencyStatus> finalStatus =
                new AtomicReference<>(OrderIdempotencyStatus.PROCESSING);
        AtomicReference<Order> persistedOrder = new AtomicReference<>();
        CountDownLatch successfulPersistenceCommitted = new CountDownLatch(1);
        AtomicInteger persistenceAttempt = new AtomicInteger();
        doAnswer(invocation -> {
            int attempt = persistenceAttempt.getAndIncrement();
            if (attempt == 0) {
                Order order = invocation.getArgument(0);
                persistedOrder.set(order);
                assertTrue(finalStatus.compareAndSet(
                        OrderIdempotencyStatus.PROCESSING, OrderIdempotencyStatus.SUCCEEDED));
                successfulPersistenceCommitted.countDown();
                return true;
            }

            assertTrue(successfulPersistenceCommitted.await(5, TimeUnit.SECONDS));
            throw new DataAccessResourceFailureException("competing persistence failed");
        }).when(orderPersistenceService).saveAndComplete(any(), any());
        when(idempotencyService.markCompensationPending(eq(orderNumber), anyString()))
                .thenAnswer(invocation -> finalStatus.compareAndSet(
                        OrderIdempotencyStatus.PROCESSING, OrderIdempotencyStatus.COMPENSATION_PENDING));
        when(idempotencyService.markSucceeded(orderNumber)).thenAnswer(invocation ->
                finalStatus.compareAndSet(
                        OrderIdempotencyStatus.PROCESSING, OrderIdempotencyStatus.SUCCEEDED));

        var executor = Executors.newFixedThreadPool(2);
        try {
            var requestOne = executor.submit(() -> placeAndCapture(idempotencyKey));
            var requestTwo = executor.submit(() -> placeAndCapture(idempotencyKey));

            var outcomes = java.util.Arrays.asList(
                    requestOne.get(10, TimeUnit.SECONDS),
                    requestTwo.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(outcome -> outcome == null).count());
            assertEquals(1, outcomes.stream()
                    .filter(OrderStateTransitionException.class::isInstance).count());
            assertEquals(OrderIdempotencyStatus.SUCCEEDED, finalStatus.get());
            assertEquals(orderNumber, persistedOrder.get().getOrderNumber());
            verify(inventoryClient, times(2)).reserve(argThat(
                    request -> orderNumber.equals(request.reservationId())));
            verify(inventoryClient, never()).release(anyString());
            verify(idempotencyService, times(1))
                    .markCompensationPending(eq(orderNumber), anyString());
            verify(orderPersistenceService, times(2)).saveAndComplete(any(), any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void inventoryFailureDoesNotCreateAnOrderOrReportInsufficientStock() {
        OrderIdempotency idempotency = new OrderIdempotency();
        idempotency.setOrderNumber("order-unavailable");
        idempotency.setStatus(OrderIdempotencyStatus.PROCESSING);
        when(idempotencyService.claim(eq("key-unavailable"), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber("order-unavailable")).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenThrow(new InventoryUnavailableException("inventory timed out", null));

        assertThrows(InventoryUnavailableException.class,
                () -> orderService.placeOrder(validRequest(), "key-unavailable"));

        verifyNoInteractions(orderPersistenceService);
        verify(inventoryClient, never()).release(anyString());
    }

    @Test
    void multiItemOrderUsesOneBatchReservationAndPersistsEveryLineAndAggregateTotal() {
        OrderIdempotency idempotency = processingRequest("order-batch", "key-batch");
        when(idempotencyService.claim(eq("key-batch"), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber("order-batch")).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenReturn(new InventoryReservationResponse("order-batch", "RESERVED", "RESERVED", "Reserved"));
        when(orderPersistenceService.saveAndComplete(any(), any())).thenReturn(true);

        OrderRequest request = new OrderRequest(null, null, null, null, null,
                new OrderRequest.UserDetails("Taylor", "Customer", "buyer@example.com"),
                List.of(new OrderItemRequest("IPHONE-15", new BigDecimal("799.00"), 2),
                        new OrderItemRequest("AIRPODS-PRO", new BigDecimal("249.00"), 1)));

        orderService.placeOrder(request, "key-batch");

        verify(inventoryClient, times(1)).reserve(argThat(reservation -> reservation.items() != null
                && reservation.items().size() == 2
                && reservation.items().getFirst().skuCode().equals("AIRPODS-PRO")));
        ArgumentCaptor<OrderPlacedPayload> payloadCaptor = ArgumentCaptor.forClass(OrderPlacedPayload.class);
        verify(orderPersistenceService).saveAndComplete(argThat(order ->
                order.getItems().size() == 2
                        && order.getItems().getFirst().getSkuCode().equals("AIRPODS-PRO")
                        && order.getPrice().compareTo(new BigDecimal("1847.00")) == 0
                        && order.getItems().stream()
                        .anyMatch(item -> item.getSkuCode().equals("IPHONE-15")
                                && item.getQuantity() == 2
                                && item.getLineTotal().compareTo(new BigDecimal("1598.00")) == 0)),
                payloadCaptor.capture());
        assertEquals(2, payloadCaptor.getValue().items().size());
        assertEquals("AIRPODS-PRO", payloadCaptor.getValue().items().getFirst().skuCode());
        assertEquals("249", payloadCaptor.getValue().items().getFirst().unitPrice());
        verify(metrics).orderCreated();
        verify(metrics).orderSucceeded();
    }

    @Test
    void duplicateSkuLinesAreRejectedBeforeInventoryReservation() {
        OrderRequest request = new OrderRequest(null, null, null, null, null,
                new OrderRequest.UserDetails("Taylor", "Customer", "buyer@example.com"),
                List.of(new OrderItemRequest("SKU-1", BigDecimal.ONE, 1),
                        new OrderItemRequest("SKU-1", BigDecimal.TEN, 2)));

        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(request, "key-duplicate"));

        verifyNoInteractions(inventoryClient);
        verifyNoInteractions(orderPersistenceService);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void persistenceFailureReleasesTheWholeMultiItemReservationByStableOrderNumber() {
        OrderIdempotency idempotency = processingRequest("order-batch-failed", "key-batch-failed");
        when(idempotencyService.claim(eq("key-batch-failed"), anyString())).thenReturn(idempotency);
        when(orderRepository.findByOrderNumber("order-batch-failed")).thenReturn(Optional.empty());
        when(inventoryClient.reserve(any(InventoryReservationRequest.class)))
                .thenReturn(new InventoryReservationResponse(
                        "order-batch-failed", "RESERVED", "RESERVED", "Reserved"));
        when(orderPersistenceService.saveAndComplete(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        when(idempotencyService.markCompensationPending(eq("order-batch-failed"), anyString())).thenReturn(true);
        when(inventoryClient.release("order-batch-failed"))
                .thenReturn(new InventoryReservationResponse(
                        "order-batch-failed", "RELEASED", "RELEASED", "Released"));

        OrderRequest request = new OrderRequest(null, null, null, null, null,
                new OrderRequest.UserDetails("Taylor", "Customer", "buyer@example.com"),
                List.of(new OrderItemRequest("SKU-A", BigDecimal.TEN, 1),
                        new OrderItemRequest("SKU-B", BigDecimal.ONE, 2)));

        assertThrows(OrderCreationException.class, () -> orderService.placeOrder(request, "key-batch-failed"));

        verify(inventoryClient, times(1)).reserve(argThat(reservation -> reservation.items() != null
                && reservation.items().size() == 2));
        verify(inventoryClient, times(1)).release("order-batch-failed");
        verify(idempotencyService).markCompensated(eq("order-batch-failed"), anyString());
    }

    private OrderRequest validRequest() {
        return new OrderRequest(null, null, "SKU-1", new java.math.BigDecimal("12.00"), 1,
                new OrderRequest.UserDetails("Taylor", "Customer", "buyer@example.com"));
    }

    private OrderIdempotency processingRequest(String orderNumber, String key) {
        OrderIdempotency idempotency = new OrderIdempotency();
        idempotency.setOrderNumber(orderNumber);
        idempotency.setIdempotencyKey(key);
        idempotency.setRequestHash("request-hash");
        idempotency.setStatus(OrderIdempotencyStatus.PROCESSING);
        return idempotency;
    }

    private RuntimeException placeAndCapture(String idempotencyKey) {
        try {
            orderService.placeOrder(validRequest(), idempotencyKey);
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }
}

package com.merryl.ecommerce.order.controller;

import com.merryl.ecommerce.order.dto.OrderRequest;
import com.merryl.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor// to inject order service
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String placeOrder(
            @RequestBody OrderRequest orderRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        orderService.placeOrder(orderRequest, idempotencyKey);
        return "Order Placed Successfully";
    }
}

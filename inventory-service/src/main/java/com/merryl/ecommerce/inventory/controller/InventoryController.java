package com.merryl.ecommerce.inventory.controller;

import com.merryl.ecommerce.inventory.service.InventoryService;
import com.merryl.ecommerce.inventory.dto.InventoryReservationRequest;
import com.merryl.ecommerce.inventory.dto.InventoryReservationResponse;
import com.merryl.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.merryl.ecommerce.inventory.dto.InventoryStockRequest;
import com.merryl.ecommerce.inventory.dto.InventoryStockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public boolean isInStock(@RequestParam String skuCode, @RequestParam Integer quantity) {
        return inventoryService.isInStock(skuCode, quantity);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.OK)
    public InventoryReservationResponse reserve(@RequestBody InventoryReservationRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/reservations/{reservationId}/release")
    @ResponseStatus(HttpStatus.OK)
    public InventoryReservationResponse release(@PathVariable String reservationId) {
        return inventoryService.release(reservationId);
    }

    @GetMapping("/stock")
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryStockResponse> getStock() {
        return inventoryService.getStock();
    }

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryStockResponse initializeStock(@RequestBody InventoryStockRequest request) {
        return inventoryService.initializeStock(request);
    }

    @PostMapping("/stock/{skuCode}/add")
    @ResponseStatus(HttpStatus.OK)
    public InventoryStockResponse addStock(
            @PathVariable String skuCode, @RequestBody InventoryAdjustmentRequest request) {
        return inventoryService.addStock(skuCode, request == null ? null : request.quantity());
    }

    @PostMapping("/stock/{skuCode}/remove")
    @ResponseStatus(HttpStatus.OK)
    public InventoryStockResponse removeStock(
            @PathVariable String skuCode, @RequestBody InventoryAdjustmentRequest request) {
        return inventoryService.removeStock(skuCode, request == null ? null : request.quantity());
    }
}

package com.merryl.ecommerce.inventory.controller;

import com.merryl.ecommerce.inventory.service.InventoryService;
import com.merryl.ecommerce.inventory.dto.InventoryReservationRequest;
import com.merryl.ecommerce.inventory.dto.InventoryReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}

package com.merryl.ecommerce.inventory.service;

import com.merryl.ecommerce.inventory.dto.InventoryReservationRequest;
import com.merryl.ecommerce.inventory.dto.InventoryReservationResponse;
import com.merryl.ecommerce.inventory.model.Inventory;
import com.merryl.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationService inventoryReservationService;

    public boolean isInStock(String skuCode, Integer quantity) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new IllegalArgumentException("skuCode must not be blank");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        return inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode, quantity);
    }

    public InventoryReservationResponse reserve(InventoryReservationRequest request) {
        return inventoryReservationService.reserve(request);
    }

    public InventoryReservationResponse release(String reservationId) {
        return inventoryReservationService.release(reservationId);
    }
}

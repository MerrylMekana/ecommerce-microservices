package com.merryl.ecommerce.inventory.service;

import com.merryl.ecommerce.inventory.dto.InventoryReservationRequest;
import com.merryl.ecommerce.inventory.dto.InventoryReservationResponse;
import com.merryl.ecommerce.inventory.dto.InventoryStockRequest;
import com.merryl.ecommerce.inventory.dto.InventoryStockResponse;
import com.merryl.ecommerce.inventory.model.Inventory;
import com.merryl.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.List;

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

    @Transactional(readOnly = true)
    public List<InventoryStockResponse> getStock() {
        return inventoryRepository.findAllByOrderBySkuCodeAsc().stream()
                .map(inventory -> new InventoryStockResponse(inventory.getSkuCode(), inventory.getQuantity()))
                .toList();
    }

    @Transactional
    public InventoryStockResponse initializeStock(InventoryStockRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("inventory stock request is required");
        }
        validateSku(request.skuCode());
        if (request.quantity() == null || request.quantity() < 0) {
            throw new IllegalArgumentException("quantity must be greater than or equal to zero");
        }

        var existing = inventoryRepository.findBySkuCodeForUpdate(request.skuCode());
        if (existing.isPresent()) {
            if (Objects.equals(existing.get().getQuantity(), request.quantity())) {
                return response(existing.get());
            }
            throw new InventoryConflictException(
                    "Inventory already exists for SKU " + request.skuCode()
                            + " with a different quantity");
        }

        Inventory inventory = new Inventory(null, request.skuCode(), request.quantity());
        try {
            return response(inventoryRepository.saveAndFlush(inventory));
        } catch (DataIntegrityViolationException exception) {
            throw new InventoryConflictException(
                    "Inventory could not be initialized because a record already exists for this SKU", exception);
        }
    }

    @Transactional
    public InventoryStockResponse addStock(String skuCode, Integer quantity) {
        validateAdjustment(skuCode, quantity);
        Inventory inventory = inventoryRepository.findBySkuCodeForUpdate(skuCode)
                .orElseThrow(() -> new InventoryNotFoundException(skuCode));
        long updatedQuantity = (long) inventory.getQuantity() + quantity;
        if (updatedQuantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("resulting quantity exceeds the supported maximum");
        }
        inventory.setQuantity((int) updatedQuantity);
        return response(inventoryRepository.save(inventory));
    }

    @Transactional
    public InventoryStockResponse removeStock(String skuCode, Integer quantity) {
        validateAdjustment(skuCode, quantity);
        Inventory inventory = inventoryRepository.findBySkuCodeForUpdate(skuCode)
                .orElseThrow(() -> new InventoryNotFoundException(skuCode));
        if (inventory.getQuantity() < quantity) {
            throw new InsufficientStockException(skuCode);
        }
        inventory.setQuantity(inventory.getQuantity() - quantity);
        return response(inventoryRepository.save(inventory));
    }

    private void validateAdjustment(String skuCode, Integer quantity) {
        validateSku(skuCode);
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    private void validateSku(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new IllegalArgumentException("skuCode must not be blank");
        }
    }

    private InventoryStockResponse response(Inventory inventory) {
        return new InventoryStockResponse(inventory.getSkuCode(), inventory.getQuantity());
    }
}

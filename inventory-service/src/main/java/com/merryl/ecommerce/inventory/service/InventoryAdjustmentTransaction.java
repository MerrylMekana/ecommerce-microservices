package com.merryl.ecommerce.inventory.service;

import com.merryl.ecommerce.inventory.dto.InventoryStockResponse;
import com.merryl.ecommerce.inventory.model.Inventory;
import com.merryl.ecommerce.inventory.model.InventoryAdjustment;
import com.merryl.ecommerce.inventory.model.InventoryAdjustmentStatus;
import com.merryl.ecommerce.inventory.model.InventoryAdjustmentType;
import com.merryl.ecommerce.inventory.repository.InventoryAdjustmentRepository;
import com.merryl.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentTransaction {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;

    @Transactional
    public InventoryAdjustment apply(
            String operationKey, String skuCode, InventoryAdjustmentType operation, Integer quantity) {
        var existing = adjustmentRepository.findByOperationKey(operationKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Inventory inventory = inventoryRepository.findBySkuCodeForUpdate(skuCode)
                .orElseThrow(() -> new InventoryNotFoundException(skuCode));
        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setOperationKey(operationKey);
        adjustment.setSkuCode(skuCode);
        adjustment.setOperation(operation);
        adjustment.setQuantity(quantity);

        if (operation == InventoryAdjustmentType.REMOVE && inventory.getQuantity() < quantity) {
            adjustment.setStatus(InventoryAdjustmentStatus.REJECTED);
            adjustment.setErrorCode("INSUFFICIENT_STOCK");
            adjustment.setResultQuantity(inventory.getQuantity());
            return adjustmentRepository.saveAndFlush(adjustment);
        }

        long updatedQuantity = operation == InventoryAdjustmentType.ADD
                ? (long) inventory.getQuantity() + quantity
                : inventory.getQuantity() - quantity;
        if (updatedQuantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("resulting quantity exceeds the supported maximum");
        }
        inventory.setQuantity((int) updatedQuantity);
        inventoryRepository.saveAndFlush(inventory);

        adjustment.setStatus(InventoryAdjustmentStatus.APPLIED);
        adjustment.setResultQuantity(inventory.getQuantity());
        return adjustmentRepository.saveAndFlush(adjustment);
    }

    public InventoryStockResponse response(InventoryAdjustment adjustment) {
        return new InventoryStockResponse(adjustment.getSkuCode(), adjustment.getResultQuantity());
    }
}

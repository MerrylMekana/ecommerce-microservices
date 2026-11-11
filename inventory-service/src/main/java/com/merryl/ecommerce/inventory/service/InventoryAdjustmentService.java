package com.merryl.ecommerce.inventory.service;

import com.merryl.ecommerce.inventory.dto.InventoryStockResponse;
import com.merryl.ecommerce.inventory.model.InventoryAdjustment;
import com.merryl.ecommerce.inventory.model.InventoryAdjustmentStatus;
import com.merryl.ecommerce.inventory.model.InventoryAdjustmentType;
import com.merryl.ecommerce.inventory.metrics.InventoryBusinessMetrics;
import com.merryl.ecommerce.inventory.repository.InventoryAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentTransaction adjustmentTransaction;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryBusinessMetrics metrics;

    public InventoryStockResponse addStock(String operationKey, String skuCode, Integer quantity) {
        return adjust(operationKey, skuCode, quantity, InventoryAdjustmentType.ADD);
    }

    public InventoryStockResponse removeStock(String operationKey, String skuCode, Integer quantity) {
        return adjust(operationKey, skuCode, quantity, InventoryAdjustmentType.REMOVE);
    }

    private InventoryStockResponse adjust(
            String operationKey, String skuCode, Integer quantity, InventoryAdjustmentType operation) {
        validate(operationKey, skuCode, quantity);

        InventoryAdjustment adjustment;
        boolean replay = adjustmentRepository.existsByOperationKey(operationKey);
        try {
            adjustment = adjustmentTransaction.apply(operationKey, skuCode, operation, quantity);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            replay = true;
            adjustment = adjustmentRepository.findByOperationKey(operationKey)
                    .orElseThrow(() -> concurrentDuplicate);
        }

        if (!adjustment.getSkuCode().equals(skuCode)
                || adjustment.getOperation() != operation
                || !adjustment.getQuantity().equals(quantity)) {
            throw new InventoryAdjustmentConflictException(operationKey);
        }
        if (replay) {
            metrics.adjustmentReplay();
        }
        if (adjustment.getStatus() == InventoryAdjustmentStatus.REJECTED) {
            throw new InsufficientStockException(skuCode);
        }
        return adjustmentTransaction.response(adjustment);
    }

    private void validate(String operationKey, String skuCode, Integer quantity) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 1 and 128 characters");
        }
        if (skuCode == null || skuCode.isBlank()) {
            throw new IllegalArgumentException("skuCode must not be blank");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }
}

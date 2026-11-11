package com.merryl.ecommerce.inventory.repository;

import com.merryl.ecommerce.inventory.model.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {
    Optional<InventoryAdjustment> findByOperationKey(String operationKey);

    boolean existsByOperationKey(String operationKey);
}

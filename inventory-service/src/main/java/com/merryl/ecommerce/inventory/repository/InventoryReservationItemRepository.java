package com.merryl.ecommerce.inventory.repository;

import com.merryl.ecommerce.inventory.model.InventoryReservationItem;
import com.merryl.ecommerce.inventory.model.InventoryReservationItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationItemRepository
        extends JpaRepository<InventoryReservationItem, InventoryReservationItemId> {

    List<InventoryReservationItem> findByIdReservationIdOrderByIdSkuCode(String reservationId);
}

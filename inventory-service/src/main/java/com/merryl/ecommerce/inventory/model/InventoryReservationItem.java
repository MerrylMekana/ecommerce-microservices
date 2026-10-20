package com.merryl.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "table_inventory_reservation_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryReservationItem {

    @EmbeddedId
    private InventoryReservationItemId id;

    @Column(nullable = false)
    private Integer quantity;

    public InventoryReservationItem(String reservationId, String skuCode, Integer quantity) {
        this.id = new InventoryReservationItemId(reservationId, skuCode);
        this.quantity = quantity;
    }

    public String getReservationId() {
        return id.getReservationId();
    }

    public String getSkuCode() {
        return id.getSkuCode();
    }
}

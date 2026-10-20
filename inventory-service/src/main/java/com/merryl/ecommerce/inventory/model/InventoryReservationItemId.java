package com.merryl.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationItemId implements Serializable {

    @Column(name = "reservation_id", nullable = false, length = 64)
    private String reservationId;

    @Column(name = "sku_code", nullable = false, length = 255)
    private String skuCode;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InventoryReservationItemId that)) {
            return false;
        }
        return Objects.equals(reservationId, that.reservationId)
                && Objects.equals(skuCode, that.skuCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, skuCode);
    }
}

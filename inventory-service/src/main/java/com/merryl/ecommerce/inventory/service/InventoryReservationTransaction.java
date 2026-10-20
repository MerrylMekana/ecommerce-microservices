package com.merryl.ecommerce.inventory.service;

import com.merryl.ecommerce.inventory.dto.InventoryReservationItemRequest;
import com.merryl.ecommerce.inventory.dto.InventoryReservationResponse;
import com.merryl.ecommerce.inventory.model.InventoryReservation;
import com.merryl.ecommerce.inventory.model.InventoryReservationItem;
import com.merryl.ecommerce.inventory.model.InventoryReservationStatus;
import com.merryl.ecommerce.inventory.repository.InventoryRepository;
import com.merryl.ecommerce.inventory.repository.InventoryReservationItemRepository;
import com.merryl.ecommerce.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryReservationTransaction {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationItemRepository reservationItemRepository;

    @Transactional
    public InventoryReservationResponse reserve(
            String reservationId, List<InventoryReservationItemRequest> items, String requestHash) {
        var existing = reservationRepository.findByReservationId(reservationId);
        if (existing.isPresent()) {
            return existingResponse(existing.get(), requestHash);
        }

        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId(reservationId);
        reservation.setRequestHash(requestHash);

        List<String> skuCodes = items.stream().map(InventoryReservationItemRequest::skuCode).toList();
        List<com.merryl.ecommerce.inventory.model.Inventory> lockedInventories =
                inventoryRepository.findAllBySkuCodeInForUpdate(skuCodes);
        Map<String, com.merryl.ecommerce.inventory.model.Inventory> inventoryBySku =
                lockedInventories.stream().collect(Collectors.toMap(
                        inventory -> inventory.getSkuCode().toLowerCase(Locale.ROOT), Function.identity()));

        for (InventoryReservationItemRequest item : items) {
            var inventory = inventoryBySku.get(item.skuCode().toLowerCase(Locale.ROOT));
            if (inventory == null) {
                reservation.setStatus(InventoryReservationStatus.REJECTED);
                reservation.setFailureCode("SKU_NOT_FOUND");
                reservation.setFailureMessage("No inventory record exists for SKU " + item.skuCode());
                return saveRejected(reservation, items);
            }
            if (inventory.getQuantity() < item.quantity()) {
                reservation.setStatus(InventoryReservationStatus.REJECTED);
                reservation.setFailureCode("INSUFFICIENT_INVENTORY");
                reservation.setFailureMessage("Insufficient inventory for SKU " + item.skuCode());
                return saveRejected(reservation, items);
            }
        }

        for (InventoryReservationItemRequest item : items) {
            if (inventoryRepository.reserve(item.skuCode(), item.quantity()) != 1) {
                throw new IllegalStateException(
                        "Inventory changed while reserving SKU " + item.skuCode());
            }
        }
        reservation.setStatus(InventoryReservationStatus.RESERVED);
        reservationRepository.saveAndFlush(reservation);
        reservationItemRepository.saveAllAndFlush(toReservationItems(reservationId, items));
        return response(reservation, "RESERVED", "Inventory reserved");
    }

    @Transactional
    public InventoryReservationResponse release(String reservationId) {
        InventoryReservation reservation = reservationRepository.findByReservationIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
            return response(reservation, reservation.getStatus().name(), reservation.getFailureMessage());
        }

        List<InventoryReservationItem> items =
                reservationItemRepository.findByIdReservationIdOrderByIdSkuCode(reservationId);
        List<String> skuCodes = items.stream().map(InventoryReservationItem::getSkuCode).toList();
        List<com.merryl.ecommerce.inventory.model.Inventory> lockedInventories =
                inventoryRepository.findAllBySkuCodeInForUpdate(skuCodes);
        if (lockedInventories.size() != items.size()) {
            throw new IllegalStateException("Inventory record missing while releasing reservation " + reservationId);
        }
        for (InventoryReservationItem item : items) {
            if (inventoryRepository.release(item.getSkuCode(), item.getQuantity()) != 1) {
                throw new IllegalStateException(
                        "Inventory record missing while releasing reservation " + reservationId);
            }
        }

        reservation.setStatus(InventoryReservationStatus.RELEASED);
        reservationRepository.save(reservation);
        return response(reservation, "RELEASED", "Inventory reservation released");
    }

    private InventoryReservationResponse existingResponse(InventoryReservation reservation, String requestHash) {
        if (!reservation.getRequestHash().equals(requestHash)) {
            throw new ReservationConflictException(reservation.getReservationId());
        }
        String message = reservation.getFailureMessage();
        if (message == null && reservation.getStatus() == InventoryReservationStatus.RESERVED) {
            message = "Inventory reserved";
        } else if (message == null && reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            message = "Inventory reservation released";
        }
        return response(reservation, reservation.getFailureCode() == null
                ? reservation.getStatus().name()
                : reservation.getFailureCode(), message);
    }

    private InventoryReservationResponse saveRejected(
            InventoryReservation reservation, List<InventoryReservationItemRequest> items) {
        reservationRepository.saveAndFlush(reservation);
        reservationItemRepository.saveAllAndFlush(toReservationItems(reservation.getReservationId(), items));
        return response(reservation, reservation.getFailureCode(), reservation.getFailureMessage());
    }

    private List<InventoryReservationItem> toReservationItems(
            String reservationId, List<InventoryReservationItemRequest> items) {
        return items.stream()
                .map(item -> new InventoryReservationItem(reservationId, item.skuCode(), item.quantity()))
                .toList();
    }

    private InventoryReservationResponse response(
            InventoryReservation reservation, String code, String message) {
        return new InventoryReservationResponse(reservation.getReservationId(), reservation.getStatus().name(),
                code, message);
    }
}

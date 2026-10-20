package com.merryl.ecommerce.inventory.service;

import com.merryl.ecommerce.inventory.dto.InventoryReservationRequest;
import com.merryl.ecommerce.inventory.dto.InventoryReservationResponse;
import com.merryl.ecommerce.inventory.model.InventoryReservation;
import com.merryl.ecommerce.inventory.dto.InventoryReservationItemRequest;
import com.merryl.ecommerce.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final InventoryReservationTransaction reservationTransaction;
    private final InventoryReservationRepository reservationRepository;

    public InventoryReservationResponse reserve(InventoryReservationRequest request) {
        List<InventoryReservationItemRequest> items = validateAndCanonicalize(request);
        String requestHash = hash(canonicalRequest(items));

        try {
            return reservationTransaction.reserve(request.reservationId(), items, requestHash);
        } catch (DataIntegrityViolationException collision) {
            InventoryReservation existing = reservationRepository.findByReservationId(request.reservationId())
                    .orElseThrow(() -> collision);
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ReservationConflictException(request.reservationId());
            }
            return existingResponse(existing);
        }
    }

    public InventoryReservationResponse release(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId must not be blank");
        }
        return reservationTransaction.release(reservationId);
    }

    private List<InventoryReservationItemRequest> validateAndCanonicalize(InventoryReservationRequest request) {
        if (request == null || request.reservationId() == null || request.reservationId().isBlank()) {
            throw new IllegalArgumentException("reservationId must not be blank");
        }
        if (request.reservationId().length() > 64) {
            throw new IllegalArgumentException("reservationId must not exceed 64 characters");
        }
        List<InventoryReservationItemRequest> items = request.canonicalItems();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        if (items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("items must not contain null entries");
        }
        if (items.stream().anyMatch(item -> item.skuCode() == null || item.skuCode().isBlank())) {
            throw new IllegalArgumentException("skuCode must not be blank");
        }
        if (items.stream().anyMatch(item -> item.skuCode().length() > 255)) {
            throw new IllegalArgumentException("skuCode must not exceed 255 characters");
        }
        if (items.stream().anyMatch(item -> item.quantity() == null || item.quantity() <= 0)) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        var skuCodes = new HashSet<String>();
        if (items.stream().anyMatch(item ->
                !skuCodes.add(item.skuCode().toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("duplicate SKU lines are not allowed");
        }
        return items.stream().sorted(Comparator.comparing(InventoryReservationItemRequest::skuCode)).toList();
    }

    private String canonicalRequest(List<InventoryReservationItemRequest> items) {
        if (items.size() == 1) {
            return items.getFirst().skuCode() + "\n" + items.getFirst().quantity();
        }
        return items.stream()
                .map(item -> item.skuCode().length() + ":" + item.skuCode()
                        + item.quantity().toString().length() + ":" + item.quantity())
                .reduce((first, second) -> first + second)
                .orElseThrow();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private InventoryReservationResponse existingResponse(InventoryReservation reservation) {
        String message = reservation.getFailureMessage();
        if (message == null && reservation.getStatus()
                == com.merryl.ecommerce.inventory.model.InventoryReservationStatus.RESERVED) {
            message = "Inventory reserved";
        } else if (message == null && reservation.getStatus()
                == com.merryl.ecommerce.inventory.model.InventoryReservationStatus.RELEASED) {
            message = "Inventory reservation released";
        }
        return new InventoryReservationResponse(reservation.getReservationId(), reservation.getStatus().name(),
                reservation.getFailureCode() == null ? reservation.getStatus().name() : reservation.getFailureCode(),
                message);
    }
}

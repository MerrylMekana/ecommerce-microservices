package com.merryl.ecommerce.inventory.service;

public class ReservationConflictException extends RuntimeException {
    public ReservationConflictException(String reservationId) {
        super("Reservation ID " + reservationId + " was already used for a different request");
    }
}

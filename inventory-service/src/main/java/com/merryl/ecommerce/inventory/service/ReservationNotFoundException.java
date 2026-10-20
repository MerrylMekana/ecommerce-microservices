package com.merryl.ecommerce.inventory.service;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String reservationId) {
        super("Reservation " + reservationId + " was not found");
    }
}

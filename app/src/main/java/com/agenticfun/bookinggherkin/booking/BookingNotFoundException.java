package com.agenticfun.bookinggherkin.booking;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingReference) {
        super("Booking not found: " + bookingReference);
    }

    public BookingNotFoundException(long bookingId) {
        super("Booking not found: " + bookingId);
    }
}

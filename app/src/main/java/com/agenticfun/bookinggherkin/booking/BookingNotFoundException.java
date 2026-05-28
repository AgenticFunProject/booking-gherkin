package com.agenticfun.bookinggherkin.booking;

public class BookingNotFoundException extends RuntimeException {

    private BookingNotFoundException(String message) {
        super(message);
    }

    public static BookingNotFoundException withId(long id) {
        return new BookingNotFoundException("Booking not found with id " + id);
    }

    public static BookingNotFoundException withReference(String bookingReference) {
        return new BookingNotFoundException("Booking not found with reference " + bookingReference);
    }

    public BookingNotFoundException(long bookingId) {
        super("Booking not found: " + bookingId);
    }
}

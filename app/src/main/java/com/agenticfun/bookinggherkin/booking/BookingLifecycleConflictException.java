package com.agenticfun.bookinggherkin.booking;

public class BookingLifecycleConflictException extends RuntimeException {

    public BookingLifecycleConflictException(long bookingId, BookingStatus currentStatus, BookingStatus targetStatus) {
        super("Cannot transition booking " + bookingId + " from " + currentStatus + " to " + targetStatus);
    }
}

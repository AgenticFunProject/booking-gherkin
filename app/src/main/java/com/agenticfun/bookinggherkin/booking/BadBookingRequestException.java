package com.agenticfun.bookinggherkin.booking;

public class BadBookingRequestException extends RuntimeException {

    public BadBookingRequestException(String message) {
        super(message);
    }
}

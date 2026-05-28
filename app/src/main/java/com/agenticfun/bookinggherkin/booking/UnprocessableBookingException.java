package com.agenticfun.bookinggherkin.booking;

public class UnprocessableBookingException extends RuntimeException {

    public UnprocessableBookingException(String message) {
        super(message);
    }
}

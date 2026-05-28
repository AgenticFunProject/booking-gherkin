package com.agenticfun.bookinggherkin.booking;

public interface EquipmentClient {

    void reserve(BookingResponse booking);

    void release(BookingResponse booking);
}

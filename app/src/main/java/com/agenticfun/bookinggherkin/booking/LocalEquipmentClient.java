package com.agenticfun.bookinggherkin.booking;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalEquipmentClient implements EquipmentClient {

    private final Set<Long> reservedBookingIds = ConcurrentHashMap.newKeySet();

    @Override
    public void reserve(BookingResponse booking) {
        reservedBookingIds.add(booking.id());
    }

    @Override
    public void release(BookingResponse booking) {
        reservedBookingIds.remove(booking.id());
    }

    boolean hasReservation(long bookingId) {
        return reservedBookingIds.contains(bookingId);
    }
}

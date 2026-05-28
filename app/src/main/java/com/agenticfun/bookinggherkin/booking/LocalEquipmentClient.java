package com.agenticfun.bookinggherkin.booking;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalEquipmentClient implements EquipmentClient {

    private final Set<Long> reservedBookingIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> releaseAttemptBookingIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> failedReleaseAttemptBookingIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> releaseFailureBookingIds = ConcurrentHashMap.newKeySet();

    @Override
    public void reserve(BookingResponse booking) {
        reservedBookingIds.add(booking.id());
    }

    @Override
    public void release(BookingResponse booking) {
        releaseAttemptBookingIds.add(booking.id());
        if (releaseFailureBookingIds.contains(booking.id())) {
            failedReleaseAttemptBookingIds.add(booking.id());
            throw new IllegalStateException("Local equipment release failed for booking " + booking.id());
        }
        reservedBookingIds.remove(booking.id());
    }

    boolean hasReservation(long bookingId) {
        return reservedBookingIds.contains(bookingId);
    }

    boolean hasReleaseAttempt(long bookingId) {
        return releaseAttemptBookingIds.contains(bookingId);
    }

    boolean hasFailedReleaseAttempt(long bookingId) {
        return failedReleaseAttemptBookingIds.contains(bookingId);
    }

    void failReleaseFor(long bookingId) {
        releaseFailureBookingIds.add(bookingId);
    }

    void clear() {
        reservedBookingIds.clear();
        releaseAttemptBookingIds.clear();
        failedReleaseAttemptBookingIds.clear();
        releaseFailureBookingIds.clear();
    }
}

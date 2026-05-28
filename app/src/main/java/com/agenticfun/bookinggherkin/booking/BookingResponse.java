package com.agenticfun.bookinggherkin.booking;

import java.time.Instant;
import java.util.List;

public record BookingResponse(
        long id,
        long customerId,
        long scheduleId,
        long quoteId,
        CustomerDetails customer,
        CargoDetails cargo,
        List<EquipmentLine> equipment,
        BookingStatus status,
        String bookingReference,
        Instant createdAt,
        Instant updatedAt) {
}

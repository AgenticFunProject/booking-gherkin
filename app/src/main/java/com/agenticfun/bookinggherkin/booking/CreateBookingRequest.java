package com.agenticfun.bookinggherkin.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateBookingRequest(
        @NotNull Long customerId,
        @NotNull Long scheduleId,
        @NotNull Long quoteId,
        @Valid @NotNull CustomerDetails customer,
        @Valid @NotNull CargoDetails cargo,
        @Valid @NotNull List<EquipmentLine> equipment) {
}

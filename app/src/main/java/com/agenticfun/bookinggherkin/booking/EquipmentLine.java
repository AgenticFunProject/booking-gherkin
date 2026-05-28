package com.agenticfun.bookinggherkin.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EquipmentLine(
        @NotBlank String type,
        @NotNull @Min(1) Integer quantity) {
}

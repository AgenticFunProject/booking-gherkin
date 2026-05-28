package com.agenticfun.bookinggherkin.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerDetails(
        @NotBlank String name,
        @Email @NotBlank String email,
        String phone) {
}

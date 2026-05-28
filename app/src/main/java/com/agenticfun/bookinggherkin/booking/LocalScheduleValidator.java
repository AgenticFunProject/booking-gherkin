package com.agenticfun.bookinggherkin.booking;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalScheduleValidator implements ExternalBookingValidator {

    @Override
    public void validate(CreateBookingRequest request) {
        // The local profile intentionally accepts schedule IDs for first-slice contract tests.
    }
}

package com.agenticfun.bookinggherkin.booking;

import java.util.List;

public record BookingPageResponse(
        List<BookingResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}

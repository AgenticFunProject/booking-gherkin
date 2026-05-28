package com.agenticfun.bookinggherkin.booking;

import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping({"/bookings", "/api/v1/bookings"})
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "X-Local-Dependency-Behavior", required = false) String localDependencyBehavior,
            @RequestHeader(value = "X-Trigger-Internal-Failure", required = false) String internalFailure) {
        if ("true".equalsIgnoreCase(internalFailure)) {
            throw new IllegalStateException("Simulated internal failure for error contract verification");
        }
        rejectConfiguredLocalDependencyFailure(localDependencyBehavior);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping("/bookings/{bookingReference}")
    public BookingResponse getByReference(@PathVariable String bookingReference) {
        return bookingService.getByIdentifier(bookingReference);
    }

    @GetMapping("/api/v1/bookings")
    public BookingPageResponse list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return bookingService.list(customerId, status, page, size, sort);
    }

    @GetMapping("/api/v1/bookings/{bookingIdentifier}")
    public BookingResponse getByIdentifier(@PathVariable String bookingIdentifier) {
        return bookingService.getByIdentifier(bookingIdentifier);
    }

    @PostMapping("/api/v1/bookings/{bookingId}/confirm")
    public BookingResponse confirm(@PathVariable long bookingId) {
        return bookingService.confirm(bookingId);
    }

    @PostMapping("/api/v1/bookings/{bookingId}/start")
    public BookingResponse start(@PathVariable long bookingId) {
        return bookingService.start(bookingId);
    }

    @PostMapping("/api/v1/bookings/{bookingId}/complete")
    public BookingResponse complete(@PathVariable long bookingId) {
        return bookingService.complete(bookingId);
    }

    @PostMapping("/api/v1/bookings/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable long bookingId) {
        return bookingService.cancel(bookingId);
    }

    private static void rejectConfiguredLocalDependencyFailure(String behavior) {
        if (behavior == null || behavior.isBlank()) {
            return;
        }
        switch (behavior.toLowerCase(Locale.ROOT)) {
            case "unavailable schedule" -> throw new UnprocessableBookingException("Selected schedule is unavailable");
            case "invalid quote" -> throw new UnprocessableBookingException("Selected quote is invalid");
            case "equipment reservation failure" ->
                    throw new IntegrationUnavailableException("equipment reservation service is unavailable");
            default -> throw new BadBookingRequestException("Unknown local dependency behavior: " + behavior);
        }
    }
}

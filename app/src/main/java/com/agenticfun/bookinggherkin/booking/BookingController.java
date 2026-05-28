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
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookingService;
    private final BookingAuthorizer authorizer;

    public BookingController(BookingService bookingService, BookingAuthorizer authorizer) {
        this.bookingService = bookingService;
        this.authorizer = authorizer;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "X-Local-Dependency-Behavior", required = false) String localDependencyBehavior,
            @RequestHeader(value = "X-Trigger-Internal-Failure", required = false) String internalFailure) {
        authorizer.requireCanCreate(request.customerId());
        maybeTriggerError(localDependencyBehavior, internalFailure);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping("/bookings/{bookingReference}")
    public BookingResponse getByReference(@PathVariable String bookingReference) {
        authorizer.requireCustomerIdentityForOwnedResource();
        BookingResponse booking = bookingService.getByIdentifier(bookingReference);
        authorizer.requireCanRead(booking);
        return booking;
    }

    static void maybeTriggerError(String localDependencyBehavior, String internalFailure) {
        if ("true".equalsIgnoreCase(internalFailure)) {
            throw new IllegalStateException("Simulated internal failure for error contract verification");
        }
        rejectConfiguredLocalDependencyFailure(localDependencyBehavior);
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

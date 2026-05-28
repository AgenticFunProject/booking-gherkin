package com.agenticfun.bookinggherkin.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
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

    @PostMapping("/api/v1/bookings")
    public ResponseEntity<BookingResponse> createV1(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
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
}

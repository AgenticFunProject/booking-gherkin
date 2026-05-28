package com.agenticfun.bookinggherkin.booking;

import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class ApiV1BookingController {

    private final BookingService bookingService;
    private final BookingAuthorizer authorizer;

    public ApiV1BookingController(BookingService bookingService, BookingAuthorizer authorizer) {
        this.bookingService = bookingService;
        this.authorizer = authorizer;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "X-Local-Dependency-Behavior", required = false) String localDependencyBehavior,
            @RequestHeader(value = "X-Trigger-Internal-Failure", required = false) String internalFailure) {
        authorizer.requireCanCreate(request.customerId());
        BookingController.maybeTriggerError(localDependencyBehavior, internalFailure);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping
    public BookingPageResponse list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Optional<Long> customerFilter = authorizer.requireCanList(customerId);
        return bookingService.list(customerFilter.orElse(null), status, page, size, sort);
    }

    @GetMapping("/{bookingIdentifier}")
    public BookingResponse getByIdentifier(@PathVariable String bookingIdentifier) {
        authorizer.requireCustomerIdentityForOwnedResource();
        BookingResponse booking = bookingService.getByIdentifier(bookingIdentifier);
        authorizer.requireCanRead(booking);
        return booking;
    }

    @RequestMapping(value = "/{id}/cancel", method = {RequestMethod.PATCH, RequestMethod.POST})
    public BookingResponse cancel(@PathVariable long id) {
        authorizer.requireCanAttemptCancel();
        BookingResponse booking = bookingService.getByIdentifier(Long.toString(id));
        authorizer.requireCanCancel(booking);
        return bookingService.cancel(id);
    }

    @RequestMapping(value = "/{id}/confirm", method = {RequestMethod.PATCH, RequestMethod.POST})
    public BookingResponse confirm(@PathVariable long id) {
        authorizer.requireCanOperate();
        return bookingService.confirm(id);
    }

    @RequestMapping(value = "/{id}/start", method = {RequestMethod.PATCH, RequestMethod.POST})
    public BookingResponse start(@PathVariable long id) {
        authorizer.requireCanOperate();
        return bookingService.start(id);
    }

    @RequestMapping(value = "/{id}/complete", method = {RequestMethod.PATCH, RequestMethod.POST})
    public BookingResponse complete(@PathVariable long id) {
        authorizer.requireCanOperate();
        return bookingService.complete(id);
    }
}

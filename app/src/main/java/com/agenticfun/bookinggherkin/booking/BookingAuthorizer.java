package com.agenticfun.bookinggherkin.booking;

import com.agenticfun.bookinggherkin.security.AuthenticatedCaller;
import com.agenticfun.bookinggherkin.security.BookingSecurityProperties;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class BookingAuthorizer {

    private final BookingSecurityProperties properties;

    public BookingAuthorizer(BookingSecurityProperties properties) {
        this.properties = properties;
    }

    public void requireCanCreate(long customerId) {
        if (!properties.isEnabled()) {
            return;
        }
        AuthenticatedCaller caller = caller();
        if (caller.hasRole("ADMIN") || caller.hasRole("SERVICE")) {
            return;
        }
        if (caller.hasRole("CUSTOMER") && ownsCustomer(caller, customerId)) {
            return;
        }
        throw forbidden();
    }

    public Optional<Long> requireCanList(Long requestedCustomerId) {
        if (!properties.isEnabled()) {
            return Optional.ofNullable(requestedCustomerId);
        }
        AuthenticatedCaller caller = caller();
        if (caller.hasRole("ADMIN") || caller.hasRole("OPERATOR") || caller.hasRole("SERVICE")) {
            return Optional.ofNullable(requestedCustomerId);
        }
        if (!caller.hasRole("CUSTOMER") || caller.customerId() == null) {
            throw forbidden();
        }
        if (requestedCustomerId == null) {
            throw new BadBookingRequestException("customerId query parameter is required for customer callers");
        }
        if (!ownsCustomer(caller, requestedCustomerId)) {
            throw forbidden();
        }
        return Optional.of(requestedCustomerId);
    }

    public void requireCanRead(BookingResponse booking) {
        if (!properties.isEnabled()) {
            return;
        }
        AuthenticatedCaller caller = caller();
        if (caller.hasRole("ADMIN") || caller.hasRole("OPERATOR") || caller.hasRole("SERVICE")) {
            return;
        }
        if (caller.hasRole("CUSTOMER") && ownsCustomer(caller, booking.customerId())) {
            return;
        }
        throw forbidden();
    }

    public void requireCustomerIdentityForOwnedResource() {
        if (!properties.isEnabled()) {
            return;
        }
        AuthenticatedCaller caller = caller();
        if (caller.hasRole("CUSTOMER") && caller.customerId() == null) {
            throw forbidden();
        }
    }

    public void requireCanAttemptCancel() {
        if (!properties.isEnabled()) {
            return;
        }
        AuthenticatedCaller caller = caller();
        if (caller.hasRole("OPERATOR")) {
            throw forbidden();
        }
        if (caller.hasRole("CUSTOMER") && caller.customerId() == null) {
            throw forbidden();
        }
    }

    public void requireCanCancel(BookingResponse booking) {
        if (!properties.isEnabled()) {
            return;
        }
        AuthenticatedCaller caller = caller();
        if (caller.hasRole("ADMIN") || caller.hasRole("SERVICE")) {
            return;
        }
        if (caller.hasRole("CUSTOMER") && ownsCustomer(caller, booking.customerId())) {
            return;
        }
        throw forbidden();
    }

    public void requireCanOperate() {
        if (!properties.isEnabled()) {
            return;
        }
        AuthenticatedCaller caller = caller();
        if (caller.hasRole("ADMIN") || caller.hasRole("OPERATOR")) {
            return;
        }
        throw forbidden();
    }

    private static boolean ownsCustomer(AuthenticatedCaller caller, long customerId) {
        return caller.customerId() != null && caller.customerId() == customerId;
    }

    private static AuthenticatedCaller caller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedCaller caller)) {
            throw forbidden();
        }
        return caller;
    }

    private static AccessDeniedException forbidden() {
        return new AccessDeniedException("Forbidden");
    }
}

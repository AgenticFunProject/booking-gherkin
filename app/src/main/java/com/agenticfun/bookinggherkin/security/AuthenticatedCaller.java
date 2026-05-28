package com.agenticfun.bookinggherkin.security;

public record AuthenticatedCaller(String role, Long customerId) {

    public boolean hasRole(String expectedRole) {
        return role != null && role.equals(expectedRole);
    }
}

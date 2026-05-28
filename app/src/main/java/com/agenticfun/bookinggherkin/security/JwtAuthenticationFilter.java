package com.agenticfun.bookinggherkin.security;

import com.agenticfun.bookinggherkin.web.ApiErrorFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final BookingSecurityProperties properties;
    private final DeterministicJwtDecoder jwtDecoder;
    private final ApiErrorFactory apiErrorFactory;

    public JwtAuthenticationFilter(
            BookingSecurityProperties properties,
            DeterministicJwtDecoder jwtDecoder,
            ApiErrorFactory apiErrorFactory) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
        this.apiErrorFactory = apiErrorFactory;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ")) {
            unauthorized(response, request);
            return;
        }

        AuthenticatedCaller caller;
        try {
            caller = jwtDecoder.decode(authorization.substring("Bearer ".length()));
        } catch (JwtAuthenticationException ex) {
            unauthorized(response, request);
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                caller,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + caller.role())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        apiErrorFactory.write(response, HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource",
                request);
    }
}

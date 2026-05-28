package com.agenticfun.bookinggherkin.booking;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class BookingExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField())
                .distinct()
                .sorted()
                .toList();

        Map<String, Object> body = errorBody(
                HttpStatus.BAD_REQUEST,
                "Validation failed for " + String.join(", ", fields),
                path(request));
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadBookingRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadBookingRequest(
            BadBookingRequestException ex,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            BookingNotFoundException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    @ExceptionHandler(BookingLifecycleConflictException.class)
    public ResponseEntity<Map<String, Object>> handleLifecycleConflict(
            BookingLifecycleConflictException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(HttpStatus.CONFLICT, ex.getMessage(), request));
    }

    private static Map<String, Object> errorBody(
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        Map<String, Object> body = errorBody(status, message, request.getRequestURI());
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isBlank()) {
            body.put("requestId", requestId);
        }
        return body;
    }

    private static Map<String, Object> errorBody(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        return body;
    }

    private static String path(WebRequest request) {
        String description = request.getDescription(false);
        if (description.startsWith("uri=")) {
            return description.substring("uri=".length());
        }
        return description;
    }
}

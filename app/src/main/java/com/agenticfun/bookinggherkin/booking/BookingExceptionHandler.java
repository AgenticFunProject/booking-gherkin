package com.agenticfun.bookinggherkin.booking;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<Map<String, Object>> violations = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(fieldError -> {
                    Map<String, Object> violation = new LinkedHashMap<>();
                    violation.put("field", fieldError.getField());
                    violation.put("message", fieldError.getDefaultMessage());
                    violation.put("rejectedValue", fieldError.getRejectedValue());
                    return violation;
                })
                .toList();

        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, "Validation failed", request);
        body.put("violations", violations);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadBookingRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadBookingRequest(
            BadBookingRequestException ex,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler(UnprocessableBookingException.class)
    public ResponseEntity<Map<String, Object>> handleUnprocessableBooking(
            UnprocessableBookingException ex,
            HttpServletRequest request) {
        return ResponseEntity.unprocessableEntity()
                .body(errorBody(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request));
    }

    @ExceptionHandler(IntegrationUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrationUnavailable(
            IntegrationUnavailableException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorBody(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, "Malformed JSON request body", request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String requiredType = ex.getRequiredType() == null ? "required type" : ex.getRequiredType().getSimpleName();
        String message = "Invalid query parameter %s. Expected %s".formatted(ex.getName(), requiredType);
        return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, message, request));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, TypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        String method = ex.getMethod() == null ? "HTTP method" : ex.getMethod();
        String message = "Request method %s is not supported".formatted(method);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(errorBody(HttpStatus.METHOD_NOT_ALLOWED, message, request));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {
        String message = "No endpoint found for %s %s".formatted(ex.getHttpMethod(), ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(HttpStatus.NOT_FOUND, message, request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. Please try again later.",
                        request));
    }

    private static Map<String, Object> errorBody(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        String requestId = request.getHeader("X-Request-ID");
        if (requestId != null && !requestId.isBlank()) {
            body.put("requestId", requestId);
        }
        return body;
    }
}

package com.agenticfun.bookinggherkin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {

    private final ObjectMapper objectMapper;

    public ApiErrorFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> body(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        return body;
    }

    public Map<String, Object> body(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = body(status, message, request.getRequestURI());
        String requestId = request.getHeader("X-Request-ID");
        if (requestId != null && !requestId.isBlank()) {
            body.put("requestId", requestId);
        }
        return body;
    }

    public void write(HttpServletResponse response, HttpStatus status, String message, String path) throws IOException {
        response.getWriter().write(objectMapper.writeValueAsString(body(status, message, path)));
    }

    public void write(HttpServletResponse response, HttpStatus status, String message, HttpServletRequest request)
            throws IOException {
        response.getWriter().write(objectMapper.writeValueAsString(body(status, message, request)));
    }
}

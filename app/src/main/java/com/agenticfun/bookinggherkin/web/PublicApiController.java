package com.agenticfun.bookinggherkin.web;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicApiController {

    @GetMapping("/api-docs/openapi.json")
    public Map<String, Object> openApi() {
        return Map.of(
                "openapi", "3.0.3",
                "info", Map.of("title", "Booking API", "version", "0.1.0"),
                "paths", Map.of(
                        "/api/v1/bookings", Map.of("get", Map.of("summary", "List bookings"),
                                "post", Map.of("summary", "Create a booking")),
                        "/api/v1/bookings/{id}", Map.of("get", Map.of("summary", "Read a booking")),
                        "/api/v1/bookings/{id}/cancel", Map.of("patch", Map.of("summary", "Cancel a booking")),
                        "/api/v1/bookings/{id}/confirm", Map.of("patch", Map.of("summary", "Confirm a booking")),
                        "/api/v1/bookings/{id}/start", Map.of("patch", Map.of("summary", "Start a booking")),
                        "/api/v1/bookings/{id}/complete", Map.of("patch", Map.of("summary", "Complete a booking"))));
    }

    @GetMapping(value = "/swagger-ui/index.html", produces = MediaType.TEXT_HTML_VALUE)
    public String swaggerUi() {
        return """
                <!doctype html>
                <html>
                <head><title>Booking API</title></head>
                <body><a href="/api-docs/openapi.json">Booking API OpenAPI document</a></body>
                </html>
                """;
    }
}

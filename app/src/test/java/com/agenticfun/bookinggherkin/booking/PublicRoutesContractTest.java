package com.agenticfun.bookinggherkin.booking;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "booking.security.enabled=true")
@AutoConfigureMockMvc
class PublicRoutesContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCallersCanReadServiceHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("json")))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void anonymousCallersCanReadOpenApiDocument() throws Exception {
        mockMvc.perform(get("/api-docs/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Booking API"))
                .andExpect(jsonPath("$.paths['/api/v1/bookings']").exists());
    }

    @Test
    void anonymousCallersCanOpenSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Booking API OpenAPI document")));
    }

    @ParameterizedTest(name = "{0} {1} requires authentication")
    @MethodSource("protectedBookingRoutes")
    void anonymousCallersAreRejectedFromProtectedBookingApiRoutes(String method, String path) throws Exception {
        mockMvc.perform(request(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void unknownApiPathsReturnStructuredErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/unknown-route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No endpoint found for GET /api/v1/unknown-route"))
                .andExpect(jsonPath("$.path").value("/api/v1/unknown-route"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    static Stream<Arguments> protectedBookingRoutes() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/bookings"),
                Arguments.of("GET", "/api/v1/bookings"),
                Arguments.of("GET", "/api/v1/bookings/42"),
                Arguments.of("PATCH", "/api/v1/bookings/42/cancel"),
                Arguments.of("PATCH", "/api/v1/bookings/42/confirm"),
                Arguments.of("PATCH", "/api/v1/bookings/42/start"),
                Arguments.of("PATCH", "/api/v1/bookings/42/complete"),
                Arguments.of("POST", "/bookings"),
                Arguments.of("GET", "/bookings/BKG-2026-00001"));
    }

    private static MockHttpServletRequestBuilder request(String method, String path) {
        return switch (method) {
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            default -> get(path);
        };
    }
}

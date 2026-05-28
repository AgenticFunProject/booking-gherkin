package com.agenticfun.bookinggherkin.booking;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BookingLifecycleContractTest {

    private static final String DEFAULT_BOOKING_REQUEST = """
            {
              "customerId": 3001,
              "scheduleId": 1001,
              "quoteId": 2001,
              "customer": {
                "name": "Test Customer",
                "email": "test@example.com"
              },
              "cargo": {
                "description": "Test cargo",
                "weightKg": 1000.00
              },
              "equipment": [
                { "type": "20FT", "quantity": 1 }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completeBookingThroughValidLifecyclePath() throws Exception {
        long bookingId = createDefaultBookingId();

        transition(bookingId, "confirm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        transition(bookingId, "start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        transition(bookingId, "complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void cancelPendingBooking() throws Exception {
        long bookingId = createDefaultBookingId();

        transition(bookingId, "cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelConfirmedBooking() throws Exception {
        long bookingId = createDefaultBookingId();
        transition(bookingId, "confirm").andExpect(status().isOk());

        transition(bookingId, "cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @ParameterizedTest(name = "reject {0} -> {2}")
    @MethodSource("invalidNonTerminalTransitions")
    void rejectInvalidNonTerminalLifecycleTransitions(String currentStatus, String action, String targetStatus)
            throws Exception {
        long bookingId = createDefaultBookingId();
        reachStatus(bookingId, currentStatus);

        transition(bookingId, action)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString(currentStatus + " to " + targetStatus)));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(currentStatus));
    }

    @ParameterizedTest(name = "completed rejects {0}")
    @MethodSource("lifecycleActions")
    void completedBookingsAreTerminal(String action, String targetStatus) throws Exception {
        long bookingId = createDefaultBookingId();
        reachStatus(bookingId, "COMPLETED");

        transition(bookingId, action)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("COMPLETED to " + targetStatus)));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @ParameterizedTest(name = "cancelled rejects {0}")
    @MethodSource("lifecycleActions")
    void cancelledBookingsAreTerminal(String action, String targetStatus) throws Exception {
        long bookingId = createDefaultBookingId();
        transition(bookingId, "cancel").andExpect(status().isOk());

        transition(bookingId, action)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("CANCELLED to " + targetStatus)));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private long createDefaultBookingId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEFAULT_BOOKING_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions transition(long bookingId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/{bookingId}/{action}", bookingId, action));
    }

    private void reachStatus(long bookingId, String status) throws Exception {
        switch (status) {
            case "PENDING" -> {
            }
            case "CONFIRMED" -> transition(bookingId, "confirm").andExpect(status().isOk());
            case "IN_PROGRESS" -> {
                transition(bookingId, "confirm").andExpect(status().isOk());
                transition(bookingId, "start").andExpect(status().isOk());
            }
            case "COMPLETED" -> {
                transition(bookingId, "confirm").andExpect(status().isOk());
                transition(bookingId, "start").andExpect(status().isOk());
                transition(bookingId, "complete").andExpect(status().isOk());
            }
            default -> throw new IllegalArgumentException("Unsupported status: " + status);
        }
    }

    static Stream<Arguments> invalidNonTerminalTransitions() {
        return Stream.of(
                Arguments.of("PENDING", "start", "IN_PROGRESS"),
                Arguments.of("PENDING", "complete", "COMPLETED"),
                Arguments.of("CONFIRMED", "confirm", "CONFIRMED"),
                Arguments.of("CONFIRMED", "complete", "COMPLETED"),
                Arguments.of("IN_PROGRESS", "confirm", "CONFIRMED"),
                Arguments.of("IN_PROGRESS", "cancel", "CANCELLED"));
    }

    static Stream<Arguments> lifecycleActions() {
        return Stream.of(
                Arguments.of("confirm", "CONFIRMED"),
                Arguments.of("start", "IN_PROGRESS"),
                Arguments.of("complete", "COMPLETED"),
                Arguments.of("cancel", "CANCELLED"));
    }
}

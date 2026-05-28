package com.agenticfun.bookinggherkin.booking;

import static com.agenticfun.bookinggherkin.security.JwtTokenFixtures.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "booking.security.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BookingCancellationContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private LocalEquipmentClient localEquipmentClient;

    @BeforeEach
    void resetState() {
        bookingService.clear();
        localEquipmentClient.clear();
    }

    @Test
    void customerCancelsTheirOwnPendingBooking() throws Exception {
        String customerToken = bearer("CUSTOMER", 3001);
        long bookingId = createBooking(customerToken, 3001);

        cancel(bookingId, customerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(3001))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        retrieve(bookingId, customerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void serviceCallerCancelsAConfirmedBookingOnBehalfOfACustomer() throws Exception {
        long bookingId = createConfirmedBooking(3001);
        String serviceToken = bearer("SERVICE");

        cancel(bookingId, serviceToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(3001))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(localEquipmentClient.hasReleaseAttempt(bookingId)).isTrue();

        retrieve(bookingId, serviceToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void adminCancelsABookingForAnyCustomer() throws Exception {
        String adminToken = bearer("ADMIN");
        long bookingId = createBooking(adminToken, 9001);

        cancel(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(9001))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customerCannotCancelAnotherCustomersBooking() throws Exception {
        String adminToken = bearer("ADMIN");
        createBooking(adminToken, 3001);
        long otherCustomerBookingId = createBooking(adminToken, 3002);

        cancel(otherCustomerBookingId, bearer("CUSTOMER", 3001))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        retrieve(otherCustomerBookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(3002))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void pendingCancellationDoesNotRequestEquipmentRelease() throws Exception {
        String adminToken = bearer("ADMIN");
        long bookingId = createBooking(adminToken, 3001);

        cancel(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(localEquipmentClient.hasReleaseAttempt(bookingId)).isFalse();
    }

    @Test
    void confirmedCancellationReleasesEquipmentBeforeReturningCancelled() throws Exception {
        String adminToken = bearer("ADMIN");
        long bookingId = createConfirmedBooking(3001);

        cancel(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(localEquipmentClient.hasReleaseAttempt(bookingId)).isTrue();
        assertThat(localEquipmentClient.hasReservation(bookingId)).isFalse();

        retrieve(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void confirmedCancellationRemainsSuccessfulWhenLocalEquipmentReleaseFails() throws Exception {
        String adminToken = bearer("ADMIN");
        long bookingId = createConfirmedBooking(3001);
        localEquipmentClient.failReleaseFor(bookingId);

        cancel(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.error").doesNotExist());

        retrieve(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(localEquipmentClient.hasFailedReleaseAttempt(bookingId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void ineligibleBookingsCannotBeCancelled(String status) throws Exception {
        String adminToken = bearer("ADMIN");
        long bookingId = createBooking(adminToken, 3001);
        reachStatus(bookingId, status);

        cancel(bookingId, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString(status + " to CANCELLED")));

        retrieve(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    private long createConfirmedBooking(long customerId) throws Exception {
        String adminToken = bearer("ADMIN");
        long bookingId = createBooking(adminToken, customerId);
        confirm(bookingId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        return bookingId;
    }

    private long createBooking(String token, long customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequest(customerId))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value((int) customerId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions cancel(long bookingId, String token) throws Exception {
        return mockMvc.perform(patch("/api/v1/bookings/{bookingId}/cancel", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(long bookingId, String token) throws Exception {
        return mockMvc.perform(patch("/api/v1/bookings/{bookingId}/confirm", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private org.springframework.test.web.servlet.ResultActions start(long bookingId, String token) throws Exception {
        return mockMvc.perform(patch("/api/v1/bookings/{bookingId}/start", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private org.springframework.test.web.servlet.ResultActions complete(long bookingId, String token) throws Exception {
        return mockMvc.perform(patch("/api/v1/bookings/{bookingId}/complete", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private org.springframework.test.web.servlet.ResultActions retrieve(long bookingId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private void reachStatus(long bookingId, String status) throws Exception {
        String adminToken = bearer("ADMIN");
        switch (status) {
            case "IN_PROGRESS" -> {
                confirm(bookingId, adminToken).andExpect(status().isOk());
                start(bookingId, adminToken).andExpect(status().isOk());
            }
            case "COMPLETED" -> {
                confirm(bookingId, adminToken).andExpect(status().isOk());
                start(bookingId, adminToken).andExpect(status().isOk());
                complete(bookingId, adminToken).andExpect(status().isOk());
            }
            case "CANCELLED" -> cancel(bookingId, adminToken).andExpect(status().isOk());
            default -> throw new IllegalArgumentException("Unsupported status: " + status);
        }
    }

    private static String bookingRequest(long customerId) {
        return """
                {
                  "customerId": %d,
                  "scheduleId": 1001,
                  "quoteId": 2001,
                  "customer": {
                    "name": "Cancellation Customer",
                    "email": "cancel.owner@example.com",
                    "phone": "+36-1-555-0140"
                  },
                  "cargo": {
                    "description": "Cancellation cargo",
                    "weightKg": 1000.00
                  },
                  "equipment": [
                    { "type": "20FT", "quantity": 1 }
                  ]
                }
                """.formatted(customerId);
    }
}

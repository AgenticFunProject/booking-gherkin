package com.agenticfun.bookinggherkin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
class BookingErrorHandlingContractTest {

    private static final String API_BOOKINGS = "/api/v1/bookings";
    private static final String VALID_BOOKING_REQUEST = """
            {
              "customerId": 3001,
              "scheduleId": 1001,
              "quoteId": 2001,
              "customer": {
                "name": "Error Contract Customer",
                "email": "errors@example.com",
                "phone": "+36-1-555-0199"
              },
              "cargo": {
                "description": "Contract error cargo",
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
    void standardErrorBodyExposesStableTopLevelFields() throws Exception {
        MvcResult result = mockMvc.perform(get(API_BOOKINGS + "/BKG-2099-99999")
                        .header("X-Request-ID", "req-error-contract-1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("Booking not found")))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS + "/BKG-2099-99999"))
                .andExpect(jsonPath("$.requestId").value("req-error-contract-1"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andReturn();

        Instant.parse(json(result).get("timestamp").asText());
    }

    @Test
    void beanValidationFailuresReturnSortedFieldLevelViolations() throws Exception {
        String request = """
                {
                  "customerId": 3001,
                  "scheduleId": 1001,
                  "quoteId": 2001,
                  "customer": {
                    "name": "",
                    "email": "not-an-email"
                  },
                  "cargo": {
                    "description": "",
                    "weightKg": -5
                  },
                  "equipment": [
                    { "type": "", "quantity": 0 }
                  ]
                }
                """;

        MvcResult result = mockMvc.perform(post(API_BOOKINGS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("Validation failed")))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS))
                .andExpect(jsonPath("$.violations").isArray())
                .andReturn();

        JsonNode violations = json(result).get("violations");
        List<String> fields = violations.findValuesAsText("field");
        assertThat(fields).containsExactly(
                "cargo.description",
                "cargo.weightKg",
                "customer.email",
                "customer.name",
                "equipment[0].quantity",
                "equipment[0].type");
        violations.forEach(violation -> assertThat(violation.fieldNames())
                .toIterable()
                .contains("field", "message", "rejectedValue"));
    }

    @ParameterizedTest
    @MethodSource("businessValidationErrors")
    void businessValidationErrorsReturnBadRequestWithoutViolations(String request, String path, String message)
            throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString(message)))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    void invalidBookingIdValueReturnsBadRequestWithoutViolations() throws Exception {
        mockMvc.perform(get(API_BOOKINGS + "/not-a-valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid booking identifier")))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS + "/not-a-valid"))
                .andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    void malformedJsonRequestBodiesReturnSanitizedBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(post(API_BOOKINGS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"customerId\": 3001, \"scheduleId\": 1001,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("JsonParseException")
                .doesNotContain("Unexpected end-of-input");
    }

    @ParameterizedTest
    @MethodSource("integrationFailures")
    void businessAndIntegrationExceptionStatusesStayStable(String behavior, int status, String error, String message)
            throws Exception {
        mockMvc.perform(post(API_BOOKINGS)
                        .header("X-Local-Dependency-Behavior", behavior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOKING_REQUEST))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(containsString(message)))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS));
    }

    @Test
    void missingBookingsReturnNotFound() throws Exception {
        mockMvc.perform(get(API_BOOKINGS + "/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("Booking not found")))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS + "/999999999"));
    }

    @Test
    void illegalLifecycleTransitionsReturnStructuredConflictAndPreserveStatus() throws Exception {
        long bookingId = createCompletedBookingId();

        mockMvc.perform(post(API_BOOKINGS + "/{bookingId}/cancel", bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("COMPLETED to CANCELLED")))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS + "/" + bookingId + "/cancel"));

        mockMvc.perform(get(API_BOOKINGS + "/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void invalidQueryParameterValuesReturnBadRequest() throws Exception {
        mockMvc.perform(get(API_BOOKINGS + "?status=SHIPPED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("status")))
                .andExpect(jsonPath("$.message").value(containsString("BookingStatus")))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS));
    }

    @Test
    void unsupportedMethodsReturnMethodNotAllowed() throws Exception {
        mockMvc.perform(delete(API_BOOKINGS + "/42"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value(containsString("DELETE")))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS + "/42"));
    }

    @Test
    void unknownApiPathsReturnStructuredNotFoundResponse() throws Exception {
        MvcResult result = mockMvc.perform(get(API_BOOKINGS + "/42/unknown-action"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("No endpoint found for GET " + API_BOOKINGS + "/42/unknown-action"))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS + "/42/unknown-action"))
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andReturn();

        Instant.parse(json(result).get("timestamp").asText());
    }

    @Test
    void unexpectedServerErrorsHideInternalDetails() throws Exception {
        MvcResult result = mockMvc.perform(post(API_BOOKINGS)
                        .header("X-Trigger-Internal-Failure", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOKING_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.path").value(API_BOOKINGS))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("IllegalStateException")
                .doesNotContain("Simulated internal failure");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createCompletedBookingId() throws Exception {
        MvcResult result = mockMvc.perform(post(API_BOOKINGS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOKING_REQUEST))
                .andExpect(status().isCreated())
                .andReturn();
        long bookingId = json(result).get("id").asLong();
        mockMvc.perform(post(API_BOOKINGS + "/{bookingId}/confirm", bookingId))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_BOOKINGS + "/{bookingId}/start", bookingId))
                .andExpect(status().isOk());
        mockMvc.perform(post(API_BOOKINGS + "/{bookingId}/complete", bookingId))
                .andExpect(status().isOk());
        return bookingId;
    }

    static Stream<Arguments> businessValidationErrors() {
        return Stream.of(
                Arguments.of("""
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "45FT", "quantity": 1 } ] }
                        """, API_BOOKINGS, "Unsupported equipment type"),
                Arguments.of("""
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [] }
                        """, API_BOOKINGS, "equipment"));
    }

    static Stream<Arguments> integrationFailures() {
        return Stream.of(
                Arguments.of("unavailable schedule", 422, "Unprocessable Entity", "schedule"),
                Arguments.of("invalid quote", 422, "Unprocessable Entity", "quote"),
                Arguments.of("equipment reservation failure", 503, "Service Unavailable", "equipment reservation"));
    }
}

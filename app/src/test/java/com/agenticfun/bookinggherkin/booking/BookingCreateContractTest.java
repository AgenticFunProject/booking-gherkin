package com.agenticfun.bookinggherkin.booking;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
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
class BookingCreateContractTest {

    private static final String VALID_BOOKING_REQUEST = """
            {
              "customerId": 3001,
              "scheduleId": 1001,
              "quoteId": 2001,
              "customer": {
                "name": "Acme Shipping Co.",
                "email": "logistics@acme.com",
                "phone": "+36-1-234-5678"
              },
              "cargo": {
                "description": "Industrial machinery parts",
                "weightKg": 12000.00
              },
              "equipment": [
                { "type": "20FT", "quantity": 2 },
                { "type": "40HC", "quantity": 1 }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBookingWithMultipleEquipmentLines() throws Exception {
        int utcYear = Year.now(ZoneOffset.UTC).getValue();

        MvcResult result = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOKING_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(3001))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")))
                .andExpect(jsonPath("$.bookingReference").value(containsString("BKG-" + utcYear + "-")))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn();

        String createdAt = json(result).get("createdAt").asText();
        Instant.parse(createdAt);
    }

    @Test
    void createdBookingDetailsPreserveCustomerCargoAndEquipmentFields() throws Exception {
        String bookingReference = createValidBookingReference();

        mockMvc.perform(get("/bookings/{bookingReference}", bookingReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(bookingReference))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value(3001))
                .andExpect(jsonPath("$.scheduleId").value(1001))
                .andExpect(jsonPath("$.quoteId").value(2001))
                .andExpect(jsonPath("$.customer.name").value("Acme Shipping Co."))
                .andExpect(jsonPath("$.customer.email").value("logistics@acme.com"))
                .andExpect(jsonPath("$.customer.phone").value("+36-1-234-5678"))
                .andExpect(jsonPath("$.cargo.description").value("Industrial machinery parts"))
                .andExpect(jsonPath("$.cargo.weightKg").value(12000.00))
                .andExpect(jsonPath("$.equipment.length()").value(2))
                .andExpect(jsonPath("$.equipment[0].type").value("20FT"))
                .andExpect(jsonPath("$.equipment[0].quantity").value(2))
                .andExpect(jsonPath("$.equipment[1].type").value("40HC"))
                .andExpect(jsonPath("$.equipment[1].quantity").value(1));
    }

    @Test
    void localScheduleAndQuoteStubsAcceptOtherwiseValidBookingRequests() throws Exception {
        String request = """
                {
                  "customerId": 3002,
                  "scheduleId": 987654321,
                  "quoteId": 876543210,
                  "customer": {
                    "name": "Stub Accepted Customer",
                    "email": "stub.accepted@example.com",
                    "phone": "+36-1-555-0100"
                  },
                  "cargo": {
                    "description": "Cargo accepted by local stubs",
                    "weightKg": 42.50
                  },
                  "equipment": [
                    { "type": "REEFER", "quantity": 1 }
                  ]
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")));
    }

    @ParameterizedTest(name = "missing {0} is rejected")
    @MethodSource("missingRequiredFieldRequests")
    void missingRequiredFieldsAreRejected(String field, String request) throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fields").value(hasItem(field)));
    }

    @Test
    void unsupportedEquipmentTypeIsRejected() throws Exception {
        String request = """
                {
                  "customerId": 3001,
                  "scheduleId": 1001,
                  "quoteId": 2001,
                  "customer": {
                    "name": "Acme Shipping Co.",
                    "email": "logistics@acme.com",
                    "phone": "+36-1-234-5678"
                  },
                  "cargo": {
                    "description": "Industrial machinery parts",
                    "weightKg": 12000.00
                  },
                  "equipment": [
                    { "type": "45FT", "quantity": 1 }
                  ]
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("Unsupported equipment type: 45FT")));
    }

    @Test
    void emptyEquipmentListIsRejected() throws Exception {
        String request = """
                {
                  "customerId": 3001,
                  "scheduleId": 1001,
                  "quoteId": 2001,
                  "customer": {
                    "name": "Acme Shipping Co.",
                    "email": "logistics@acme.com",
                    "phone": "+36-1-234-5678"
                  },
                  "cargo": {
                    "description": "Industrial machinery parts",
                    "weightKg": 12000.00
                  },
                  "equipment": []
                }
                """;

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fields").value(hasItem("equipment")));
    }

    private String createValidBookingReference() throws Exception {
        MvcResult result = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BOOKING_REQUEST))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("bookingReference").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    static Stream<Arguments> missingRequiredFieldRequests() {
        return Stream.of(
                Arguments.of("customerId", """
                        { "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }
                        """),
                Arguments.of("scheduleId", """
                        { "customerId": 3001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }
                        """),
                Arguments.of("quoteId", """
                        { "customerId": 3001, "scheduleId": 1001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }
                        """),
                Arguments.of("customer.name", """
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }
                        """),
                Arguments.of("customer.email", """
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }
                        """),
                Arguments.of("cargo.description", """
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "weightKg": 12000.00 }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }
                        """),
                Arguments.of("cargo.weightKg", """
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts" }, "equipment": [ { "type": "20FT", "quantity": 2 } ] }
                        """),
                Arguments.of("equipment[0].type", """
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "quantity": 2 } ] }
                        """),
                Arguments.of("equipment[0].quantity", """
                        { "customerId": 3001, "scheduleId": 1001, "quoteId": 2001, "customer": { "name": "Acme Shipping Co.", "email": "logistics@acme.com", "phone": "+36-1-234-5678" }, "cargo": { "description": "Industrial machinery parts", "weightKg": 12000.00 }, "equipment": [ { "type": "20FT" } ] }
                        """));
    }
}

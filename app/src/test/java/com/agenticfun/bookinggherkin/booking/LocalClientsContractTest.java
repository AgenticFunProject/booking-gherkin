package com.agenticfun.bookinggherkin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "booking.clients.schedule.base-url=http://127.0.0.1:1/unavailable-schedule",
        "booking.clients.quote.base-url=http://127.0.0.1:1/unavailable-quote",
        "booking.clients.equipment.base-url=http://127.0.0.1:1/unavailable-equipment"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalClientsContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalEquipmentClient localEquipmentClient;

    @Test
    void localScheduleAndQuoteStubsAcceptAValidBookingRequest() throws Exception {
        JsonNode booking = createBooking("""
                {
                  "customerId": 3101,
                  "scheduleId": 999001,
                  "quoteId": 999101,
                  "customer": {
                    "name": "Local Stub Customer",
                    "email": "local.stub@example.com",
                    "phone": "+36-1-555-3101"
                  },
                  "cargo": {
                    "description": "Cargo accepted without real external clients",
                    "weightKg": 5250.75
                  },
                  "equipment": [
                    { "type": "20FT", "quantity": 1 },
                    { "type": "REEFER", "quantity": 1 }
                  ]
                }
                """);

        assertThat(booking.get("status").asText()).isEqualTo("PENDING");
        assertThat(booking.get("scheduleId").asLong()).isEqualTo(999001);
        assertThat(booking.get("quoteId").asLong()).isEqualTo(999101);
        assertThat(booking.get("bookingReference").asText()).matches("BKG-[0-9]{4}-[0-9]{5}");
    }

    @Test
    void confirmingABookingUsesLocalEquipmentReservationBehavior() throws Exception {
        JsonNode booking = createBooking("""
                {
                  "customerId": 3102,
                  "scheduleId": 999002,
                  "quoteId": 999102,
                  "customer": {
                    "name": "Local Confirm Customer",
                    "email": "local.confirm@example.com",
                    "phone": "+36-1-555-3102"
                  },
                  "cargo": {
                    "description": "Cargo confirmed with local equipment stub",
                    "weightKg": 800.00
                  },
                  "equipment": [
                    { "type": "40HC", "quantity": 2 }
                  ]
                }
                """);
        long bookingId = booking.get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.id").value(bookingId));

        assertThat(localEquipmentClient.hasReservation(bookingId)).isTrue();

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancellingAConfirmedBookingUsesLocalEquipmentReleaseBehavior() throws Exception {
        JsonNode booking = createBooking("""
                {
                  "customerId": 3103,
                  "scheduleId": 999003,
                  "quoteId": 999103,
                  "customer": {
                    "name": "Local Cancel Customer",
                    "email": "local.cancel@example.com",
                    "phone": "+36-1-555-3103"
                  },
                  "cargo": {
                    "description": "Cargo cancelled with local equipment release",
                    "weightKg": 1200.00
                  },
                  "equipment": [
                    { "type": "20FT", "quantity": 3 }
                  ]
                }
                """);
        long bookingId = booking.get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(localEquipmentClient.hasReservation(bookingId)).isTrue();

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(localEquipmentClient.hasReservation(bookingId)).isFalse();

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void realExternalHttpServicesAreOutsideThisLocalContract() throws Exception {
        JsonNode booking = createBooking("""
                {
                  "customerId": 3104,
                  "scheduleId": 999004,
                  "quoteId": 999104,
                  "customer": {
                    "name": "Unavailable External Customer",
                    "email": "external.unavailable@example.com",
                    "phone": "+36-1-555-3104"
                  },
                  "cargo": {
                    "description": "Cargo accepted while external URLs are unavailable",
                    "weightKg": 1500.00
                  },
                  "equipment": [
                    { "type": "REEFER", "quantity": 1 }
                  ]
                }
                """);
        long bookingId = booking.get("id").asLong();

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")))
                .andExpect(jsonPath("$.customer.name").value("Unavailable External Customer"))
                .andExpect(jsonPath("$.equipment.length()").value(1));

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")))
                .andExpect(jsonPath("$.customer.name").value("Unavailable External Customer"))
                .andExpect(jsonPath("$.equipment.length()").value(1));
    }

    private JsonNode createBooking(String request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

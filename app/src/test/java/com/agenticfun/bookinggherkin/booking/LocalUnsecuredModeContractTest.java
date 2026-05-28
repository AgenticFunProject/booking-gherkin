package com.agenticfun.bookinggherkin.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
class LocalUnsecuredModeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void localUnsecuredModePermitsBookingRequestsWithoutJwtOwnershipChecks() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequest(3001)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(3001));

        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId").value(3001));
    }

    @Test
    void localUnsecuredModePermitsLegacyBookingRoutesWithoutJwtOwnershipChecks() throws Exception {
        MvcResult result = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequest(3002)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(3002))
                .andReturn();

        String bookingReference = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("bookingReference")
                .asText();

        mockMvc.perform(get("/bookings/{bookingReference}", bookingReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(3002));
    }

    private static String bookingRequest(long customerId) {
        return """
                {
                  "customerId": %d,
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
                    { "type": "20FT", "quantity": 2 }
                  ]
                }
                """.formatted(customerId);
    }
}

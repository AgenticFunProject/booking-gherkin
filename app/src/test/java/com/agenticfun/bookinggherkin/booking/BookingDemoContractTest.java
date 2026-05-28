package com.agenticfun.bookinggherkin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
class BookingDemoContractTest {

    private static final String DEMO_BOOKING_REQUEST = """
            {
              "customerId": 4201,
              "scheduleId": 91001,
              "quoteId": 92001,
              "customer": {
                "name": "Demo Logistics Ltd.",
                "email": "demo.ops@example.com",
                "phone": "+36-1-555-4201"
              },
              "cargo": {
                "description": "Demo cargo: packaged industrial components",
                "weightKg": 1250.50
              },
              "equipment": [
                { "type": "20FT", "quantity": 1 },
                { "type": "40HC", "quantity": 1 }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingService bookingService;

    @BeforeEach
    void clearBookings() {
        bookingService.clear();
    }

    @Test
    void startFromInspectableLocalServiceWithNoDemoBookings() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Booking API OpenAPI document")));

        mockMvc.perform(get("/api-docs/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Booking API"))
                .andExpect(jsonPath("$.paths['/api/v1/bookings']").exists());

        mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "4201")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void demonstrateCreateReadListAndCompleteLifecycleBehavior() throws Exception {
        MvcResult createdResult = mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEMO_BOOKING_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(4201))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")))
                .andReturn();
        JsonNode created = body(createdResult);
        long bookingId = created.get("id").asLong();
        String bookingReference = created.get("bookingReference").asText();

        mockMvc.perform(get("/api/v1/bookings/{bookingReference}", bookingReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(bookingReference))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value(4201))
                .andExpect(jsonPath("$.scheduleId").value(91001))
                .andExpect(jsonPath("$.quoteId").value(92001))
                .andExpect(jsonPath("$.customer.name").value("Demo Logistics Ltd."))
                .andExpect(jsonPath("$.customer.email").value("demo.ops@example.com"))
                .andExpect(jsonPath("$.customer.phone").value("+36-1-555-4201"))
                .andExpect(jsonPath("$.cargo.description").value("Demo cargo: packaged industrial components"))
                .andExpect(jsonPath("$.cargo.weightKg").value(1250.50))
                .andExpect(jsonPath("$.equipment.length()").value(2))
                .andExpect(jsonPath("$.equipment[0].type").value("20FT"))
                .andExpect(jsonPath("$.equipment[0].quantity").value(1))
                .andExpect(jsonPath("$.equipment[1].type").value("40HC"))
                .andExpect(jsonPath("$.equipment[1].quantity").value(1));

        MvcResult listResult = mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "4201")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn();
        JsonNode listedBooking = body(listResult).get("content").get(0);
        assertThat(listedBooking.get("customerId").asLong()).isEqualTo(4201);
        assertThat(listedBooking.get("bookingReference").asText()).isEqualTo(bookingReference);

        transition(bookingId, "confirm", "CONFIRMED");
        transition(bookingId, "start", "IN_PROGRESS");
        transition(bookingId, "complete", "COMPLETED");

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingReference").value(bookingReference))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.customer.name").value("Demo Logistics Ltd."))
                .andExpect(jsonPath("$.cargo.description").value("Demo cargo: packaged industrial components"))
                .andExpect(jsonPath("$.equipment.length()").value(2))
                .andExpect(jsonPath("$.equipment[0].type").value("20FT"))
                .andExpect(jsonPath("$.equipment[0].quantity").value(1))
                .andExpect(jsonPath("$.equipment[1].type").value("40HC"))
                .andExpect(jsonPath("$.equipment[1].quantity").value(1));
    }

    private void transition(long bookingId, String action, String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/{action}", bookingId, action))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

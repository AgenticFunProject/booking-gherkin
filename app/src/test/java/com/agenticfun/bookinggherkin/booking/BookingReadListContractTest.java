package com.agenticfun.bookinggherkin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class BookingReadListContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingService bookingService;

    private final Map<String, BookingResponse> bookings = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        bookingService.clear();
        bookings.clear();
        seedBookings();
    }

    @Test
    void fetchBookingByNumericId() throws Exception {
        BookingResponse booking = bookings.get("acme-pending");

        MvcResult result = mockMvc.perform(get("/api/v1/bookings/{identifier}", booking.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(booking.id()))
                .andExpect(jsonPath("$.bookingReference").value(booking.bookingReference()))
                .andExpect(jsonPath("$.customerId").value(3001))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.scheduleId").value(1001))
                .andExpect(jsonPath("$.quoteId").value(2001))
                .andExpect(jsonPath("$.customer.name").value("Acme Shipping Co."))
                .andExpect(jsonPath("$.customer.email").value("logistics@acme.com"))
                .andExpect(jsonPath("$.cargo.description").value("Industrial machinery"))
                .andExpect(jsonPath("$.cargo.weightKg").value(12000.00))
                .andExpect(jsonPath("$.equipment.length()").value(1))
                .andExpect(jsonPath("$.equipment[0].type").value("20FT"))
                .andExpect(jsonPath("$.equipment[0].quantity").value(2))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        JsonNode body = json(result);
        Instant.parse(body.get("createdAt").asText());
        Instant.parse(body.get("updatedAt").asText());
    }

    @Test
    void fetchBookingByBookingReference() throws Exception {
        BookingResponse booking = bookings.get("acme-confirmed");

        mockMvc.perform(get("/api/v1/bookings/{identifier}", booking.bookingReference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.id()))
                .andExpect(jsonPath("$.bookingReference").value(booking.bookingReference()))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")))
                .andExpect(jsonPath("$.customerId").value(3001))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.scheduleId").value(1002))
                .andExpect(jsonPath("$.quoteId").value(2002))
                .andExpect(jsonPath("$.customer.name").value("Acme Shipping Co."))
                .andExpect(jsonPath("$.cargo.description").value("Replacement parts"))
                .andExpect(jsonPath("$.equipment.length()").value(1))
                .andExpect(jsonPath("$.equipment[0].type").value("40HC"))
                .andExpect(jsonPath("$.equipment[0].quantity").value(1));
    }

    @ParameterizedTest
    @CsvSource({
            "999999999, Booking not found with id 999999999",
            "BKG-2026-99999, Booking not found with reference BKG-2026-99999"
    })
    void missingBookingsReturnStructuredNotFoundError(String identifier, String messageFragment) throws Exception {
        mockMvc.perform(get("/api/v1/bookings/{identifier}", identifier))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString(messageFragment)))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings/" + identifier))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "not-a-booking",
            "BKG-2026-1234",
            "bkg-2026-00042",
            "BKG-26-00042"
    })
    void invalidBookingIdentifiersAreRejectedBeforeLookup(String identifier) throws Exception {
        mockMvc.perform(get("/api/v1/bookings/{identifier}", identifier))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid booking identifier: " + identifier
                        + ". Expected numeric ID or booking reference in format BKG-YYYY-NNNNN"))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings/" + identifier))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void listBookingsFilteredByCustomerId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "3001")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(values(content, "customerId")).containsOnly("3001");
        assertThat(values(content, "bookingReference"))
                .contains(bookings.get("acme-pending").bookingReference(), bookings.get("acme-confirmed").bookingReference())
                .doesNotContain(bookings.get("globex-pending").bookingReference(), bookings.get("initech-complete").bookingReference());
        assertEveryBookingHasContractFields(content);
    }

    @Test
    void listBookingsFilteredByStatus() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bookings")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(values(content, "status")).containsOnly("PENDING");
        assertThat(values(content, "bookingReference"))
                .contains(bookings.get("acme-pending").bookingReference(), bookings.get("globex-pending").bookingReference())
                .doesNotContain(bookings.get("acme-confirmed").bookingReference(), bookings.get("initech-complete").bookingReference());
    }

    @Test
    void listBookingsFilteredByCustomerIdAndStatus() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "3001")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn();

        JsonNode booking = json(result).get("content").get(0);
        assertThat(booking.get("bookingReference").asText()).isEqualTo(bookings.get("acme-pending").bookingReference());
        assertThat(booking.get("customerId").asLong()).isEqualTo(3001);
        assertThat(booking.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void listResponseExposesStablePaginationMetadata() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bookings")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true))
                .andReturn();

        JsonNode body = json(result);
        List<String> fieldNames = new ArrayList<>();
        body.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames)
                .containsExactly("content", "page", "size", "totalElements", "totalPages", "last");
    }

    @Test
    void emptyListFiltersStillReturnPaginationMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "404040")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void invalidStatusFilterReturnsStructuredBadRequestError() throws Exception {
        mockMvc.perform(get("/api/v1/bookings").param("status", "LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Parameter 'status' must be a valid BookingStatus"))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    private void seedBookings() {
        bookings.put("acme-pending", bookingService.create(request(
                3001,
                1001,
                2001,
                "Acme Shipping Co.",
                "logistics@acme.com",
                "Industrial machinery",
                "12000.00",
                "20FT",
                2), BookingStatus.PENDING));
        bookings.put("acme-confirmed", bookingService.create(request(
                3001,
                1002,
                2002,
                "Acme Shipping Co.",
                "logistics@acme.com",
                "Replacement parts",
                "3200.50",
                "40HC",
                1), BookingStatus.CONFIRMED));
        bookings.put("globex-pending", bookingService.create(request(
                3002,
                1003,
                2003,
                "Globex Logistics",
                "freight@globex.example",
                "Refrigerated cargo",
                "850.25",
                "REEFER",
                1), BookingStatus.PENDING));
        bookings.put("initech-complete", bookingService.create(request(
                3003,
                1004,
                2004,
                "Initech Distribution",
                "shipping@initech.example",
                "Office equipment",
                "410.00",
                "40FT",
                1), BookingStatus.COMPLETED));
    }

    private static CreateBookingRequest request(
            long customerId,
            long scheduleId,
            long quoteId,
            String customerName,
            String customerEmail,
            String cargoDescription,
            String weightKg,
            String equipmentType,
            int quantity) {
        return new CreateBookingRequest(
                customerId,
                scheduleId,
                quoteId,
                new CustomerDetails(customerName, customerEmail, null),
                new CargoDetails(cargoDescription, new BigDecimal(weightKg)),
                List.of(new EquipmentLine(equipmentType, quantity)));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> values(JsonNode content, String fieldName) {
        return StreamSupport.stream(content.spliterator(), false)
                .map(booking -> booking.get(fieldName).asText())
                .toList();
    }

    private static void assertEveryBookingHasContractFields(JsonNode content) {
        for (JsonNode booking : content) {
            assertThat(booking.hasNonNull("id")).isTrue();
            assertThat(booking.hasNonNull("bookingReference")).isTrue();
            assertThat(booking.hasNonNull("status")).isTrue();
            assertThat(booking.hasNonNull("scheduleId")).isTrue();
            assertThat(booking.hasNonNull("quoteId")).isTrue();
            assertThat(booking.hasNonNull("customer")).isTrue();
            assertThat(booking.hasNonNull("cargo")).isTrue();
            assertThat(booking.hasNonNull("equipment")).isTrue();
            assertThat(booking.hasNonNull("createdAt")).isTrue();
            assertThat(booking.hasNonNull("updatedAt")).isTrue();
        }
    }
}

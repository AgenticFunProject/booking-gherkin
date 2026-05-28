package com.agenticfun.bookinggherkin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticfun.bookinggherkin.BookingGherkinApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class BookingPersistenceRuntimeContractTest {

    @TempDir
    private Path tempDir;

    @Test
    void emptyDurableServiceRemainsEmptyAfterRestart() throws Exception {
        Path runtimeFile = runtimeFile();

        try (RunningApplication ignored = start(runtimeFile)) {
        }

        try (RunningApplication restarted = start(runtimeFile)) {
            restarted.mockMvc().perform(get("/api/v1/bookings")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0))
                    .andExpect(jsonPath("$.last").value(true));
        }
    }

    @Test
    void createdBookingCanBeReadAfterRestart() throws Exception {
        Path runtimeFile = runtimeFile();
        long bookingId;
        String bookingReference;

        try (RunningApplication app = start(runtimeFile)) {
            JsonNode created = createBooking(app, durableFreightRequest());
            bookingId = created.get("id").asLong();
            bookingReference = created.get("bookingReference").asText();
        }

        try (RunningApplication restarted = start(runtimeFile)) {
            MvcResult result = restarted.mockMvc().perform(get("/api/v1/bookings/{bookingReference}", bookingReference))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.bookingReference").value(bookingReference))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.customerId").value(3101))
                    .andExpect(jsonPath("$.scheduleId").value(1101))
                    .andExpect(jsonPath("$.quoteId").value(2101))
                    .andExpect(jsonPath("$.customer.name").value("Durable Freight Co."))
                    .andExpect(jsonPath("$.customer.email").value("durable.freight@example.com"))
                    .andExpect(jsonPath("$.customer.phone").value("+36-1-555-1101"))
                    .andExpect(jsonPath("$.cargo.description").value("Restart-visible cargo"))
                    .andExpect(jsonPath("$.cargo.weightKg").value(2100.25))
                    .andExpect(jsonPath("$.equipment.length()").value(2))
                    .andExpect(jsonPath("$.equipment[0].type").value("20FT"))
                    .andExpect(jsonPath("$.equipment[0].quantity").value(1))
                    .andExpect(jsonPath("$.equipment[1].type").value("REEFER"))
                    .andExpect(jsonPath("$.equipment[1].quantity").value(1))
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andExpect(jsonPath("$.updatedAt").isString())
                    .andReturn();

            JsonNode body = restarted.json(result);
            Instant.parse(body.get("createdAt").asText());
            Instant.parse(body.get("updatedAt").asText());
            assertThat(body.get("createdAt").asText()).endsWith("Z");
            assertThat(body.get("updatedAt").asText()).endsWith("Z");
        }
    }

    @Test
    void lifecycleStatusSurvivesRestart() throws Exception {
        Map<String, Long> bookingIdsByStatus = new HashMap<>();
        Map<String, String> referencesByStatus = new HashMap<>();

        try (RunningApplication app = start(runtimeFile())) {
            reachStatus(app, createBooking(app, customerRequest(3201)).get("id").asLong(), "CONFIRMED");
            reachStatus(app, createBooking(app, customerRequest(3202)).get("id").asLong(), "IN_PROGRESS");
            reachStatus(app, createBooking(app, customerRequest(3203)).get("id").asLong(), "COMPLETED");
            reachStatus(app, createBooking(app, customerRequest(3204)).get("id").asLong(), "CANCELLED");

            for (String status : new String[] {"CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELLED"}) {
                JsonNode booking = getBookingByStatus(app, status);
                bookingIdsByStatus.put(status, booking.get("id").asLong());
                referencesByStatus.put(status, booking.get("bookingReference").asText());
            }
        }

        try (RunningApplication restarted = start(runtimeFile())) {
            assertPersistedStatus(restarted, 3201, "CONFIRMED", bookingIdsByStatus, referencesByStatus);
            assertPersistedStatus(restarted, 3202, "IN_PROGRESS", bookingIdsByStatus, referencesByStatus);
            assertPersistedStatus(restarted, 3203, "COMPLETED", bookingIdsByStatus, referencesByStatus);
            assertPersistedStatus(restarted, 3204, "CANCELLED", bookingIdsByStatus, referencesByStatus);
        }
    }

    @Test
    void bookingReferencesRemainUniqueAfterRestart() throws Exception {
        Path runtimeFile = runtimeFile();
        String referenceBeforeRestart;

        try (RunningApplication app = start(runtimeFile)) {
            referenceBeforeRestart = createBooking(app, customerRequest(3301)).get("bookingReference").asText();
        }

        try (RunningApplication restarted = start(runtimeFile)) {
            JsonNode createdAfterRestart = createBooking(restarted, customerRequest(3302));
            String referenceAfterRestart = createdAfterRestart.get("bookingReference").asText();

            assertThat(referenceAfterRestart).matches("BKG-[0-9]{4}-[0-9]{5}");
            assertThat(referenceAfterRestart).contains("BKG-" + Year.now(ZoneOffset.UTC).getValue() + "-");
            assertThat(referenceAfterRestart).isNotEqualTo(referenceBeforeRestart);
            assertThat(sequence(referenceAfterRestart)).isGreaterThan(sequence(referenceBeforeRestart));

            restarted.mockMvc().perform(get("/api/v1/bookings/{bookingReference}", referenceBeforeRestart))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingReference").value(referenceBeforeRestart));
        }
    }

    @Test
    void customerAndStatusFilteredListsReflectDurableStateAfterRestart() throws Exception {
        Path runtimeFile = runtimeFile();
        Map<String, String> aliases = new HashMap<>();

        try (RunningApplication app = start(runtimeFile)) {
            aliases.put("durable-acme-pending", createBooking(app, tableRequest(
                    3401, 1401, 2401, "Durable Acme", "durable.acme@example.com",
                    "Pending durable cargo", "100.00", "20FT")).get("bookingReference").asText());
            aliases.put("durable-acme-complete", createBooking(app, tableRequest(
                    3401, 1402, 2402, "Durable Acme", "durable.acme@example.com",
                    "Complete durable cargo", "200.00", "40FT")).get("bookingReference").asText());
            aliases.put("durable-globex-open", createBooking(app, tableRequest(
                    3402, 1403, 2403, "Durable Globex", "durable.globex@example.com",
                    "Other customer cargo", "300.00", "REEFER")).get("bookingReference").asText());
            aliases.put("durable-initech-done", createBooking(app, tableRequest(
                    3403, 1404, 2404, "Durable Initech", "durable.initech@example.com",
                    "Completed other cargo", "400.00", "40HC")).get("bookingReference").asText());

            reachStatus(app, idByReference(app, aliases.get("durable-acme-complete")), "COMPLETED");
            reachStatus(app, idByReference(app, aliases.get("durable-initech-done")), "COMPLETED");
        }

        try (RunningApplication restarted = start(runtimeFile)) {
            MvcResult result = restarted.mockMvc().perform(get("/api/v1/bookings")
                            .param("customerId", "3401")
                            .param("status", "COMPLETED")
                            .param("page", "0")
                            .param("size", "20")
                            .param("sort", "createdAt,desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.last").value(true))
                    .andReturn();

            JsonNode content = restarted.json(result).get("content");
            assertThat(references(content)).containsExactly(aliases.get("durable-acme-complete"));
            assertThat(references(content))
                    .doesNotContain(
                            aliases.get("durable-acme-pending"),
                            aliases.get("durable-globex-open"),
                            aliases.get("durable-initech-done"));
            for (JsonNode booking : content) {
                assertThat(booking.get("customerId").asLong()).isEqualTo(3401);
                assertThat(booking.get("status").asText()).isEqualTo("COMPLETED");
            }
        }
    }

    private Path runtimeFile() {
        return tempDir.resolve("booking-state.json");
    }

    private RunningApplication start(Path runtimeFile) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(BookingGherkinApplication.class)
                .profiles("local")
                .run(
                        "--booking.persistence.enabled=true",
                        "--booking.persistence.path=" + runtimeFile.toAbsolutePath(),
                        "--booking.security.enabled=false",
                        "--server.port=0",
                        "--spring.main.banner-mode=off");
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
        return new RunningApplication(context, mockMvc, context.getBean(ObjectMapper.class));
    }

    private JsonNode createBooking(RunningApplication app, String request) throws Exception {
        MvcResult result = app.mockMvc().perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingReference").value(matchesPattern("BKG-[0-9]{4}-[0-9]{5}")))
                .andReturn();
        return app.json(result);
    }

    private void reachStatus(RunningApplication app, long bookingId, String targetStatus) throws Exception {
        switch (targetStatus) {
            case "PENDING" -> {
            }
            case "CONFIRMED" -> transition(app, bookingId, "confirm");
            case "IN_PROGRESS" -> {
                transition(app, bookingId, "confirm");
                transition(app, bookingId, "start");
            }
            case "COMPLETED" -> {
                transition(app, bookingId, "confirm");
                transition(app, bookingId, "start");
                transition(app, bookingId, "complete");
            }
            case "CANCELLED" -> transition(app, bookingId, "cancel");
            default -> throw new IllegalArgumentException("Unsupported status: " + targetStatus);
        }
    }

    private void transition(RunningApplication app, long bookingId, String action) throws Exception {
        app.mockMvc().perform(post("/api/v1/bookings/{bookingId}/{action}", bookingId, action))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId));
    }

    private JsonNode getBookingByStatus(RunningApplication app, String expectedStatus) throws Exception {
        MvcResult result = app.mockMvc().perform(get("/api/v1/bookings")
                        .param("status", expectedStatus)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();
        return app.json(result).get("content").get(0);
    }

    private void assertPersistedStatus(
            RunningApplication app,
            long customerId,
            String expectedStatus,
            Map<String, Long> bookingIdsByStatus,
            Map<String, String> referencesByStatus) throws Exception {
        String bookingReference = referencesByStatus.get(expectedStatus);
        app.mockMvc().perform(get("/api/v1/bookings/{bookingReference}", bookingReference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingIdsByStatus.get(expectedStatus)))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.bookingReference").value(bookingReference));
    }

    private long idByReference(RunningApplication app, String bookingReference) throws Exception {
        MvcResult result = app.mockMvc().perform(get("/api/v1/bookings/{bookingReference}", bookingReference))
                .andExpect(status().isOk())
                .andReturn();
        return app.json(result).get("id").asLong();
    }

    private static int sequence(String bookingReference) {
        return Integer.parseInt(bookingReference.substring(bookingReference.lastIndexOf('-') + 1));
    }

    private static Iterable<String> references(JsonNode content) {
        return StreamSupport.stream(content.spliterator(), false)
                .map(booking -> booking.get("bookingReference").asText())
                .toList();
    }

    private static String durableFreightRequest() {
        return """
                {
                  "customerId": 3101,
                  "scheduleId": 1101,
                  "quoteId": 2101,
                  "customer": {
                    "name": "Durable Freight Co.",
                    "email": "durable.freight@example.com",
                    "phone": "+36-1-555-1101"
                  },
                  "cargo": {
                    "description": "Restart-visible cargo",
                    "weightKg": 2100.25
                  },
                  "equipment": [
                    { "type": "20FT", "quantity": 1 },
                    { "type": "REEFER", "quantity": 1 }
                  ]
                }
                """;
    }

    private static String customerRequest(long customerId) {
        return tableRequest(
                customerId,
                1100 + customerId,
                2100 + customerId,
                "Durable Customer " + customerId,
                "durable." + customerId + "@example.com",
                "Durable cargo " + customerId,
                "100.00",
                "20FT");
    }

    private static String tableRequest(
            long customerId,
            long scheduleId,
            long quoteId,
            String customerName,
            String customerEmail,
            String cargoDescription,
            String weightKg,
            String equipmentType) {
        return """
                {
                  "customerId": %d,
                  "scheduleId": %d,
                  "quoteId": %d,
                  "customer": {
                    "name": "%s",
                    "email": "%s"
                  },
                  "cargo": {
                    "description": "%s",
                    "weightKg": %s
                  },
                  "equipment": [
                    { "type": "%s", "quantity": 1 }
                  ]
                }
                """.formatted(
                customerId,
                scheduleId,
                quoteId,
                customerName,
                customerEmail,
                cargoDescription,
                new BigDecimal(weightKg).toPlainString(),
                equipmentType);
    }

    private record RunningApplication(
            ConfigurableApplicationContext context,
            MockMvc mockMvc,
            ObjectMapper objectMapper) implements AutoCloseable {

        JsonNode json(MvcResult result) throws Exception {
            return objectMapper.readTree(result.getResponse().getContentAsString());
        }

        @Override
        public void close() {
            context.close();
        }
    }
}

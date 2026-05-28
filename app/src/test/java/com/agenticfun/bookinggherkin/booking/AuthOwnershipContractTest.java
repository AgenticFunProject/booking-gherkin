package com.agenticfun.bookinggherkin.booking;

import static com.agenticfun.bookinggherkin.security.JwtTokenFixtures.SECRET;
import static com.agenticfun.bookinggherkin.security.JwtTokenFixtures.bearer;
import static com.agenticfun.bookinggherkin.security.JwtTokenFixtures.bearerWithSnakeCaseCustomerId;
import static com.agenticfun.bookinggherkin.security.JwtTokenFixtures.token;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "booking.security.enabled=true")
@AutoConfigureMockMvc
class AuthOwnershipContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest(name = "{0} rejected")
    @MethodSource("invalidCredentials")
    void invalidOrAbsentJwtCredentialsAreRejectedFromProtectedBookingRoutes(
            String name,
            String authorizationHeader) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/bookings");
        if (authorizationHeader != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings"));
    }

    @Test
    void customerTokensCanCreateListReadAndCancelTheirOwnBookings() throws Exception {
        String customerToken = bearer("CUSTOMER", 3001);
        long id = createBooking(customerToken, 3001);

        mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "3001")
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customerId", everyItem(is(3001))));

        mockMvc.perform(get("/api/v1/bookings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(3001));

        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", id)
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(3001))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customerOwnershipChecksRejectAccessToAnotherCustomersData() throws Exception {
        String adminToken = bearer("ADMIN");
        createBooking(adminToken, 3001);
        long otherCustomerBookingId = createBooking(adminToken, 3002);
        String customerToken = bearer("CUSTOMER", 3001);

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequest(3002))
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "3002")
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(get("/api/v1/bookings/{id}", otherCustomerBookingId)
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", otherCustomerBookingId)
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void customerListRequestsRequireExplicitMatchingCustomerIdQueryParameter() throws Exception {
        String customerToken = bearer("CUSTOMER", 3001);

        mockMvc.perform(get("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("customerId query parameter is required for customer callers"));

        mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "3002")
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "3001")
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "customer without identity cannot {0}")
    @MethodSource("customerActionsRequiringIdentity")
    void customerTokensWithoutCustomerIdentityClaimsCannotPassOwnershipChecks(
            String action,
            MockHttpServletRequestBuilder request) throws Exception {
        String customerToken = bearer("CUSTOMER");

        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void customerIdentityCanUseSnakeCaseCustomerIdClaim() throws Exception {
        long id = createBooking(bearer("ADMIN"), 3001);
        String customerToken = bearerWithSnakeCaseCustomerId("CUSTOMER", 3001);

        mockMvc.perform(get("/api/v1/bookings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(3001));
    }

    @Test
    void operatorsCanViewAllBookingsAndPerformLifecycleTransitions() throws Exception {
        long id = createBooking(bearer("ADMIN"), 3001);
        String operatorToken = bearer("OPERATOR");

        mockMvc.perform(get("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(patch("/api/v1/bookings/{id}/start", id)
                        .header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/bookings/{id}/complete", id)
                        .header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @ParameterizedTest(name = "operator cannot {0}")
    @MethodSource("operatorForbiddenActions")
    void operatorsCannotCreateOrCancelBookings(String action, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer("OPERATOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void adminsHaveFullBookingAndProtectedActuatorAccess() throws Exception {
        String adminToken = bearer("ADMIN");
        long id = createBooking(adminToken, 9001);

        mockMvc.perform(get("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", id)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long lifecycleId = createBooking(adminToken, 9002);
        mockMvc.perform(patch("/api/v1/bookings/{id}/confirm", lifecycleId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(patch("/api/v1/bookings/{id}/start", lifecycleId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(patch("/api/v1/bookings/{id}/complete", lifecycleId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void serviceCallersCanCreateReadListAndCancelOnBehalfOfCustomers() throws Exception {
        String serviceToken = bearer("SERVICE");
        long id = createBooking(serviceToken, 7001);

        mockMvc.perform(get("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, serviceToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings")
                        .param("customerId", "7001")
                        .header(HttpHeaders.AUTHORIZATION, serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customerId", everyItem(is(7001))));

        mockMvc.perform(get("/api/v1/bookings/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(7001));

        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", id)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @ParameterizedTest(name = "service caller cannot {0}")
    @MethodSource("serviceForbiddenActions")
    void serviceCallersCannotPerformOperatorLifecycleTransitions(
            String action,
            MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer("SERVICE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    private long createBooking(String bearerToken, long customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequest(customerId))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value((int) customerId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return json(result).get("id").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    static Stream<Arguments> invalidCredentials() {
        return Stream.of(
                Arguments.of("no Authorization header", null),
                Arguments.of("Authorization header without Bearer token", "Basic abc123"),
                Arguments.of("malformed Bearer token", "Bearer malformed"),
                Arguments.of("expired Bearer token", "Bearer " + token("CUSTOMER", 3001L, "customerId",
                        "test-issuer", "equipments-service", SECRET, Instant.now().minusSeconds(60))),
                Arguments.of("Bearer token signed with wrong key", "Bearer " + token("CUSTOMER", 3001L, "customerId",
                        "test-issuer", "equipments-service", "wrong-secret", Instant.now().plusSeconds(3600))),
                Arguments.of("Bearer token with wrong issuer", "Bearer " + token("CUSTOMER", 3001L, "customerId",
                        "wrong-issuer", "equipments-service", SECRET, Instant.now().plusSeconds(3600))),
                Arguments.of("Bearer token with wrong audience", "Bearer " + token("CUSTOMER", 3001L, "customerId",
                        "test-issuer", "other-service", SECRET, Instant.now().plusSeconds(3600))));
    }

    static Stream<Arguments> customerActionsRequiringIdentity() {
        return Stream.of(
                Arguments.of("create a booking", post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequest(3001))),
                Arguments.of("list bookings with customerId", get("/api/v1/bookings").param("customerId", "3001")),
                Arguments.of("list bookings without customerId", get("/api/v1/bookings")),
                Arguments.of("retrieve a booking", get("/api/v1/bookings/1")),
                Arguments.of("cancel a booking", patch("/api/v1/bookings/1/cancel")));
    }

    static Stream<Arguments> operatorForbiddenActions() {
        return Stream.of(
                Arguments.of("create a booking", post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingRequest(3001))),
                Arguments.of("cancel a pending booking", patch("/api/v1/bookings/1/cancel")));
    }

    static Stream<Arguments> serviceForbiddenActions() {
        return Stream.of(
                Arguments.of("confirm a booking", patch("/api/v1/bookings/1/confirm")),
                Arguments.of("start a booking", patch("/api/v1/bookings/1/start")),
                Arguments.of("complete a booking", patch("/api/v1/bookings/1/complete")));
    }
}

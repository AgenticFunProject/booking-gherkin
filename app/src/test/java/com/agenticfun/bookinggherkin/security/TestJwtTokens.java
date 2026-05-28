package com.agenticfun.bookinggherkin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TestJwtTokens {

    public static final String SECRET = "booking-gherkin-test-secret-for-hs256-validation";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestJwtTokens() {
    }

    public static String bearer(String role) {
        return "Bearer " + token(role, null, "customerId", "test-issuer", "equipments-service", SECRET,
                Instant.now().plusSeconds(3600));
    }

    public static String bearer(String role, long customerId) {
        return "Bearer " + token(role, customerId, "customerId", "test-issuer", "equipments-service", SECRET,
                Instant.now().plusSeconds(3600));
    }

    public static String bearerWithSnakeCaseCustomerId(String role, long customerId) {
        return "Bearer " + token(role, customerId, "customer_id", "test-issuer", "equipments-service", SECRET,
                Instant.now().plusSeconds(3600));
    }

    public static String token(
            String role,
            Long customerId,
            String customerClaim,
            String issuer,
            String audience,
            String secret,
            Instant expiresAt) {
        try {
            String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", issuer);
            claims.put("aud", audience);
            claims.put("exp", expiresAt.getEpochSecond());
            claims.put("role", role);
            if (customerId != null) {
                claims.put(customerClaim, customerId);
            }
            String payload = encode(claims);
            return header + "." + payload + "." + sign(header + "." + payload, secret);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create test JWT", ex);
        }
    }

    private static String encode(Object value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
    }

    private static String sign(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}

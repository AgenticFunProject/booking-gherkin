package com.agenticfun.bookinggherkin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class DeterministicJwtDecoder {

    private final BookingSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public DeterministicJwtDecoder(BookingSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AuthenticatedCaller decode(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new JwtAuthenticationException("Malformed Bearer token");
        }

        JsonNode header = readJson(parts[0], "JWT header");
        if (!"HS256".equals(text(header, "alg"))) {
            throw new JwtAuthenticationException("Unsupported JWT algorithm");
        }
        verifySignature(parts[0], parts[1], parts[2]);

        JsonNode claims = readJson(parts[1], "JWT claims");
        requireExpectedIssuer(claims);
        requireExpectedAudience(claims);
        requireNotExpired(claims);

        String role = firstRole(claims);
        Long customerId = longClaim(claims, "customerId");
        if (customerId == null) {
            customerId = longClaim(claims, "customer_id");
        }
        return new AuthenticatedCaller(role, customerId);
    }

    private JsonNode readJson(String encoded, String description) {
        try {
            return objectMapper.readTree(Base64.getUrlDecoder().decode(encoded));
        } catch (Exception ex) {
            throw new JwtAuthenticationException("Malformed " + description);
        }
    }

    private void verifySignature(String header, String payload, String actualSignature) {
        String expectedSignature = sign(header + "." + payload);
        if (!constantTimeEquals(expectedSignature, actualSignature)) {
            throw new JwtAuthenticationException("Invalid JWT signature");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new JwtAuthenticationException("JWT signature validation failed");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            diff |= expectedBytes[i] ^ actualBytes[i];
        }
        return diff == 0;
    }

    private void requireExpectedIssuer(JsonNode claims) {
        if (!properties.getJwt().getIssuer().equals(text(claims, "iss"))) {
            throw new JwtAuthenticationException("Invalid JWT issuer");
        }
    }

    private void requireExpectedAudience(JsonNode claims) {
        JsonNode audience = claims.get("aud");
        if (audience == null) {
            throw new JwtAuthenticationException("Invalid JWT audience");
        }
        if (audience.isTextual() && properties.getJwt().getAudience().equals(audience.asText())) {
            return;
        }
        if (audience.isArray()) {
            for (JsonNode item : audience) {
                if (properties.getJwt().getAudience().equals(item.asText())) {
                    return;
                }
            }
        }
        throw new JwtAuthenticationException("Invalid JWT audience");
    }

    private void requireNotExpired(JsonNode claims) {
        JsonNode expiresAt = claims.get("exp");
        if (expiresAt == null || !expiresAt.canConvertToLong()) {
            throw new JwtAuthenticationException("Missing JWT expiration");
        }
        if (expiresAt.asLong() <= clock.instant().getEpochSecond()) {
            throw new JwtAuthenticationException("Expired JWT");
        }
    }

    private static String firstRole(JsonNode claims) {
        List<String> roles = new ArrayList<>();
        addRole(roles, claims.get("role"));
        addRole(roles, claims.get("roles"));
        return roles.isEmpty() ? null : normalizeRole(roles.getFirst());
    }

    private static void addRole(List<String> roles, JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            roles.add(node.asText());
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    roles.add(item.asText());
                }
            }
        }
    }

    private static String normalizeRole(String role) {
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private static Long longClaim(JsonNode claims, String field) {
        JsonNode value = claims.get(field);
        return value == null || !value.canConvertToLong() ? null : value.asLong();
    }
}

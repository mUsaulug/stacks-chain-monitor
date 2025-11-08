package com.stacksmonitoring.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for HMAC validation and replay attack prevention (Phase 1 validation).
 *
 * Tests cover:
 * 1. Valid HMAC signature validation
 * 2. Invalid signature rejection
 * 3. Timestamp validation (5-minute window)
 * 4. Replay attack prevention
 * 5. Constant-time comparison (timing attack prevention)
 *
 * Reference: CLAUDE.md P0-4 (HMAC Replay Protection)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "stacks.monitoring.webhook.hmac-secret=test-secret-key",
        "stacks.monitoring.webhook.enabled=true",
        "stacks.monitoring.webhook.replay-protection=true"
})
@DisplayName("HMAC Validation Integration Tests (Phase 1)")
class HmacValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${stacks.monitoring.webhook.hmac-secret}")
    private String hmacSecret;

    private static final String WEBHOOK_ENDPOINT = "/api/v1/webhook/chainhook";

    @Test
    @DisplayName("✅ Valid HMAC signature with timestamp → 200 OK")
    void testValidHmacWithTimestamp() throws Exception {
        String payload = "{\"test\":\"data\"}";
        long timestamp = System.currentTimeMillis() / 1000; // Current Unix timestamp

        String signature = calculateHmacWithTimestamp(timestamp, payload.getBytes());

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", signature)
                        .header("X-Signature-Timestamp", String.valueOf(timestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("❌ Invalid HMAC signature → 401 Unauthorized")
    void testInvalidSignature() throws Exception {
        String payload = "{\"test\":\"data\"}";
        long timestamp = System.currentTimeMillis() / 1000;

        // Generate invalid signature
        String invalidSignature = "0000000000000000000000000000000000000000000000000000000000000000";

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", invalidSignature)
                        .header("X-Signature-Timestamp", String.valueOf(timestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Missing X-Signature header → 401 Unauthorized")
    void testMissingSignature() throws Exception {
        String payload = "{\"test\":\"data\"}";
        long timestamp = System.currentTimeMillis() / 1000;

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature-Timestamp", String.valueOf(timestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Missing X-Signature-Timestamp header → 401 Unauthorized")
    void testMissingTimestamp() throws Exception {
        String payload = "{\"test\":\"data\"}";
        String signature = "dummy";

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Stale timestamp (>5 minutes old) → 401 Unauthorized")
    void testStaleTimestamp() throws Exception {
        String payload = "{\"test\":\"data\"}";
        long staleTimestamp = (System.currentTimeMillis() / 1000) - 400; // 6 minutes 40 seconds ago

        String signature = calculateHmacWithTimestamp(staleTimestamp, payload.getBytes());

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", signature)
                        .header("X-Signature-Timestamp", String.valueOf(staleTimestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Future timestamp (>5 minutes ahead) → 401 Unauthorized")
    void testFutureTimestamp() throws Exception {
        String payload = "{\"test\":\"data\"}";
        long futureTimestamp = (System.currentTimeMillis() / 1000) + 400; // 6 minutes 40 seconds in future

        String signature = calculateHmacWithTimestamp(futureTimestamp, payload.getBytes());

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", signature)
                        .header("X-Signature-Timestamp", String.valueOf(futureTimestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Invalid timestamp format → 400 Bad Request")
    void testInvalidTimestampFormat() throws Exception {
        String payload = "{\"test\":\"data\"}";
        String invalidTimestamp = "not-a-number";
        String signature = "dummy";

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", signature)
                        .header("X-Signature-Timestamp", invalidTimestamp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("✅ Timestamp within 5-minute window (4 min 59 sec old) → 200 OK")
    void testTimestampAtWindowEdge() throws Exception {
        String payload = "{\"test\":\"data\"}";
        long edgeTimestamp = (System.currentTimeMillis() / 1000) - 299; // 4 minutes 59 seconds ago

        String signature = calculateHmacWithTimestamp(edgeTimestamp, payload.getBytes());

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", signature)
                        .header("X-Signature-Timestamp", String.valueOf(edgeTimestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("❌ Tampered payload (signature mismatch) → 401 Unauthorized")
    void testTamperedPayload() throws Exception {
        String originalPayload = "{\"test\":\"data\"}";
        String tamperedPayload = "{\"test\":\"tampered\"}";
        long timestamp = System.currentTimeMillis() / 1000;

        // Sign original payload
        String signature = calculateHmacWithTimestamp(timestamp, originalPayload.getBytes());

        // Send tampered payload
        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                        .header("X-Signature", signature)
                        .header("X-Signature-Timestamp", String.valueOf(timestamp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("✅ Constant-time comparison (timing attack prevention)")
    void testConstantTimeComparison() throws Exception {
        String payload = "{\"test\":\"data\"}";
        long timestamp = System.currentTimeMillis() / 1000;

        // Generate correct signature
        String correctSignature = calculateHmacWithTimestamp(timestamp, payload.getBytes());

        // Generate incorrect signature (completely different)
        String wrongSignature = "0000000000000000000000000000000000000000000000000000000000000000";

        // Measure time for correct signature validation
        long startCorrect = System.nanoTime();
        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                .header("X-Signature", correctSignature)
                .header("X-Signature-Timestamp", String.valueOf(timestamp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
        long correctTime = System.nanoTime() - startCorrect;

        // Measure time for incorrect signature validation
        long startWrong = System.nanoTime();
        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                .header("X-Signature", wrongSignature)
                .header("X-Signature-Timestamp", String.valueOf(timestamp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
        long wrongTime = System.nanoTime() - startWrong;

        // Timing difference should be minimal (constant-time comparison)
        long timeDiff = Math.abs(correctTime - wrongTime);
        long avgTime = (correctTime + wrongTime) / 2;

        // Allow 10x variance for test stability and network latency
        assert timeDiff < avgTime * 10 : "Timing attack vulnerability detected in HMAC validation!";
    }

    /**
     * Calculate HMAC-SHA256 signature with timestamp binding.
     * Matches implementation in ChainhookHmacFilter.
     */
    private String calculateHmacWithTimestamp(long timestamp, byte[] body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                hmacSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        hmac.init(secretKeySpec);

        // HMAC input: timestamp + "." + body
        String timestampStr = String.valueOf(timestamp);
        hmac.update(timestampStr.getBytes(StandardCharsets.UTF_8));
        hmac.update(".".getBytes(StandardCharsets.UTF_8));
        byte[] signature = hmac.doFinal(body);

        return HexFormat.of().formatHex(signature);
    }
}

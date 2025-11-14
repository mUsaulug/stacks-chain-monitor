package com.stacksmonitoring.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for HMAC nonce replay protection (P0-SEC-1).
 *
 * Tests verify that:
 * 1. Missing X-Nonce header → 401
 * 2. First use of nonce → 200 (or 202 if async processing)
 * 3. Second use of same nonce → 401 (replay attack blocked)
 *
 * These tests require Redis and replay protection to be enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "stacks.monitoring.webhook.replay-protection=true",
    "stacks.monitoring.webhook.hmac-secret=test-secret-for-integration-tests",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
class HmacNonceValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String WEBHOOK_ENDPOINT = "/api/v1/webhook/chainhook";
    private static final String HMAC_SECRET = "test-secret-for-integration-tests";
    private static final String WEBHOOK_PAYLOAD = "{\"apply\":[],\"rollback\":[]}";

    @BeforeEach
    void setUp() {
        // Clear Redis nonce keys before each test
        redisTemplate.keys("webhook:nonce:*").forEach(key -> redisTemplate.delete(key));
    }

    @Test
    @DisplayName("P0-SEC-1: Missing X-Nonce header should return 401")
    void testMissingNonceHeader() throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = calculateHmacSignature(timestamp, WEBHOOK_PAYLOAD);

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Signature-Timestamp", String.valueOf(timestamp))
                // X-Nonce header intentionally omitted
                .content(WEBHOOK_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0-SEC-1: First use of nonce should succeed (200 or 202)")
    void testFirstNonceUse() throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = "test-nonce-" + System.currentTimeMillis();
        String signature = calculateHmacSignature(timestamp, WEBHOOK_PAYLOAD);

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Signature-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce)
                .content(WEBHOOK_PAYLOAD))
                .andExpect(status().is2xxSuccessful());

        // Verify nonce was stored in Redis
        String nonceKey = "webhook:nonce:" + nonce;
        Boolean exists = redisTemplate.hasKey(nonceKey);
        assert Boolean.TRUE.equals(exists) : "Nonce should be stored in Redis after first use";
    }

    @Test
    @DisplayName("P0-SEC-1: Duplicate nonce (replay attack) should return 401")
    void testDuplicateNonceReplayAttack() throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = "replay-attack-nonce-" + System.currentTimeMillis();
        String signature = calculateHmacSignature(timestamp, WEBHOOK_PAYLOAD);

        // First request - should succeed
        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Signature-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce)
                .content(WEBHOOK_PAYLOAD))
                .andExpect(status().is2xxSuccessful());

        // Second request with same nonce (replay attack) - should fail
        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Signature-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce) // Same nonce
                .content(WEBHOOK_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0-SEC-1: Expired nonce (>5 min) can be reused")
    void testExpiredNonceCanBeReused() throws Exception {
        String nonce = "expired-nonce-" + System.currentTimeMillis();
        String nonceKey = "webhook:nonce:" + nonce;

        // Manually insert nonce with very short TTL (1 second)
        redisTemplate.opsForValue().set(nonceKey, "1", Duration.ofSeconds(1));

        // Wait for nonce to expire
        Thread.sleep(1500);

        // Nonce should be expired and reusable
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = calculateHmacSignature(timestamp, WEBHOOK_PAYLOAD);

        mockMvc.perform(post(WEBHOOK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Signature-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce)
                .content(WEBHOOK_PAYLOAD))
                .andExpect(status().is2xxSuccessful());
    }

    /**
     * Calculate HMAC signature with timestamp binding.
     * Format: hmac(secret, timestamp + "." + body)
     */
    private String calculateHmacSignature(long timestamp, String body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                HMAC_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        hmac.init(secretKeySpec);

        String timestampStr = String.valueOf(timestamp);
        hmac.update(timestampStr.getBytes(StandardCharsets.UTF_8));
        hmac.update(".".getBytes(StandardCharsets.UTF_8));
        byte[] signature = hmac.doFinal(body.getBytes(StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(signature);
    }
}

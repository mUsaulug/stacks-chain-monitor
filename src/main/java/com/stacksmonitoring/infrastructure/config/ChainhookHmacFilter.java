package com.stacksmonitoring.infrastructure.config;

import com.stacksmonitoring.application.service.WebhookArchivalService;
import com.stacksmonitoring.domain.model.webhook.RawWebhookEvent;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.time.Duration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 validation filter for Chainhook webhook endpoints.
 * Validates signature in X-Signature header to ensure webhook authenticity.
 *
 * Event Sourcing Integration (A.2):
 * - Archives ALL webhooks BEFORE validation (PENDING status)
 * - Updates status to REJECTED if HMAC validation fails
 * - Controller updates status to PROCESSED/FAILED after processing
 *
 * Security Enhancements (OWASP Best Practices):
 * - Timestamp validation: Reject requests older than 5 minutes
 * - HMAC includes timestamp: hmac(secret, timestamp + "." + body)
 * - Constant-time comparison: Prevent timing attacks
 *
 * Replay Attack Prevention (P0-4):
 * 1. Client includes X-Signature-Timestamp header (Unix seconds)
 * 2. Client includes X-Nonce header (unique request ID)
 * 3. Filter rejects requests outside 5-minute window
 * 4. Filter rejects nonce reuse via Redis SETNX (atomic check-and-set)
 * 5. HMAC calculated as: hmac(secret, timestamp + "." + body)
 *
 * Redis Nonce Tracking:
 * - Key format: webhook:nonce:{nonce}
 * - TTL: MAX_TIMESTAMP_SKEW_SECONDS (5 minutes)
 * - Atomic operation: SETNX prevents concurrent replay
 *
 * Reference: CLAUDE.md P0-4 (HMAC Replay Protection), A.2 (Webhook Archival)
 */
@Slf4j
@Component
public class ChainhookHmacFilter extends OncePerRequestFilter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String TIMESTAMP_HEADER = "X-Signature-Timestamp";
    private static final String NONCE_HEADER = "X-Nonce";
    private static final long MAX_TIMESTAMP_SKEW_SECONDS = 300; // 5 minutes
    private static final String WEBHOOK_ID_ATTRIBUTE = "webhookEventId";
    private static final String NONCE_REDIS_KEY_PREFIX = "webhook:nonce:";

    @Value("${stacks.monitoring.webhook.hmac-secret}")
    private String hmacSecret;

    @Value("${stacks.monitoring.webhook.enabled:true}")
    private boolean webhookEnabled;

    @Value("${stacks.monitoring.webhook.replay-protection:true}")
    private boolean replayProtectionEnabled;

    @Autowired(required = false)
    private WebhookArchivalService webhookArchivalService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * Validate HMAC secret on application startup (P1 - Security).
     * Prevents deploying with weak or default secrets.
     */
    @PostConstruct
    public void validateHmacSecret() {
        if (!webhookEnabled) {
            log.info("Webhook validation disabled - skipping HMAC secret validation");
            return;
        }

        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalStateException(
                "CHAINHOOK_HMAC_SECRET must be configured when webhooks are enabled. " +
                "Set environment variable CHAINHOOK_HMAC_SECRET to a strong random value."
            );
        }

        // Check for weak default values that should never be used in production
        String secretLower = hmacSecret.toLowerCase();
        if (secretLower.equals("change-me-in-production") ||
            secretLower.equals("test-secret") ||
            secretLower.equals("secret") ||
            secretLower.equals("password") ||
            secretLower.equals("default") ||
            secretLower.length() < 32) {

            throw new IllegalStateException(
                "CHAINHOOK_HMAC_SECRET is weak or uses a default value. " +
                "Current value: '" + hmacSecret + "' (length: " + hmacSecret.length() + "). " +
                "REQUIRED: Strong random secret with at least 32 characters. " +
                "Generate with: openssl rand -hex 32"
            );
        }

        log.info("HMAC secret validation passed (length: {} chars)", hmacSecret.length());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Skip if webhook validation is disabled
        if (!webhookEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only validate Chainhook webhook endpoint
        if (!request.getRequestURI().startsWith("/api/v1/webhook/chainhook")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request to allow reading body multiple times
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

        // Archive webhook BEFORE validation (Event Sourcing - A.2)
        Long webhookEventId = null;
        String requestBody = null;

        try {
            // Read request body for archival and validation
            byte[] requestBodyBytes = wrappedRequest.getContentAsByteArray();
            if (requestBodyBytes.length == 0) {
                // Body not yet cached, read it
                requestBodyBytes = wrappedRequest.getInputStream().readAllBytes();
            }
            requestBody = new String(requestBodyBytes, StandardCharsets.UTF_8);

            // Archive webhook with PENDING status (before any validation)
            if (webhookArchivalService != null) {
                RawWebhookEvent archived = webhookArchivalService.archiveIncomingWebhook(
                    wrappedRequest, requestBody);
                webhookEventId = archived.getId();

                // Store webhook ID in request attribute for controller access
                wrappedRequest.setAttribute(WEBHOOK_ID_ATTRIBUTE, webhookEventId);
                log.debug("Archived webhook with ID: {} (request ID: {})",
                    webhookEventId, archived.getRequestId());
            }

            // Extract signature from header
            String providedSignature = wrappedRequest.getHeader(SIGNATURE_HEADER);
            if (providedSignature == null || providedSignature.isEmpty()) {
                log.warn("Chainhook webhook rejected: Missing {} header", SIGNATURE_HEADER);
                markAsRejected(webhookEventId, "Missing HMAC signature header");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing HMAC signature");
                return;
            }

            // Extract and validate timestamp (replay attack prevention)
            String timestampHeader = wrappedRequest.getHeader(TIMESTAMP_HEADER);
            long timestamp = 0;

            if (replayProtectionEnabled) {
                if (timestampHeader == null || timestampHeader.isEmpty()) {
                    log.warn("Chainhook webhook rejected: Missing {} header", TIMESTAMP_HEADER);
                    markAsRejected(webhookEventId, "Missing timestamp header");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing timestamp header");
                    return;
                }

                try {
                    timestamp = Long.parseLong(timestampHeader);
                } catch (NumberFormatException e) {
                    log.warn("Chainhook webhook rejected: Invalid timestamp format");
                    markAsRejected(webhookEventId, "Invalid timestamp format: " + timestampHeader);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp format");
                    return;
                }

                // Validate timestamp freshness (within 5-minute window)
                long currentTime = System.currentTimeMillis() / 1000; // Unix seconds
                long timeDiff = Math.abs(currentTime - timestamp);

                if (timeDiff > MAX_TIMESTAMP_SKEW_SECONDS) {
                    log.warn("Chainhook webhook rejected: Stale timestamp (diff: {}s, max: {}s)",
                            timeDiff, MAX_TIMESTAMP_SKEW_SECONDS);
                    markAsRejected(webhookEventId,
                        String.format("Stale timestamp (diff: %ds, max: %ds)", timeDiff, MAX_TIMESTAMP_SKEW_SECONDS));
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                            "Request timestamp outside acceptable window");
                    return;
                }

                // Nonce validation (prevent replay attacks within timestamp window)
                String nonce = wrappedRequest.getHeader(NONCE_HEADER);
                if (nonce == null || nonce.isEmpty()) {
                    log.warn("Chainhook webhook rejected: Missing {} header", NONCE_HEADER);
                    markAsRejected(webhookEventId, "Missing nonce header");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing nonce header");
                    return;
                }

                // Check nonce uniqueness via Redis (atomic SETNX with TTL)
                // If nonce already exists, this is a replay attack
                if (redisTemplate != null) {
                    String nonceKey = NONCE_REDIS_KEY_PREFIX + nonce;
                    Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(
                        nonceKey,
                        "1",
                        Duration.ofSeconds(MAX_TIMESTAMP_SKEW_SECONDS)
                    );

                    if (!Boolean.TRUE.equals(wasAbsent)) {
                        log.warn("Chainhook webhook rejected: Nonce {} already used (replay attack detected)", nonce);
                        markAsRejected(webhookEventId, "Nonce replay attack detected: " + nonce);
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Nonce already used");
                        return;
                    }

                    log.debug("Nonce {} accepted and stored in Redis with TTL {}s", nonce, MAX_TIMESTAMP_SKEW_SECONDS);
                } else {
                    log.warn("Redis not available - nonce validation skipped (replay attacks possible!)");
                }
            }

            // Calculate expected signature (includes timestamp if replay protection enabled)
            String expectedSignature = replayProtectionEnabled
                    ? calculateHmacSignatureWithTimestamp(timestamp, requestBody.getBytes(StandardCharsets.UTF_8))
                    : calculateHmacSignature(requestBody.getBytes(StandardCharsets.UTF_8));

            // Compare signatures (constant-time comparison)
            if (!MessageDigest.isEqual(
                    providedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Chainhook webhook rejected: Invalid HMAC signature");
                markAsRejected(webhookEventId, "Invalid HMAC signature");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid HMAC signature");
                return;
            }

            log.debug("Chainhook webhook HMAC validation successful (replay protection: {})",
                    replayProtectionEnabled ? "enabled" : "disabled");
            filterChain.doFilter(wrappedRequest, response);

        } catch (Exception e) {
            log.error("HMAC validation error: {}", e.getMessage(), e);
            markAsRejected(webhookEventId, "HMAC validation error: " + e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "HMAC validation failed");
        }
    }

    /**
     * Mark archived webhook as REJECTED (validation failed, cannot be replayed).
     */
    private void markAsRejected(Long webhookEventId, String errorMessage) {
        if (webhookEventId != null && webhookArchivalService != null) {
            try {
                webhookArchivalService.markAsRejected(webhookEventId, errorMessage);
            } catch (Exception e) {
                log.error("Failed to mark webhook {} as REJECTED: {}", webhookEventId, e.getMessage());
            }
        }
    }

    /**
     * Calculate HMAC-SHA256 signature for request body (legacy, without timestamp).
     * Used when replay protection is disabled.
     */
    private String calculateHmacSignature(byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                hmacSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM);
        hmac.init(secretKeySpec);
        byte[] signature = hmac.doFinal(data);
        return HexFormat.of().formatHex(signature);
    }

    /**
     * Calculate HMAC-SHA256 signature with timestamp binding.
     * Format: hmac(secret, timestamp + "." + body)
     *
     * This binds the signature to a specific time window, preventing replay attacks.
     * Attacker cannot reuse old signatures because timestamp is part of HMAC input.
     *
     * @param timestamp Unix timestamp in seconds
     * @param body Request body bytes
     * @return Hex-encoded HMAC signature
     */
    private String calculateHmacSignatureWithTimestamp(long timestamp, byte[] body)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                hmacSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM);
        hmac.init(secretKeySpec);

        // HMAC input: timestamp + "." + body
        String timestampStr = String.valueOf(timestamp);
        hmac.update(timestampStr.getBytes(StandardCharsets.UTF_8));
        hmac.update(".".getBytes(StandardCharsets.UTF_8));
        byte[] signature = hmac.doFinal(body);

        return HexFormat.of().formatHex(signature);
    }
}

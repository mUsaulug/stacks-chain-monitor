package com.stacksmonitoring.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

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
 * Security Enhancements (OWASP Best Practices):
 * - Timestamp validation: Reject requests older than 5 minutes
 * - HMAC includes timestamp: hmac(secret, timestamp + "." + body)
 * - Constant-time comparison: Prevent timing attacks
 *
 * Replay Attack Prevention:
 * 1. Client includes X-Signature-Timestamp header (Unix seconds)
 * 2. Filter rejects requests outside 5-minute window
 * 3. HMAC calculated as: hmac(secret, timestamp + "." + body)
 * 4. Future enhancement: Nonce tracking with Redis (P0-2)
 *
 * Reference: CLAUDE.md P0-4 (HMAC Replay Protection)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainhookHmacFilter extends OncePerRequestFilter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String TIMESTAMP_HEADER = "X-Signature-Timestamp";
    private static final long MAX_TIMESTAMP_SKEW_SECONDS = 300; // 5 minutes

    @Value("${stacks.monitoring.webhook.hmac-secret}")
    private String hmacSecret;

    @Value("${stacks.monitoring.webhook.enabled:true}")
    private boolean webhookEnabled;

    @Value("${stacks.monitoring.webhook.replay-protection:true}")
    private boolean replayProtectionEnabled;

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

        try {
            // Extract signature from header
            String providedSignature = wrappedRequest.getHeader(SIGNATURE_HEADER);
            if (providedSignature == null || providedSignature.isEmpty()) {
                log.warn("Chainhook webhook rejected: Missing {} header", SIGNATURE_HEADER);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing HMAC signature");
                return;
            }

            // Extract and validate timestamp (replay attack prevention)
            String timestampHeader = wrappedRequest.getHeader(TIMESTAMP_HEADER);
            long timestamp = 0;

            if (replayProtectionEnabled) {
                if (timestampHeader == null || timestampHeader.isEmpty()) {
                    log.warn("Chainhook webhook rejected: Missing {} header", TIMESTAMP_HEADER);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing timestamp header");
                    return;
                }

                try {
                    timestamp = Long.parseLong(timestampHeader);
                } catch (NumberFormatException e) {
                    log.warn("Chainhook webhook rejected: Invalid timestamp format");
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp format");
                    return;
                }

                // Validate timestamp freshness (within 5-minute window)
                long currentTime = System.currentTimeMillis() / 1000; // Unix seconds
                long timeDiff = Math.abs(currentTime - timestamp);

                if (timeDiff > MAX_TIMESTAMP_SKEW_SECONDS) {
                    log.warn("Chainhook webhook rejected: Stale timestamp (diff: {}s, max: {}s)",
                            timeDiff, MAX_TIMESTAMP_SKEW_SECONDS);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                            "Request timestamp outside acceptable window");
                    return;
                }
            }

            // Read request body
            byte[] requestBody = wrappedRequest.getContentAsByteArray();
            if (requestBody.length == 0) {
                // Body not yet cached, read it
                requestBody = wrappedRequest.getInputStream().readAllBytes();
            }

            // Calculate expected signature (includes timestamp if replay protection enabled)
            String expectedSignature = replayProtectionEnabled
                    ? calculateHmacSignatureWithTimestamp(timestamp, requestBody)
                    : calculateHmacSignature(requestBody);

            // Compare signatures (constant-time comparison)
            if (!MessageDigest.isEqual(
                    providedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Chainhook webhook rejected: Invalid HMAC signature");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid HMAC signature");
                return;
            }

            log.debug("Chainhook webhook HMAC validation successful (replay protection: {})",
                    replayProtectionEnabled ? "enabled" : "disabled");
            filterChain.doFilter(wrappedRequest, response);

        } catch (Exception e) {
            log.error("HMAC validation error: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "HMAC validation failed");
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

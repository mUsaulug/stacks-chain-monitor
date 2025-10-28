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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainhookHmacFilter extends OncePerRequestFilter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Signature";

    @Value("${stacks.monitoring.webhook.hmac-secret}")
    private String hmacSecret;

    @Value("${stacks.monitoring.webhook.enabled:true}")
    private boolean webhookEnabled;

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

            // Read request body
            byte[] requestBody = wrappedRequest.getContentAsByteArray();
            if (requestBody.length == 0) {
                // Body not yet cached, read it
                requestBody = wrappedRequest.getInputStream().readAllBytes();
            }

            // Calculate expected signature
            String expectedSignature = calculateHmacSignature(requestBody);

            // Compare signatures (constant-time comparison)
            if (!MessageDigest.isEqual(
                    providedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Chainhook webhook rejected: Invalid HMAC signature");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid HMAC signature");
                return;
            }

            log.debug("Chainhook webhook HMAC validation successful");
            filterChain.doFilter(wrappedRequest, response);

        } catch (Exception e) {
            log.error("HMAC validation error: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "HMAC validation failed");
        }
    }

    /**
     * Calculate HMAC-SHA256 signature for request body.
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
}

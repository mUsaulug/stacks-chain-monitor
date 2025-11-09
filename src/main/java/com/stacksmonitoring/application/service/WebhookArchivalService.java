package com.stacksmonitoring.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.domain.model.webhook.RawWebhookEvent;
import com.stacksmonitoring.domain.repository.RawWebhookEventRepository;
import com.stacksmonitoring.domain.valueobject.WebhookProcessingStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for archiving webhook events (Event Sourcing pattern).
 * Stores all incoming webhooks for debugging, auditing, and replay capability.
 *
 * Pattern: Event Sourcing (Martin Fowler)
 * - Archive webhook BEFORE processing (even if HMAC validation fails)
 * - Track processing status (PENDING → PROCESSED/FAILED/REJECTED)
 * - Enable replay for FAILED webhooks
 *
 * Reference: PART A.2 - Raw Webhook Events Archive [P1]
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookArchivalService {

    private final RawWebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Archive incoming webhook with PENDING status.
     * Called BEFORE HMAC validation and processing.
     *
     * Uses REQUIRES_NEW propagation to ensure webhook is archived even if
     * processing transaction rolls back (FAILED webhooks should still be saved).
     *
     * @param request HTTP request
     * @param payloadJson Raw JSON payload
     * @return Archived webhook event with generated request ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RawWebhookEvent archiveIncomingWebhook(HttpServletRequest request, String payloadJson) {
        RawWebhookEvent event = new RawWebhookEvent();

        // Generate request ID for correlation with logs
        String requestId = UUID.randomUUID().toString();
        event.setRequestId(requestId);

        // Timing
        event.setReceivedAt(Instant.now());
        event.setProcessingStatus(WebhookProcessingStatus.PENDING);

        // Store HTTP headers (includes X-Signature for debugging)
        Map<String, String> headers = extractHeaders(request);
        event.setHeadersJson(headers);

        // Store raw JSON payload
        event.setPayloadJson(payloadJson);

        // Metadata
        event.setSourceIp(extractClientIp(request));
        event.setUserAgent(request.getHeader("User-Agent"));

        RawWebhookEvent saved = webhookEventRepository.save(event);
        log.debug("Archived incoming webhook with request ID: {}", requestId);

        return saved;
    }

    /**
     * Mark webhook as REJECTED (invalid signature or malformed payload).
     * Cannot be replayed.
     *
     * Uses REQUIRES_NEW to ensure update is committed even if calling transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsRejected(Long webhookId, String errorMessage) {
        webhookEventRepository.findById(webhookId).ifPresent(event -> {
            event.markAsRejected(errorMessage);
            webhookEventRepository.save(event);
            log.info("Webhook {} marked as REJECTED: {}", webhookId, errorMessage);
        });
    }

    /**
     * Mark webhook as FAILED (processing error, can be retried).
     *
     * Uses REQUIRES_NEW to ensure update is committed even if processing transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long webhookId, Exception exception) {
        webhookEventRepository.findById(webhookId).ifPresent(event -> {
            String errorMessage = exception.getMessage();
            String stackTrace = getStackTraceAsString(exception);
            event.markAsFailed(errorMessage, stackTrace);
            webhookEventRepository.save(event);
            log.warn("Webhook {} marked as FAILED: {}", webhookId, errorMessage);
        });
    }

    /**
     * Mark webhook as PROCESSED (successfully persisted to database).
     *
     * Uses REQUIRES_NEW to ensure update is committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsProcessed(Long webhookId) {
        webhookEventRepository.findById(webhookId).ifPresent(event -> {
            event.markAsProcessed();
            webhookEventRepository.save(event);
            log.debug("Webhook {} marked as PROCESSED", webhookId);
        });
    }

    /**
     * Extract all HTTP headers from request.
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
        }

        return headers;
    }

    /**
     * Extract client IP address (handles X-Forwarded-For for proxies).
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Convert exception stack trace to string for storage.
     */
    private String getStackTraceAsString(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}

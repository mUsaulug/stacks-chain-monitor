package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.api.dto.webhook.ChainhookPayloadDto;
import com.stacksmonitoring.application.service.WebhookArchivalService;
import com.stacksmonitoring.application.usecase.ProcessChainhookPayloadUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for handling Chainhook webhook callbacks.
 * Receives blockchain event notifications from Chainhook service.
 *
 * Event Sourcing Integration (A.2):
 * - Webhook archived by ChainhookHmacFilter BEFORE reaching this controller
 * - Controller retrieves webhook ID from request attribute
 * - Updates status to PROCESSED/FAILED after processing
 *
 * Reference: PART A.2 - Raw Webhook Events Archive [P1]
 */
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private static final String WEBHOOK_ID_ATTRIBUTE = "webhookEventId";

    private final ProcessChainhookPayloadUseCase processChainhookPayloadUseCase;
    private final WebhookArchivalService webhookArchivalService;

    /**
     * Endpoint for receiving Chainhook webhook payloads.
     * Protected by HMAC signature validation (configured in ChainhookHmacFilter).
     *
     * Event Sourcing Flow:
     * 1. ChainhookHmacFilter archives webhook with PENDING status
     * 2. Filter validates HMAC signature (marks as REJECTED if invalid)
     * 3. Controller retrieves webhook ID and processes asynchronously
     * 4. Async processing updates status to PROCESSED/FAILED
     *
     * Returns immediately with 200 OK to acknowledge receipt,
     * then processes the payload asynchronously.
     *
     * @param payload The Chainhook webhook payload
     * @param request HTTP request (contains webhook ID from filter)
     * @return Acknowledgement response
     */
    @PostMapping("/chainhook")
    public ResponseEntity<Map<String, Object>> handleChainhookWebhook(
            @RequestBody ChainhookPayloadDto payload,
            HttpServletRequest request) {

        log.info("Received Chainhook webhook with {} apply and {} rollback events",
            payload.getApply() != null ? payload.getApply().size() : 0,
            payload.getRollback() != null ? payload.getRollback().size() : 0);

        // Get webhook ID from request attribute (set by ChainhookHmacFilter)
        Long webhookEventId = (Long) request.getAttribute(WEBHOOK_ID_ATTRIBUTE);

        // Process asynchronously to return immediate response to Chainhook
        processPayloadAsync(payload, webhookEventId);

        // Return immediate acknowledgement (200 OK even for duplicates - A.1)
        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("message", "Webhook payload received and queued for processing");

        return ResponseEntity.ok(response);
    }

    /**
     * Process the webhook payload asynchronously.
     * This allows the controller to return immediately while processing continues.
     *
     * Updates webhook status:
     * - PROCESSED: Successfully persisted to database
     * - FAILED: Processing error (can be replayed)
     *
     * @param payload The webhook payload to process
     * @param webhookEventId ID of archived webhook event (from ChainhookHmacFilter)
     */
    @Async
    protected CompletableFuture<Void> processPayloadAsync(ChainhookPayloadDto payload, Long webhookEventId) {
        return CompletableFuture.runAsync(() -> {
            try {
                ProcessChainhookPayloadUseCase.ProcessingResult result =
                    processChainhookPayloadUseCase.processPayload(payload);

                if (result.success) {
                    log.info("Successfully processed webhook payload: {}", result.getSummary());

                    // Mark as PROCESSED (Event Sourcing - A.2)
                    if (webhookEventId != null) {
                        webhookArchivalService.markAsProcessed(webhookEventId);
                    }
                } else {
                    log.error("Failed to process webhook payload: {}", result.getSummary());

                    // Mark as FAILED (can be replayed)
                    if (webhookEventId != null) {
                        webhookArchivalService.markAsFailed(webhookEventId,
                            new Exception("Processing failed: " + result.getSummary()));
                    }
                }
            } catch (Exception e) {
                log.error("Error in async webhook processing", e);

                // Mark as FAILED with exception details (Event Sourcing - A.2)
                if (webhookEventId != null) {
                    webhookArchivalService.markAsFailed(webhookEventId, e);
                }
            }
        });
    }

    /**
     * Health check endpoint for webhook service.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "webhook");
        return ResponseEntity.ok(response);
    }

    /**
     * Exception handler for webhook processing errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Error processing webhook request", e);

        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", e.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

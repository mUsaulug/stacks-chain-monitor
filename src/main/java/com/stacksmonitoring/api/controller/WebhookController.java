package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.api.dto.webhook.ChainhookPayloadDto;
import com.stacksmonitoring.application.usecase.ProcessChainhookPayloadUseCase;
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
 */
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ProcessChainhookPayloadUseCase processChainhookPayloadUseCase;

    /**
     * Endpoint for receiving Chainhook webhook payloads.
     * Protected by HMAC signature validation (configured in ChainhookHmacFilter).
     *
     * Returns immediately with 200 OK to acknowledge receipt,
     * then processes the payload asynchronously.
     *
     * @param payload The Chainhook webhook payload
     * @return Acknowledgement response
     */
    @PostMapping("/chainhook")
    public ResponseEntity<Map<String, Object>> handleChainhookWebhook(
            @RequestBody ChainhookPayloadDto payload) {

        log.info("Received Chainhook webhook with {} apply and {} rollback events",
            payload.getApply() != null ? payload.getApply().size() : 0,
            payload.getRollback() != null ? payload.getRollback().size() : 0);

        // Process asynchronously to return immediate response to Chainhook
        processPayloadAsync(payload);

        // Return immediate acknowledgement
        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("message", "Webhook payload received and queued for processing");

        return ResponseEntity.ok(response);
    }

    /**
     * Process the webhook payload asynchronously.
     * This allows the controller to return immediately while processing continues.
     */
    @Async
    protected CompletableFuture<Void> processPayloadAsync(ChainhookPayloadDto payload) {
        return CompletableFuture.runAsync(() -> {
            try {
                ProcessChainhookPayloadUseCase.ProcessingResult result =
                    processChainhookPayloadUseCase.processPayload(payload);

                if (result.success) {
                    log.info("Successfully processed webhook payload: {}", result.getSummary());
                } else {
                    log.error("Failed to process webhook payload: {}", result.getSummary());
                }
            } catch (Exception e) {
                log.error("Error in async webhook processing", e);
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

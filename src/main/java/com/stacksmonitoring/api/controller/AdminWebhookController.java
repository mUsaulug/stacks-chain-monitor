package com.stacksmonitoring.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.api.dto.webhook.ChainhookPayloadDto;
import com.stacksmonitoring.application.service.WebhookArchivalService;
import com.stacksmonitoring.application.usecase.ProcessChainhookPayloadUseCase;
import com.stacksmonitoring.domain.model.webhook.RawWebhookEvent;
import com.stacksmonitoring.domain.repository.RawWebhookEventRepository;
import com.stacksmonitoring.domain.valueobject.WebhookProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for webhook management and replay.
 * Requires ADMIN role for access.
 *
 * Features:
 * - List archived webhooks with filtering by status
 * - Replay FAILED/PENDING webhooks
 * - View webhook details (headers, payload, errors)
 * - Get processing statistics
 *
 * Reference: PART A.2 - Raw Webhook Events Archive [P1]
 */
@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminWebhookController {

    private final RawWebhookEventRepository webhookEventRepository;
    private final WebhookArchivalService webhookArchivalService;
    private final ProcessChainhookPayloadUseCase processChainhookPayloadUseCase;
    private final ObjectMapper objectMapper;

    /**
     * Replay a FAILED or PENDING webhook.
     * REJECTED webhooks cannot be replayed (signature validation will fail again).
     *
     * @param id Webhook event ID
     * @return Processing result
     */
    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayWebhook(@PathVariable Long id) {
        log.info("Admin requested replay of webhook {}", id);

        RawWebhookEvent webhook = webhookEventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + id));

        Map<String, Object> response = new HashMap<>();

        // Check if webhook can be replayed
        if (!webhook.canBeReplayed()) {
            response.put("status", "error");
            response.put("message", "Webhook cannot be replayed (status: " + webhook.getProcessingStatus() + ")");
            response.put("hint", "Only FAILED and PENDING webhooks can be replayed. REJECTED webhooks have invalid signatures.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Parse stored JSON payload
            ChainhookPayloadDto payload = objectMapper.readValue(
                webhook.getPayloadJson(),
                ChainhookPayloadDto.class
            );

            // Reset status to PENDING before replay
            webhook.setProcessingStatus(WebhookProcessingStatus.PENDING);
            webhook.setProcessedAt(null);
            webhook.setErrorMessage(null);
            webhook.setErrorStackTrace(null);
            webhookEventRepository.save(webhook);

            // Process the webhook
            ProcessChainhookPayloadUseCase.ProcessingResult result =
                processChainhookPayloadUseCase.processPayload(payload);

            // Update status based on result
            if (result.success) {
                webhookArchivalService.markAsProcessed(id);
                response.put("status", "success");
                response.put("message", "Webhook replayed successfully");
                response.put("summary", result.getSummary());
            } else {
                webhookArchivalService.markAsFailed(id,
                    new Exception("Replay failed: " + result.getSummary()));
                response.put("status", "failed");
                response.put("message", "Webhook replay failed");
                response.put("summary", result.getSummary());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error replaying webhook {}: {}", id, e.getMessage(), e);
            webhookArchivalService.markAsFailed(id, e);

            response.put("status", "error");
            response.put("message", "Replay error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * List archived webhooks with pagination and filtering.
     *
     * @param status Filter by processing status (optional)
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Paginated webhook list
     */
    @GetMapping
    public ResponseEntity<Page<RawWebhookEvent>> listWebhooks(
            @RequestParam(required = false) WebhookProcessingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<RawWebhookEvent> webhooks = (status != null)
            ? webhookEventRepository.findByProcessingStatusOrderByReceivedAtDesc(status, pageable)
            : webhookEventRepository.findAllByOrderByReceivedAtDesc(pageable);

        return ResponseEntity.ok(webhooks);
    }

    /**
     * Get webhook details by ID.
     *
     * @param id Webhook event ID
     * @return Webhook details (headers, payload, error)
     */
    @GetMapping("/{id}")
    public ResponseEntity<RawWebhookEvent> getWebhook(@PathVariable Long id) {
        RawWebhookEvent webhook = webhookEventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + id));

        return ResponseEntity.ok(webhook);
    }

    /**
     * Get replayable webhooks (FAILED or PENDING).
     *
     * @return List of webhooks that can be replayed
     */
    @GetMapping("/replayable")
    public ResponseEntity<List<RawWebhookEvent>> getReplayableWebhooks() {
        List<RawWebhookEvent> replayable = webhookEventRepository.findReplayableWebhooks();
        return ResponseEntity.ok(replayable);
    }

    /**
     * Get webhook processing statistics.
     *
     * @return Counts by status
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("pending", webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PENDING));
        stats.put("processed", webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.PROCESSED));
        stats.put("failed", webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.FAILED));
        stats.put("rejected", webhookEventRepository.countByProcessingStatus(WebhookProcessingStatus.REJECTED));
        stats.put("total", webhookEventRepository.count());

        return ResponseEntity.ok(stats);
    }

    /**
     * Bulk replay all FAILED webhooks.
     *
     * @return Replay results
     */
    @PostMapping("/bulk-replay")
    public ResponseEntity<Map<String, Object>> bulkReplayFailed() {
        log.info("Admin requested bulk replay of FAILED webhooks");

        List<RawWebhookEvent> replayable = webhookEventRepository.findReplayableWebhooks();

        int successCount = 0;
        int failedCount = 0;

        for (RawWebhookEvent webhook : replayable) {
            try {
                ChainhookPayloadDto payload = objectMapper.readValue(
                    webhook.getPayloadJson(),
                    ChainhookPayloadDto.class
                );

                webhook.setProcessingStatus(WebhookProcessingStatus.PENDING);
                webhook.setProcessedAt(null);
                webhook.setErrorMessage(null);
                webhookEventRepository.save(webhook);

                ProcessChainhookPayloadUseCase.ProcessingResult result =
                    processChainhookPayloadUseCase.processPayload(payload);

                if (result.success) {
                    webhookArchivalService.markAsProcessed(webhook.getId());
                    successCount++;
                } else {
                    webhookArchivalService.markAsFailed(webhook.getId(),
                        new Exception("Bulk replay failed: " + result.getSummary()));
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("Error in bulk replay for webhook {}: {}", webhook.getId(), e.getMessage());
                webhookArchivalService.markAsFailed(webhook.getId(), e);
                failedCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total_replayable", replayable.size());
        response.put("success", successCount);
        response.put("failed", failedCount);

        return ResponseEntity.ok(response);
    }
}

package com.stacksmonitoring.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook notification service.
 * Sends alert notifications to configured webhook URLs via HTTP POST with circuit breaker and retry support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookNotificationService implements NotificationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DeadLetterQueueService dlqService;
    private final AlertNotificationRepository notificationRepository;

    /**
     * Send webhook notification with circuit breaker and retry support.
     *
     * Circuit breaker opens after 50% failure rate (10 calls window).
     * Retry: 3 attempts with exponential backoff (1s, 2s, 4s).
     * Fallback: Move to dead-letter queue on permanent failure.
     */
    @Override
    @CircuitBreaker(name = "webhookNotification", fallbackMethod = "sendFallback")
    @Retry(name = "webhookNotification")
    public void send(AlertNotification notification) throws NotificationException {
        String webhookUrl = notification.getAlertRule().getWebhookUrl();

        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            throw new NotificationException("No webhook URL configured for rule");
        }

        // Track delivery attempt
        Instant firstAttempt = notification.getDeliveryAttemptCount() == 0
            ? Instant.now()
            : notification.getLastDeliveryAttemptAt();

        notification.markAsDelivering();
        notificationRepository.save(notification);

        try {
            // Build webhook payload
            Map<String, Object> payload = buildWebhookPayload(notification);

            // Prepare HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // Send webhook
            ResponseEntity<String> response = restTemplate.exchange(
                webhookUrl,
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // Mark as successfully delivered
                notification.markAsSent();
                notificationRepository.save(notification);

                log.info("Sent webhook notification {} to {} (attempt {})",
                    notification.getId(),
                    webhookUrl,
                    notification.getDeliveryAttemptCount() + 1
                );
            } else {
                String errorMsg = String.format("Webhook returned non-2xx status: %d", response.getStatusCode().value());

                // Mark as retrying (will trigger retry via Resilience4j)
                notification.markAsRetrying(errorMsg);
                notificationRepository.save(notification);

                throw new NotificationException(errorMsg);
            }

        } catch (RestClientException e) {
            // Mark as retrying (will trigger retry via Resilience4j)
            notification.markAsRetrying(e.getMessage());
            notificationRepository.save(notification);

            log.error("Failed to send webhook notification {} to {} (attempt {}): {}",
                notification.getId(),
                webhookUrl,
                notification.getDeliveryAttemptCount(),
                e.getMessage(),
                e
            );
            throw new NotificationException("Failed to send webhook", e);
        }
    }

    /**
     * Fallback method called when circuit breaker opens or max retries exceeded.
     * Moves notification to dead-letter queue for manual intervention.
     */
    private void sendFallback(AlertNotification notification, Exception e) {
        log.error("Webhook notification {} failed permanently. Moving to DLQ. Error: {}",
            notification.getId(),
            e.getMessage()
        );

        try {
            // Determine failure reason
            String failureReason = e.getClass().getSimpleName().contains("CircuitBreaker")
                ? "CIRCUIT_OPEN"
                : "MAX_RETRIES_EXCEEDED";

            // Calculate first attempt time
            Instant firstAttempt = notification.getLastDeliveryAttemptAt() != null
                ? notification.getLastDeliveryAttemptAt()
                : Instant.now();

            // Add to dead-letter queue
            dlqService.addToDeadLetterQueue(
                notification,
                failureReason,
                e,
                notification.getDeliveryAttemptCount(),
                firstAttempt,
                Instant.now()
            );

            // Mark notification as dead-letter
            notification.markAsDeadLetter(e.getMessage());
            notificationRepository.save(notification);

        } catch (Exception dlqError) {
            log.error("Failed to add notification {} to DLQ: {}",
                notification.getId(),
                dlqError.getMessage(),
                dlqError
            );
        }
    }

    @Override
    public boolean supports(AlertNotification notification) {
        return notification.getChannel() == NotificationChannel.WEBHOOK;
    }

    /**
     * Build webhook payload in standard format.
     */
    private Map<String, Object> buildWebhookPayload(AlertNotification notification) {
        Map<String, Object> payload = new HashMap<>();

        // Alert metadata
        payload.put("notification_id", notification.getId());
        payload.put("triggered_at", notification.getTriggeredAt().toString());
        payload.put("alert_rule_id", notification.getAlertRule().getId());
        payload.put("alert_rule_name", notification.getAlertRule().getRuleName());
        payload.put("severity", notification.getAlertRule().getSeverity().toString());

        // Transaction data
        if (notification.getTransaction() != null) {
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("tx_id", notification.getTransaction().getTxId());
            transaction.put("sender", notification.getTransaction().getSender());
            transaction.put("success", notification.getTransaction().getSuccess());
            transaction.put("block_height", notification.getTransaction().getBlock().getBlockHeight());
            payload.put("transaction", transaction);
        }

        // Event data
        if (notification.getEvent() != null) {
            Map<String, Object> event = new HashMap<>();
            event.put("event_type", notification.getEvent().getEventType().toString());
            event.put("event_index", notification.getEvent().getEventIndex());
            event.put("contract_identifier", notification.getEvent().getContractIdentifier());
            event.put("description", notification.getEvent().getEventDescription());
            payload.put("event", event);
        }

        // Message
        payload.put("message", notification.getMessage());

        // Timestamp
        payload.put("timestamp", Instant.now().toString());

        return payload;
    }
}

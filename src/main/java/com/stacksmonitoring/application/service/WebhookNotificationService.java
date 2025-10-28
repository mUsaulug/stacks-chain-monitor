package com.stacksmonitoring.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
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
 * Sends alert notifications to configured webhook URLs via HTTP POST.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookNotificationService implements NotificationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void send(AlertNotification notification) throws NotificationException {
        String webhookUrl = notification.getAlertRule().getWebhookUrl();

        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            throw new NotificationException("No webhook URL configured for rule");
        }

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
                log.info("Sent webhook notification {} to {}", notification.getId(), webhookUrl);
            } else {
                throw new NotificationException(
                    String.format("Webhook returned non-2xx status: %d", response.getStatusCode().value())
                );
            }

        } catch (RestClientException e) {
            log.error("Failed to send webhook notification {} to {}: {}",
                notification.getId(), webhookUrl, e.getMessage(), e);
            throw new NotificationException("Failed to send webhook", e);
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

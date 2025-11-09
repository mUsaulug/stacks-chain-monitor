package com.stacksmonitoring.domain.model.webhook;

import com.stacksmonitoring.domain.valueobject.WebhookProcessingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Event Sourcing: Archives all incoming webhook payloads for debugging, auditing, and replay.
 *
 * Design Pattern: Event Sourcing (Martin Fowler)
 * - Store all incoming events (webhooks) in append-only log
 * - Enable replay for debugging and failure recovery
 * - Audit trail for compliance and troubleshooting
 *
 * Reference: PART A.2 - Raw Webhook Events Archive [P1]
 */
@Entity
@Table(name = "raw_webhook_events", indexes = {
    @Index(name = "idx_webhook_received_at", columnList = "received_at"),
    @Index(name = "idx_webhook_status", columnList = "processing_status"),
    @Index(name = "idx_webhook_status_time", columnList = "processing_status, received_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RawWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "raw_webhook_event_seq_gen")
    @SequenceGenerator(name = "raw_webhook_event_seq_gen", sequenceName = "raw_webhook_events_id_seq", allocationSize = 50)
    private Long id;

    /**
     * Timestamp when webhook was received by the server.
     */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /**
     * Timestamp when webhook processing completed (success or failure).
     */
    @Column(name = "processed_at")
    private Instant processedAt;

    /**
     * HTTP headers from the webhook request (includes X-Signature for HMAC validation).
     * Stored as JSONB for efficient querying.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> headersJson;

    /**
     * Full JSON payload from Chainhook webhook.
     * Contains blocks, transactions, and events.
     * Stored as JSONB for efficient querying (e.g., find all webhooks for specific block hash).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson; // Store as raw JSON string for exact replay

    /**
     * Processing status: PENDING, PROCESSED, FAILED, REJECTED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private WebhookProcessingStatus processingStatus;

    /**
     * Error message if processing failed or webhook was rejected.
     * Populated for FAILED and REJECTED statuses.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Full stack trace for FAILED webhooks (debugging).
     */
    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    /**
     * Source IP address of the webhook request.
     */
    @Column(name = "source_ip", length = 45) // IPv6 compatible
    private String sourceIp;

    /**
     * User-Agent header from webhook request.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Request ID for correlation with application logs.
     */
    @Column(name = "request_id", length = 100)
    private String requestId;

    /**
     * Mark webhook as successfully processed.
     */
    public void markAsProcessed() {
        this.processingStatus = WebhookProcessingStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    /**
     * Mark webhook as failed with error details.
     */
    public void markAsFailed(String errorMessage, String stackTrace) {
        this.processingStatus = WebhookProcessingStatus.FAILED;
        this.processedAt = Instant.now();
        this.errorMessage = errorMessage;
        this.errorStackTrace = stackTrace;
    }

    /**
     * Mark webhook as rejected (invalid signature or payload).
     */
    public void markAsRejected(String errorMessage) {
        this.processingStatus = WebhookProcessingStatus.REJECTED;
        this.processedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    /**
     * Check if this webhook can be replayed (FAILED status only).
     * REJECTED webhooks cannot be replayed (signature will fail again).
     */
    public boolean canBeReplayed() {
        return processingStatus == WebhookProcessingStatus.FAILED ||
               processingStatus == WebhookProcessingStatus.PENDING;
    }
}

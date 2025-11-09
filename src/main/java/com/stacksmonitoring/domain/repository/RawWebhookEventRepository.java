package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.webhook.RawWebhookEvent;
import com.stacksmonitoring.domain.valueobject.WebhookProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for raw webhook event archival and replay.
 * Supports event sourcing pattern for webhook debugging.
 *
 * Reference: PART A.2 - Raw Webhook Events Archive [P1]
 */
@Repository
public interface RawWebhookEventRepository extends JpaRepository<RawWebhookEvent, Long> {

    /**
     * Find all webhooks by status (e.g., find all FAILED webhooks for retry).
     */
    List<RawWebhookEvent> findByProcessingStatus(WebhookProcessingStatus status);

    /**
     * Find webhooks by status with pagination (admin dashboard).
     */
    Page<RawWebhookEvent> findByProcessingStatusOrderByReceivedAtDesc(
        WebhookProcessingStatus status,
        Pageable pageable
    );

    /**
     * Find recent webhooks (admin dashboard).
     */
    Page<RawWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    /**
     * Find webhooks received within a time range.
     */
    List<RawWebhookEvent> findByReceivedAtBetween(Instant start, Instant end);

    /**
     * Count webhooks by status (metrics).
     */
    long countByProcessingStatus(WebhookProcessingStatus status);

    /**
     * Find FAILED webhooks that can be replayed.
     * Excludes REJECTED (invalid signature) and PROCESSED (already done).
     */
    @Query("""
        SELECT w FROM RawWebhookEvent w
        WHERE w.processingStatus IN ('FAILED', 'PENDING')
        ORDER BY w.receivedAt DESC
    """)
    List<RawWebhookEvent> findReplayableWebhooks();

    /**
     * Find webhooks by request ID (correlation with application logs).
     */
    List<RawWebhookEvent> findByRequestId(String requestId);
}

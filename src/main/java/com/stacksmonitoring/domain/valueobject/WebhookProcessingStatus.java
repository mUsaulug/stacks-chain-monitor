package com.stacksmonitoring.domain.valueobject;

/**
 * Processing status for archived webhook events.
 * Used in event sourcing pattern for webhook debugging and replay.
 *
 * Reference: PART A.2 - Raw Webhook Events Archive [P1]
 */
public enum WebhookProcessingStatus {

    /**
     * Webhook received and archived, awaiting processing.
     * Initial state when webhook arrives.
     */
    PENDING,

    /**
     * Webhook successfully processed and persisted to database.
     * Blocks, transactions, and events are saved.
     */
    PROCESSED,

    /**
     * Processing failed due to application error (e.g., DB connection issue).
     * Can be retried via admin replay endpoint.
     * Error details stored in error_message field.
     */
    FAILED,

    /**
     * Webhook rejected due to invalid signature or malformed payload.
     * Cannot be retried (signature validation will fail again).
     * Error details stored in error_message field.
     */
    REJECTED
}

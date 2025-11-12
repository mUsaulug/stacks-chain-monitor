-- V10: Notification Dead Letter Queue (DLQ)
-- Purpose: Track permanently failed notifications for manual intervention
-- Reference: Master Prompt A.5 - Notification Resilience

-- ============================================================
-- NOTIFICATION_DEAD_LETTER_QUEUE: Failed notification tracking
-- ============================================================

CREATE TABLE notification_dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,

    -- Reference to original notification
    notification_id BIGINT NOT NULL,

    -- Notification details (denormalized for audit)
    alert_rule_id BIGINT NOT NULL,
    alert_rule_name VARCHAR(200) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(500) NOT NULL, -- email or webhook URL

    -- Failure tracking
    failure_reason VARCHAR(100) NOT NULL, -- CIRCUIT_OPEN, MAX_RETRIES_EXCEEDED, TIMEOUT, etc.
    error_message TEXT,
    error_stack_trace TEXT,

    -- Retry history
    attempt_count INTEGER NOT NULL DEFAULT 0,
    first_attempt_at TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ NOT NULL,

    -- DLQ metadata
    queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    processed_by VARCHAR(100), -- admin user or system job
    resolution_notes TEXT,

    -- Foreign key to alert_notification
    CONSTRAINT fk_dlq_notification FOREIGN KEY (notification_id)
        REFERENCES alert_notification(id) ON DELETE CASCADE
);

-- ============================================================
-- PERFORMANCE INDEXES
-- ============================================================

-- Fast lookup of pending DLQ items (for admin dashboard)
CREATE INDEX idx_dlq_pending
    ON notification_dead_letter_queue(queued_at DESC)
    WHERE processed = FALSE;

-- Fast lookup by notification ID (avoid duplicates)
CREATE INDEX idx_dlq_notification
    ON notification_dead_letter_queue(notification_id);

-- Fast lookup by failure reason (for analytics)
CREATE INDEX idx_dlq_failure_reason
    ON notification_dead_letter_queue(failure_reason);

-- Fast lookup by channel (for channel-specific investigations)
CREATE INDEX idx_dlq_channel
    ON notification_dead_letter_queue(channel);

-- ============================================================
-- ALERT_NOTIFICATION: Add delivery status tracking
-- ============================================================

-- Add columns to track delivery attempts and circuit breaker state
ALTER TABLE alert_notification
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'DELIVERING', 'DELIVERED', 'RETRYING', 'DEAD_LETTER')),
    ADD COLUMN delivery_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_delivery_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_delivery_error TEXT;

-- Performance index for retry queue processing
CREATE INDEX idx_notification_retry_queue
    ON alert_notification(last_delivery_attempt_at ASC)
    WHERE delivery_status = 'RETRYING';

-- Performance index for pending notifications
CREATE INDEX idx_notification_delivery_pending
    ON alert_notification(created_at ASC)
    WHERE delivery_status = 'PENDING';

-- ============================================================
-- COMMENTS (Documentation)
-- ============================================================

COMMENT ON TABLE notification_dead_letter_queue IS
    'Dead Letter Queue for permanently failed notifications. Tracks notifications that exceeded max retry attempts or encountered permanent failures. Enables manual investigation and re-processing by admins.';

COMMENT ON COLUMN notification_dead_letter_queue.failure_reason IS
    'Why notification failed permanently: CIRCUIT_OPEN (circuit breaker triggered), MAX_RETRIES_EXCEEDED (all retry attempts exhausted), TIMEOUT (delivery timeout), INVALID_RECIPIENT (email/webhook invalid), etc.';

COMMENT ON COLUMN notification_dead_letter_queue.attempt_count IS
    'Total number of delivery attempts before DLQ (typically 3-5 based on Resilience4j config)';

COMMENT ON COLUMN alert_notification.delivery_status IS
    'Notification delivery lifecycle: PENDING (not yet sent), DELIVERING (in progress), DELIVERED (success), RETRYING (transient failure, will retry), DEAD_LETTER (permanent failure, in DLQ)';

COMMENT ON COLUMN alert_notification.delivery_attempt_count IS
    'Number of delivery attempts made. Incremented on each retry. Max attempts defined in Resilience4j config.';

COMMENT ON INDEX idx_dlq_pending IS
    'Partial index: Fast queries for unprocessed DLQ items (admin dashboard). Only indexes rows where processed = FALSE.';

COMMENT ON INDEX idx_notification_retry_queue IS
    'Partial index: Fast processing of retry queue (ordered by last attempt time). Used by scheduled job to retry RETRYING notifications after backoff period.';

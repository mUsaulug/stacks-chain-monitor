-- V8: Raw Webhook Events Archive (Event Sourcing Pattern)
-- PART A.2: Raw Webhook Events Archive [P1]
-- Reference: Martin Fowler - Event Sourcing
-- Purpose: Store all incoming webhooks for debugging, auditing, and replay capability

-- ============================================================
-- RAW_WEBHOOK_EVENTS TABLE
-- ============================================================

CREATE TABLE raw_webhook_events (
    id BIGSERIAL PRIMARY KEY,

    -- Timing
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,

    -- Webhook content (JSONB for efficient querying)
    headers_json JSONB NOT NULL,
    payload_json JSONB NOT NULL,

    -- Processing status tracking
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (processing_status IN ('PENDING', 'PROCESSED', 'FAILED', 'REJECTED')),

    -- Error tracking (for FAILED/REJECTED webhooks)
    error_message TEXT,
    error_stack_trace TEXT,

    -- Metadata
    source_ip VARCHAR(45), -- IPv6 compatible (max 45 chars)
    user_agent VARCHAR(500),
    request_id VARCHAR(100) -- For correlation with application logs
);

-- ============================================================
-- INDEXES (Performance)
-- ============================================================

-- Query recent webhooks (admin dashboard)
CREATE INDEX idx_webhook_received_at
    ON raw_webhook_events(received_at DESC);

-- Filter by status (find failed webhooks for retry)
CREATE INDEX idx_webhook_status
    ON raw_webhook_events(processing_status);

-- Composite index for status + time (common query pattern)
CREATE INDEX idx_webhook_status_time
    ON raw_webhook_events(processing_status, received_at DESC);

-- JSONB index for querying webhook content
CREATE INDEX idx_webhook_payload_gin
    ON raw_webhook_events USING GIN (payload_json);

-- ============================================================
-- COMMENTS (Documentation)
-- ============================================================

COMMENT ON TABLE raw_webhook_events IS
    'Event Sourcing: Archives all incoming Chainhook webhooks for debugging, auditing, and replay. Enables investigation of processing failures and webhook re-delivery detection.';

COMMENT ON COLUMN raw_webhook_events.headers_json IS
    'HTTP headers from webhook request (includes X-Signature for HMAC validation)';

COMMENT ON COLUMN raw_webhook_events.payload_json IS
    'Full JSON payload from Chainhook (blocks, transactions, events)';

COMMENT ON COLUMN raw_webhook_events.processing_status IS
    'PENDING: not yet processed, PROCESSED: successfully processed, FAILED: processing error (can retry), REJECTED: invalid signature/payload (cannot retry)';

COMMENT ON INDEX idx_webhook_received_at IS
    'Performance: fast queries for recent webhooks (admin dashboard)';

COMMENT ON INDEX idx_webhook_status IS
    'Performance: filter by status (find FAILED webhooks for manual replay)';

COMMENT ON INDEX idx_webhook_payload_gin IS
    'Performance: JSONB GIN index enables efficient queries on payload content (e.g., find all webhooks for block hash)';

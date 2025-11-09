-- V7: Idempotent Constraints and Indexes for duplicate prevention
-- PART A.1: Idempotent Upsert + UNIQUE Constraints
-- Reference: stacks-monitoring-derin-analiz-ozet.txt

-- ============================================================
-- UNIQUE CONSTRAINTS (prevent duplicate insertions)
-- ============================================================

-- 1. BLOCKS: Unique on block_hash
--    Prevents duplicate blocks from same blockchain height
CREATE UNIQUE INDEX IF NOT EXISTS uk_block_hash
    ON stacks_block(block_hash);

-- 2. TRANSACTIONS: Unique on tx_id
--    Prevents duplicate transactions (same tx_id = same transaction)
CREATE UNIQUE INDEX IF NOT EXISTS uk_tx_id
    ON stacks_transaction(tx_id);

-- 3. EVENTS: Unique on (transaction_id, event_index, event_type)
--    Prevents duplicate events within same transaction
--    event_index already exists in schema (added in earlier phases)
CREATE UNIQUE INDEX IF NOT EXISTS uk_event_tx_idx_type
    ON transaction_event(transaction_id, event_index, event_type);

-- Note: alert_notification unique constraint already exists from V3
--       (alert_rule_id, transaction_id, event_id, channel)

-- ============================================================
-- PERFORMANCE INDEXES (fast lookups for common queries)
-- ============================================================

-- BLOCKS: Fast lookup by height (commonly used in reorg detection)
CREATE INDEX IF NOT EXISTS idx_block_height
    ON stacks_block(block_height);

-- TRANSACTIONS: Fast lookups for common query patterns
CREATE INDEX IF NOT EXISTS idx_tx_sender
    ON stacks_transaction(sender);

CREATE INDEX IF NOT EXISTS idx_tx_block
    ON stacks_transaction(block_id);

CREATE INDEX IF NOT EXISTS idx_tx_type
    ON stacks_transaction(tx_type);

CREATE INDEX IF NOT EXISTS idx_tx_success
    ON stacks_transaction(success);

-- EVENTS: Fast lookup by type (filtering events in alert matching)
CREATE INDEX IF NOT EXISTS idx_event_type
    ON transaction_event(event_type);

CREATE INDEX IF NOT EXISTS idx_event_transaction
    ON transaction_event(transaction_id);

-- NOTIFICATIONS: Partial index for PENDING notifications (queue processing)
CREATE INDEX IF NOT EXISTS idx_notification_pending
    ON alert_notification(status)
    WHERE status = 'PENDING';

-- ============================================================
-- COMMENTS (documentation for future maintainers)
-- ============================================================

COMMENT ON INDEX uk_block_hash IS 'Ensures idempotency: prevents duplicate blocks from same blockchain event';
COMMENT ON INDEX uk_tx_id IS 'Ensures idempotency: prevents duplicate transactions (webhook re-delivery)';
COMMENT ON INDEX uk_event_tx_idx_type IS 'Ensures idempotency: prevents duplicate events within same transaction';

COMMENT ON INDEX idx_block_height IS 'Performance: fast block lookups by height (reorg detection)';
COMMENT ON INDEX idx_tx_sender IS 'Performance: fast transaction lookups by sender address';
COMMENT ON INDEX idx_tx_block IS 'Performance: fast transaction lookups by block (cascade operations)';
COMMENT ON INDEX idx_notification_pending IS 'Performance: fast queue processing (partial index on PENDING only)';

-- V9: Blockchain Rollback - Notification Invalidation (P0)
-- Reference: stacks-monitoring-derin-analiz-ozet.txt - "Rollback tamamlama"
-- Purpose: Track and invalidate notifications when blockchain reorgs occur
--          Preserve audit trail while preventing re-dispatch of invalidated alerts

-- ============================================================
-- ALERT_NOTIFICATION: Add Invalidation Tracking
-- ============================================================

-- Add invalidation columns (NOT soft-delete - notifications are audit records)
ALTER TABLE alert_notification
    ADD COLUMN invalidated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN invalidated_at TIMESTAMPTZ,
    ADD COLUMN invalidation_reason VARCHAR(100);

-- ============================================================
-- PERFORMANCE INDEXES
-- ============================================================

-- Fast filtering of active (non-invalidated) notifications with time sorting
-- Used by: Admin dashboard, notification queries
CREATE INDEX idx_notification_active_partial
    ON alert_notification(created_at DESC)
    WHERE invalidated = FALSE;

-- Fast filtering of invalidated notifications (audit queries)
CREATE INDEX idx_notification_invalidated
    ON alert_notification(invalidated)
    WHERE invalidated = TRUE;

-- Fast lookup of notifications by transaction (for rollback cascade)
-- Critical for bulk invalidation: findByTransactionBlockId()
CREATE INDEX idx_notification_tx
    ON alert_notification(transaction_id);

-- Fast lookup of transactions by block (for rollback cascade)
-- Enables efficient: SELECT id FROM stacks_transaction WHERE block_id = ?
CREATE INDEX IF NOT EXISTS idx_tx_block
    ON stacks_transaction(block_id);

-- ============================================================
-- COMMENTS (Documentation)
-- ============================================================

COMMENT ON COLUMN alert_notification.invalidated IS
    'Marks notification as invalidated due to blockchain reorg. ' ||
    'Notification WAS sent legitimately but underlying blockchain data became invalid. ' ||
    'Different from soft-delete - preserves audit trail.';

COMMENT ON COLUMN alert_notification.invalidated_at IS
    'Timestamp when notification was invalidated (blockchain reorg detected). Uses TIMESTAMPTZ for timezone safety.';

COMMENT ON COLUMN alert_notification.invalidation_reason IS
    'Why notification was invalidated: BLOCKCHAIN_REORG (common), MANUAL (admin action), etc.';

COMMENT ON INDEX idx_notification_active_partial IS
    'Partial index: Fast queries for active notifications (invalidated = FALSE). ' ||
    'Used by dispatcher to skip invalidated notifications.';

COMMENT ON INDEX idx_notification_invalidated IS
    'Partial index: Fast audit queries for invalidated notifications. ' ||
    'Used by admin dashboard to investigate blockchain reorgs.';

COMMENT ON INDEX idx_notification_tx IS
    'Performance: Fast lookup of notifications by transaction for bulk invalidation during rollback.';

COMMENT ON INDEX idx_tx_block IS
    'Performance: Fast lookup of transactions by block for rollback cascade. ' ||
    'Critical for bulk invalidation query performance.';

-- ============================================================
-- BULK INVALIDATION SUPPORT (Performance Optimization)
-- ============================================================

-- Note: Bulk invalidation will be implemented in repository layer using JPQL:
--
-- @Modifying
-- @Query("""
--     UPDATE AlertNotification n
--        SET n.invalidated = true,
--            n.invalidatedAt = :invalidatedAt,
--            n.invalidationReason = :reason
--      WHERE n.transaction.block.id = :blockId
--        AND n.invalidated = false
-- """)
-- int bulkInvalidateByBlockId(
--     @Param("blockId") Long blockId,
--     @Param("invalidatedAt") Instant invalidatedAt,
--     @Param("reason") String reason
-- );
--
-- This single UPDATE statement is ~100x faster than iterating notifications.
-- For block with 1000 transactions × 5 notifications each = 5000 notifications:
--   - Individual saves: ~3-5 seconds
--   - Bulk UPDATE: ~50-100ms
--
-- Idempotency: WHERE invalidated = false ensures rollback can be called multiple times safely.

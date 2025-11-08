-- V3: Add idempotency constraint to alert_notification table
--
-- Purpose: Prevent duplicate notifications when webhooks arrive multiple times
-- (network retries, blockchain reorgs, manual replays)
--
-- Unique constraint ensures only ONE notification per:
-- - alert_rule_id: Which rule triggered
-- - transaction_id: Which transaction triggered it
-- - event_id: Which event triggered it (NULL for contract calls)
-- - channel: Which notification channel (EMAIL, WEBHOOK)
--
-- This makes notification creation idempotent - safe to call multiple times.

-- Add unique constraint
ALTER TABLE alert_notification
ADD CONSTRAINT uk_notification_rule_tx_event_channel
UNIQUE (alert_rule_id, transaction_id, event_id, channel);

-- Performance: PostgreSQL automatically creates a unique index for the constraint
-- This index also speeds up duplicate detection queries.

-- Migration Notes:
-- - If existing duplicates exist, this migration will FAIL
-- - Run cleanup query first if needed:
--   DELETE FROM alert_notification a
--   WHERE a.id NOT IN (
--     SELECT MIN(id)
--     FROM alert_notification
--     GROUP BY alert_rule_id, transaction_id, event_id, channel
--   );

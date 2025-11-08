-- V4: Add soft delete columns to transaction_event table
--
-- Purpose: Complete soft delete propagation for blockchain reorganizations
--
-- Problem: Before this migration, blockchain rollbacks marked blocks and
-- transactions as deleted, but events remained visible. This caused:
-- - Alert matching on "deleted" events
-- - Query services returning inconsistent data
-- - Reorg recovery issues
--
-- Solution: Add deleted flag and deletedAt timestamp to transaction_event
-- table to enable complete soft delete chain: block → transaction → event

-- Add soft delete columns to transaction_event
ALTER TABLE transaction_event
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE transaction_event
ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- Create index for performance (WHERE deleted = false is common filter)
CREATE INDEX idx_event_deleted ON transaction_event(deleted);

-- Migration Notes:
-- - All existing events will have deleted = false (default)
-- - @Where(clause = "deleted = false") in TransactionEvent.java filters queries
-- - This completes the soft delete chain for blockchain reorgs

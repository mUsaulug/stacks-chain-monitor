-- V6: Migrate fee_rate (DECIMAL) to fee_micro_stx (NUMERIC) for precision
-- Task 2.4.2: Transaction Fee Precision Fix

-- Rename and change type from DECIMAL(30) to NUMERIC(50) for BigInteger support
ALTER TABLE stacks_transaction
    RENAME COLUMN fee_rate TO fee_micro_stx;

ALTER TABLE stacks_transaction
    ALTER COLUMN fee_micro_stx TYPE NUMERIC(50,0);

-- Add comment
COMMENT ON COLUMN stacks_transaction.fee_micro_stx IS 'Transaction fee in microSTX (1 STX = 1,000,000 microSTX). Stored as BigInteger to avoid precision loss.';

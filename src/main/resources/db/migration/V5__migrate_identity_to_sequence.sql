-- V5: Migrate from IDENTITY to SEQUENCE for batch insert performance
--
-- Problem: IDENTITY strategy prevents JDBC batching
-- - Hibernate must get ID immediately after each INSERT
-- - Cannot batch multiple INSERTs into single DB roundtrip
-- - Performance: 10,000 inserts = 185s with IDENTITY vs 9s with SEQUENCE (95% slower)
--
-- Solution: Use SEQUENCE with allocationSize=50
-- - Hibernate pre-allocates 50 IDs at once
-- - Enables JDBC batching (hibernate.jdbc.batch_size=30)
-- - Massive performance improvement for bulk operations

-- Create sequences for all tables
-- Starting value = MAX(id) + 1 from existing data, or 1 if table is empty

CREATE SEQUENCE IF NOT EXISTS alert_rule_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS alert_notification_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS monitored_contract_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS stacks_block_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS stacks_transaction_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS transaction_event_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS contract_call_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS contract_deployment_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS revoked_token_seq START WITH 1 INCREMENT BY 50;

-- Sync sequence values with existing data (if any)
-- This prevents duplicate key errors when new records are inserted

DO $$
DECLARE
    max_id BIGINT;
BEGIN
    -- alert_rule
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM alert_rule;
    EXECUTE format('ALTER SEQUENCE alert_rule_seq RESTART WITH %s', max_id);

    -- alert_notification
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM alert_notification;
    EXECUTE format('ALTER SEQUENCE alert_notification_seq RESTART WITH %s', max_id);

    -- monitored_contract
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM monitored_contract;
    EXECUTE format('ALTER SEQUENCE monitored_contract_seq RESTART WITH %s', max_id);

    -- stacks_block
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM stacks_block;
    EXECUTE format('ALTER SEQUENCE stacks_block_seq RESTART WITH %s', max_id);

    -- stacks_transaction
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM stacks_transaction;
    EXECUTE format('ALTER SEQUENCE stacks_transaction_seq RESTART WITH %s', max_id);

    -- transaction_event
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM transaction_event;
    EXECUTE format('ALTER SEQUENCE transaction_event_seq RESTART WITH %s', max_id);

    -- contract_call
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM contract_call;
    EXECUTE format('ALTER SEQUENCE contract_call_seq RESTART WITH %s', max_id);

    -- contract_deployment
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM contract_deployment;
    EXECUTE format('ALTER SEQUENCE contract_deployment_seq RESTART WITH %s', max_id);

    -- users
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM users;
    EXECUTE format('ALTER SEQUENCE user_seq RESTART WITH %s', max_id);

    -- revoked_token
    SELECT COALESCE(MAX(id), 0) + 1 INTO max_id FROM revoked_token;
    EXECUTE format('ALTER SEQUENCE revoked_token_seq RESTART WITH %s', max_id);
END $$;

-- Migration Notes:
-- 1. Sequences created with INCREMENT BY 50 (matches @SequenceGenerator allocationSize)
-- 2. Existing IDs are preserved (no data modification needed)
-- 3. Java code changes required: @GeneratedValue(strategy = SEQUENCE, generator = "...")
-- 4. After migration, enable JDBC batching in application.yml:
--    spring.jpa.properties.hibernate.jdbc.batch_size=30
--    spring.jpa.properties.hibernate.order_inserts=true
--    spring.jpa.properties.hibernate.order_updates=true

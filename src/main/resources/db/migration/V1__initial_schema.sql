-- Stacks Blockchain Smart Contract Monitoring System - Initial Schema
-- Version: 1.0.0
-- Description: Creates all core tables, indexes, and constraints for the monitoring system

-- ============================================================================
-- BLOCKCHAIN ENTITIES
-- ============================================================================

-- Stacks Block table
CREATE TABLE stacks_block (
    id BIGSERIAL PRIMARY KEY,
    block_height BIGINT NOT NULL UNIQUE,
    block_hash VARCHAR(66) NOT NULL UNIQUE,
    index_block_hash VARCHAR(66) NOT NULL,
    parent_block_hash VARCHAR(66),
    timestamp TIMESTAMP NOT NULL,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    burn_block_height BIGINT,
    burn_block_hash VARCHAR(66),
    burn_block_timestamp TIMESTAMP,
    miner_address VARCHAR(50),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Stacks Block indexes
CREATE INDEX idx_block_height ON stacks_block(block_height);
CREATE INDEX idx_block_hash ON stacks_block(block_hash);
CREATE INDEX idx_block_timestamp ON stacks_block(timestamp);

-- Stacks Transaction table
CREATE TABLE stacks_transaction (
    id BIGSERIAL PRIMARY KEY,
    tx_id VARCHAR(66) NOT NULL UNIQUE,
    block_id BIGINT NOT NULL REFERENCES stacks_block(id),
    sender VARCHAR(50) NOT NULL,
    sponsor_address VARCHAR(50),
    tx_type VARCHAR(50) NOT NULL,
    success BOOLEAN NOT NULL,
    tx_index INTEGER NOT NULL,
    nonce BIGINT NOT NULL,
    fee_rate NUMERIC(30) NOT NULL,
    execution_cost_read_count BIGINT,
    execution_cost_read_length BIGINT,
    execution_cost_runtime BIGINT,
    execution_cost_write_count BIGINT,
    execution_cost_write_length BIGINT,
    raw_result TEXT,
    raw_tx TEXT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Stacks Transaction indexes
CREATE UNIQUE INDEX idx_tx_id ON stacks_transaction(tx_id);
CREATE INDEX idx_tx_sender ON stacks_transaction(sender);
CREATE INDEX idx_tx_block ON stacks_transaction(block_id);
CREATE INDEX idx_tx_success ON stacks_transaction(success);
CREATE INDEX idx_tx_type ON stacks_transaction(tx_type);

-- Contract Call table
CREATE TABLE contract_call (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES stacks_transaction(id),
    contract_identifier VARCHAR(150) NOT NULL,
    function_name VARCHAR(100) NOT NULL,
    function_args JSONB,
    function_args_raw TEXT
);

-- Contract Call indexes (CRITICAL for alert matching - 60% of rules use these)
CREATE INDEX idx_contract_call_identifier ON contract_call(contract_identifier);
CREATE INDEX idx_contract_call_function ON contract_call(function_name);
CREATE INDEX idx_contract_call_composite ON contract_call(contract_identifier, function_name);

-- Contract Deployment table
CREATE TABLE contract_deployment (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES stacks_transaction(id),
    contract_identifier VARCHAR(150) NOT NULL UNIQUE,
    contract_name VARCHAR(100) NOT NULL,
    source_code TEXT NOT NULL,
    abi JSONB,
    trait_implementations VARCHAR(500)
);

-- Contract Deployment indexes
CREATE UNIQUE INDEX idx_contract_deployment_identifier ON contract_deployment(contract_identifier);

-- Transaction Event base table (JOINED inheritance)
CREATE TABLE transaction_event (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES stacks_transaction(id),
    event_index INTEGER NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    contract_identifier VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transaction Event indexes (CRITICAL - 80% of alert rules query events!)
CREATE INDEX idx_event_type ON transaction_event(event_type);
CREATE INDEX idx_event_contract ON transaction_event(event_type, contract_identifier);
CREATE INDEX idx_event_transaction ON transaction_event(transaction_id);

-- FT Transfer Event table
CREATE TABLE ft_transfer_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    asset_identifier VARCHAR(150) NOT NULL,
    amount NUMERIC(30) NOT NULL,
    sender VARCHAR(50) NOT NULL,
    recipient VARCHAR(50) NOT NULL
);

CREATE INDEX idx_ft_asset ON ft_transfer_event(asset_identifier);
CREATE INDEX idx_ft_sender ON ft_transfer_event(sender);
CREATE INDEX idx_ft_recipient ON ft_transfer_event(recipient);
CREATE INDEX idx_ft_amount ON ft_transfer_event(amount);

-- FT Mint Event table
CREATE TABLE ft_mint_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    asset_identifier VARCHAR(150) NOT NULL,
    amount NUMERIC(30) NOT NULL,
    recipient VARCHAR(50) NOT NULL
);

CREATE INDEX idx_ft_mint_asset ON ft_mint_event(asset_identifier);
CREATE INDEX idx_ft_mint_recipient ON ft_mint_event(recipient);

-- FT Burn Event table
CREATE TABLE ft_burn_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    asset_identifier VARCHAR(150) NOT NULL,
    amount NUMERIC(30) NOT NULL,
    sender VARCHAR(50) NOT NULL
);

CREATE INDEX idx_ft_burn_asset ON ft_burn_event(asset_identifier);
CREATE INDEX idx_ft_burn_sender ON ft_burn_event(sender);

-- NFT Transfer Event table
CREATE TABLE nft_transfer_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    asset_class_identifier VARCHAR(150) NOT NULL,
    asset_identifier TEXT NOT NULL,
    sender VARCHAR(50) NOT NULL,
    recipient VARCHAR(50) NOT NULL,
    raw_value TEXT
);

CREATE INDEX idx_nft_asset_class ON nft_transfer_event(asset_class_identifier);
CREATE INDEX idx_nft_sender ON nft_transfer_event(sender);
CREATE INDEX idx_nft_recipient ON nft_transfer_event(recipient);

-- NFT Mint Event table
CREATE TABLE nft_mint_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    asset_class_identifier VARCHAR(150) NOT NULL,
    asset_identifier TEXT NOT NULL,
    recipient VARCHAR(50) NOT NULL,
    raw_value TEXT
);

CREATE INDEX idx_nft_mint_asset_class ON nft_mint_event(asset_class_identifier);
CREATE INDEX idx_nft_mint_recipient ON nft_mint_event(recipient);

-- NFT Burn Event table
CREATE TABLE nft_burn_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    asset_class_identifier VARCHAR(150) NOT NULL,
    asset_identifier TEXT NOT NULL,
    sender VARCHAR(50) NOT NULL,
    raw_value TEXT
);

CREATE INDEX idx_nft_burn_asset_class ON nft_burn_event(asset_class_identifier);
CREATE INDEX idx_nft_burn_sender ON nft_burn_event(sender);

-- STX Transfer Event table
CREATE TABLE stx_transfer_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    amount NUMERIC(30) NOT NULL,
    sender VARCHAR(50) NOT NULL,
    recipient VARCHAR(50) NOT NULL
);

CREATE INDEX idx_stx_sender ON stx_transfer_event(sender);
CREATE INDEX idx_stx_recipient ON stx_transfer_event(recipient);
CREATE INDEX idx_stx_amount ON stx_transfer_event(amount);

-- STX Mint Event table
CREATE TABLE stx_mint_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    amount NUMERIC(30) NOT NULL,
    recipient VARCHAR(50) NOT NULL
);

CREATE INDEX idx_stx_mint_recipient ON stx_mint_event(recipient);

-- STX Burn Event table
CREATE TABLE stx_burn_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    amount NUMERIC(30) NOT NULL,
    sender VARCHAR(50) NOT NULL
);

CREATE INDEX idx_stx_burn_sender ON stx_burn_event(sender);

-- STX Lock Event table
CREATE TABLE stx_lock_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    lock_amount NUMERIC(30) NOT NULL,
    unlock_height BIGINT NOT NULL,
    locked_address VARCHAR(50) NOT NULL
);

CREATE INDEX idx_stx_lock_sender ON stx_lock_event(locked_address);
CREATE INDEX idx_stx_lock_unlock_height ON stx_lock_event(unlock_height);

-- Smart Contract Event table
CREATE TABLE smart_contract_event (
    id BIGINT PRIMARY KEY REFERENCES transaction_event(id),
    topic VARCHAR(100),
    value_decoded JSONB,
    value_raw TEXT
);

CREATE INDEX idx_sc_event_topic ON smart_contract_event(topic);
CREATE INDEX idx_sc_event_contract ON smart_contract_event(id);

-- ============================================================================
-- USER & MONITORING ENTITIES
-- ============================================================================

-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_user_email ON users(email);

-- Monitored Contracts table
CREATE TABLE monitored_contract (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    contract_identifier VARCHAR(150) NOT NULL,
    contract_name VARCHAR(100),
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_contract UNIQUE (user_id, contract_identifier)
);

CREATE INDEX idx_monitored_contract_identifier ON monitored_contract(contract_identifier);
CREATE INDEX idx_monitored_contract_user ON monitored_contract(user_id);
CREATE INDEX idx_monitored_contract_active ON monitored_contract(is_active);

-- Alert Rules table (SINGLE_TABLE inheritance)
CREATE TABLE alert_rule (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    monitored_contract_id BIGINT REFERENCES monitored_contract(id),
    rule_name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    rule_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    cooldown_minutes INTEGER NOT NULL DEFAULT 60,
    last_triggered_at TIMESTAMP,
    notification_channels JSONB,
    notification_emails VARCHAR(500),
    webhook_url VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Discriminator fields for different rule types
    contract_identifier VARCHAR(150),
    function_name VARCHAR(100),
    amount_threshold NUMERIC(30),
    event_type VARCHAR(50),
    asset_identifier VARCHAR(150),
    event_key VARCHAR(100),
    data_conditions JSONB,
    watched_address VARCHAR(50),
    activity_types JSONB
);

CREATE INDEX idx_alert_rule_user ON alert_rule(user_id);
CREATE INDEX idx_alert_rule_contract ON alert_rule(monitored_contract_id);
CREATE INDEX idx_alert_rule_active ON alert_rule(is_active);
CREATE INDEX idx_alert_rule_type ON alert_rule(rule_type);

-- Alert Notifications table
CREATE TABLE alert_notification (
    id BIGSERIAL PRIMARY KEY,
    alert_rule_id BIGINT NOT NULL REFERENCES alert_rule(id),
    transaction_id BIGINT REFERENCES stacks_transaction(id),
    event_id BIGINT REFERENCES transaction_event(id),
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    triggered_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    message TEXT,
    failure_reason TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_rule ON alert_notification(alert_rule_id);
CREATE INDEX idx_notification_transaction ON alert_notification(transaction_id);
CREATE INDEX idx_notification_triggered_at ON alert_notification(triggered_at);
CREATE INDEX idx_notification_status ON alert_notification(status);

-- ============================================================================
-- COMMENTS & ASSUMPTIONS
-- ============================================================================

-- Assumption: PostgreSQL 14+ for JSONB support
-- Assumption: All timestamps stored in UTC
-- Assumption: Soft delete strategy for blockchain reorganization handling
-- Assumption: BCrypt password hashing with strength 12 (application layer)
-- Assumption: JWT tokens RS256 signed (application layer)
-- Assumption: HMAC-SHA256 for webhook signature validation (application layer)
-- Assumption: Maximum Chainhook payload size: 5MB
-- Assumption: Transaction event indexes optimized for alert matching (80% of queries)
-- Assumption: Contract call composite index for performance (60% of alert rules)
-- Assumption: Optimistic locking (@Version) on alert_rule for concurrent updates

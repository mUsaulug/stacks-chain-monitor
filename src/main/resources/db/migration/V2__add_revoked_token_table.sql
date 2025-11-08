-- Migration: Add revoked_token table for JWT denylist
-- Author: Claude Code Agent
-- Date: 2025-11-08
-- Reference: CLAUDE.md P0-1 (JWT RS256 Migration)

-- Create revoked_token table for JWT revocation denylist
CREATE TABLE revoked_token (
    id BIGSERIAL PRIMARY KEY,
    token_digest VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hash (hex-encoded, 64 chars)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,  -- Token expiration (from JWT exp claim)
    user_email VARCHAR(255),  -- User email for audit trail
    revocation_reason VARCHAR(100),  -- Reason for revocation (LOGOUT, SECURITY_BREACH, etc.)
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP  -- When token was revoked
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_revoked_token_digest ON revoked_token(token_digest);
CREATE INDEX idx_revoked_token_expiry ON revoked_token(expires_at);
CREATE INDEX idx_revoked_token_user ON revoked_token(user_email);

-- Comment the table
COMMENT ON TABLE revoked_token IS 'JWT token revocation denylist - stores SHA-256 digests of revoked tokens';
COMMENT ON COLUMN revoked_token.token_digest IS 'SHA-256 digest of revoked JWT token (hex-encoded, 64 chars)';
COMMENT ON COLUMN revoked_token.expires_at IS 'Token expiration timestamp - used for automatic cleanup';
COMMENT ON COLUMN revoked_token.user_email IS 'User email for audit trail and bulk revocation';
COMMENT ON COLUMN revoked_token.revocation_reason IS 'Reason for revocation (LOGOUT, SECURITY_BREACH, PERMISSION_CHANGE, etc.)';
COMMENT ON COLUMN revoked_token.revoked_at IS 'Timestamp when token was revoked';

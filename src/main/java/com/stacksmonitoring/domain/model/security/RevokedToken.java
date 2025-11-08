package com.stacksmonitoring.domain.model.security;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Entity for tracking revoked JWT tokens (denylist).
 * Stores SHA-256 digest of revoked tokens with expiration timestamp.
 *
 * Use Cases:
 * - User logout (revoke all tokens)
 * - Security breach (revoke compromised tokens)
 * - Permission change (force re-authentication)
 *
 * Storage Strategy:
 * - Store SHA-256 digest (not full token) for privacy
 * - Include expiration timestamp for automatic cleanup
 * - Index on token_digest for fast O(1) lookup
 *
 * Cleanup:
 * - @Scheduled job runs every 6 hours
 * - Deletes expired tokens (no longer needed)
 *
 * Reference: OWASP JWT Cheat Sheet - Token Revocation Patterns
 */
@Entity
@Table(name = "revoked_token", indexes = {
        @Index(name = "idx_revoked_token_digest", columnList = "token_digest", unique = true),
        @Index(name = "idx_revoked_token_expiry", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 digest of the revoked JWT token.
     * We store hash instead of full token for:
     * - Privacy (don't store sensitive data)
     * - Space efficiency (256 bits vs full JWT)
     * - Security (can't reconstruct original token)
     */
    @Column(name = "token_digest", nullable = false, unique = true, length = 64)
    private String tokenDigest; // Hex-encoded SHA-256 (64 chars)

    /**
     * Token expiration timestamp (from JWT exp claim).
     * Used for automatic cleanup - tokens are deleted after expiration.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Optional: User email for audit trail.
     */
    @Column(name = "user_email", length = 255)
    private String userEmail;

    /**
     * Optional: Revocation reason (LOGOUT, SECURITY_BREACH, etc.).
     */
    @Column(name = "revocation_reason", length = 100)
    private String revocationReason;

    /**
     * Timestamp when token was revoked.
     */
    @CreatedDate
    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RevokedToken)) return false;
        RevokedToken that = (RevokedToken) o;
        return tokenDigest != null && tokenDigest.equals(that.tokenDigest);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

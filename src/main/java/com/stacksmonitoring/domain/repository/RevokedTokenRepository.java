package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.security.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for managing revoked JWT tokens (denylist).
 *
 * Performance Optimization:
 * - Index on token_digest for O(1) lookup
 * - Batch cleanup of expired tokens
 * - @Cacheable for frequently checked tokens (optional)
 */
@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /**
     * Check if a token digest exists in the denylist.
     * This is the primary lookup method - must be fast O(1).
     *
     * @param tokenDigest SHA-256 digest of the token
     * @return true if token is revoked, false otherwise
     */
    boolean existsByTokenDigest(String tokenDigest);

    /**
     * Find revoked token by digest (for audit/debugging).
     *
     * @param tokenDigest SHA-256 digest of the token
     * @return Optional RevokedToken
     */
    Optional<RevokedToken> findByTokenDigest(String tokenDigest);

    /**
     * Delete all tokens that have expired.
     * Called by scheduled cleanup job every 6 hours.
     *
     * Performance: Uses index on expires_at for fast filtering.
     *
     * @param now Current timestamp
     * @return Number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM RevokedToken r WHERE r.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);

    /**
     * Delete all revoked tokens for a specific user (e.g., on account deletion).
     *
     * @param userEmail User email
     * @return Number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM RevokedToken r WHERE r.userEmail = :userEmail")
    int deleteByUserEmail(@Param("userEmail") String userEmail);

    /**
     * Count expired tokens (for monitoring/metrics).
     *
     * @param now Current timestamp
     * @return Number of expired tokens
     */
    @Query("SELECT COUNT(r) FROM RevokedToken r WHERE r.expiresAt < :now")
    long countExpiredTokens(@Param("now") Instant now);

    /**
     * Get total count of revoked tokens (for monitoring).
     *
     * @return Total count
     */
    long count();
}

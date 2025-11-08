package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.security.RevokedToken;
import com.stacksmonitoring.domain.repository.RevokedTokenRepository;
import com.stacksmonitoring.infrastructure.config.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/**
 * Service for revoking and validating JWT tokens.
 * Implements token denylist pattern with automatic cleanup.
 *
 * Security Considerations:
 * - Stores SHA-256 digest (not full token) for privacy
 * - O(1) lookup via indexed database query
 * - Automatic cleanup of expired tokens (every 6 hours)
 * - Supports user-level revocation (revoke all tokens)
 *
 * Use Cases:
 * 1. User logout - revoke current token
 * 2. Security breach - revoke all user tokens
 * 3. Permission change - force re-authentication
 * 4. Compromised token - immediate revocation
 *
 * Reference: OWASP JWT Cheat Sheet - Token Revocation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtTokenService jwtTokenService;

    /**
     * Revoke a JWT token by adding its digest to the denylist.
     *
     * @param token The JWT token to revoke
     * @param reason Revocation reason (LOGOUT, SECURITY_BREACH, etc.)
     */
    @Transactional
    public void revokeToken(String token, String reason) {
        try {
            // Extract expiration from token
            Date expiration = jwtTokenService.extractExpiration(token);
            String userEmail = jwtTokenService.extractUsername(token);

            // Calculate SHA-256 digest
            String tokenDigest = calculateTokenDigest(token);

            // Check if already revoked (idempotency)
            if (revokedTokenRepository.existsByTokenDigest(tokenDigest)) {
                log.debug("Token already revoked: {}", tokenDigest.substring(0, 16) + "...");
                return;
            }

            // Create revocation record
            RevokedToken revokedToken = new RevokedToken();
            revokedToken.setTokenDigest(tokenDigest);
            revokedToken.setExpiresAt(expiration.toInstant());
            revokedToken.setUserEmail(userEmail);
            revokedToken.setRevocationReason(reason);

            revokedTokenRepository.save(revokedToken);

            log.info("Token revoked for user: {} (reason: {})", userEmail, reason);
        } catch (Exception e) {
            log.error("Failed to revoke token: {}", e.getMessage(), e);
            throw new RuntimeException("Token revocation failed", e);
        }
    }

    /**
     * Check if a token has been revoked.
     * This method is called on every authenticated request.
     *
     * Performance: O(1) database lookup via indexed column.
     *
     * @param token The JWT token to check
     * @return true if token is revoked, false otherwise
     */
    public boolean isTokenRevoked(String token) {
        try {
            String tokenDigest = calculateTokenDigest(token);
            return revokedTokenRepository.existsByTokenDigest(tokenDigest);
        } catch (Exception e) {
            log.error("Failed to check token revocation: {}", e.getMessage());
            // Fail secure: treat as revoked on error
            return true;
        }
    }

    /**
     * Revoke all tokens for a specific user.
     * Use case: Security breach, permission change, account deletion.
     *
     * Note: This only revokes tokens currently in the denylist.
     * For complete user logout, also need to prevent new token generation.
     *
     * @param userEmail User email
     */
    @Transactional
    public void revokeAllUserTokens(String userEmail) {
        int deleted = revokedTokenRepository.deleteByUserEmail(userEmail);
        log.info("Revoked all tokens for user: {} ({} tokens)", userEmail, deleted);
    }

    /**
     * Calculate SHA-256 digest of a JWT token.
     * Returns hex-encoded digest (64 characters).
     *
     * @param token The JWT token
     * @return Hex-encoded SHA-256 digest
     */
    private String calculateTokenDigest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Scheduled cleanup job: Delete expired tokens from denylist.
     * Runs every 6 hours at minute 0.
     *
     * Why cleanup?
     * - Expired tokens no longer need revocation checking
     * - Reduces database size and improves query performance
     * - Prevents unbounded growth of revocation table
     *
     * Cron: 0 0 */6 * * * (every 6 hours at minute 0)
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting scheduled cleanup of expired revoked tokens");

        Instant now = Instant.now();
        long expiredCount = revokedTokenRepository.countExpiredTokens(now);

        if (expiredCount == 0) {
            log.info("No expired tokens to clean up");
            return;
        }

        int deleted = revokedTokenRepository.deleteExpiredTokens(now);
        log.info("Deleted {} expired tokens from revocation denylist", deleted);
    }

    /**
     * Get statistics about token revocation (for monitoring/metrics).
     */
    public RevocationStats getStats() {
        long totalRevoked = revokedTokenRepository.count();
        long expiredCount = revokedTokenRepository.countExpiredTokens(Instant.now());
        long activeRevoked = totalRevoked - expiredCount;

        return new RevocationStats(totalRevoked, activeRevoked, expiredCount);
    }

    /**
     * Statistics about revoked tokens.
     */
    public record RevocationStats(
            long totalRevoked,
            long activeRevoked,
            long expiredCount
    ) {}
}

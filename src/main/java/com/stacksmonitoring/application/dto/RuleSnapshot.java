package com.stacksmonitoring.application.dto;

import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable snapshot of AlertRule for caching.
 *
 * Problem: Caching mutable JPA entities causes:
 * - Thread safety issues (concurrent modifications)
 * - Stale data (lastTriggeredAt changes but cache not updated)
 * - Redis serialization failures (lazy-loaded proxies)
 *
 * Solution: Cache immutable DTOs instead of entities
 * - Thread-safe (all fields final, no setters)
 * - No lazy-loading issues (all data copied at creation)
 * - Serializable for Redis
 * - Lightweight (only fields needed for matching)
 *
 * Reference: CLAUDE.md P1-1 (Caching Mutable Entities)
 */
public record RuleSnapshot(
    Long id,
    AlertRuleType type,
    AlertSeverity severity,
    String contractIdentifier,  // For CONTRACT_CALL rules
    String functionName,        // For CONTRACT_CALL rules
    String assetIdentifier,     // For TOKEN_TRANSFER rules
    Duration cooldown,
    Instant lastTriggeredAt,
    Set<NotificationChannel> channels,
    String ruleName
) implements Serializable {

    /**
     * Create immutable snapshot from mutable entity.
     * Called when building cache index.
     */
    public static RuleSnapshot from(AlertRule rule) {
        return new RuleSnapshot(
            rule.getId(),
            rule.getRuleType(),
            rule.getSeverity(),
            extractContractIdentifier(rule),
            extractFunctionName(rule),
            extractAssetIdentifier(rule),
            Duration.ofMinutes(rule.getCooldownMinutes()),
            rule.getLastTriggeredAt(),
            Set.copyOf(rule.getNotificationChannels()),
            rule.getRuleName()
        );
    }

    /**
     * Check if rule is currently in cooldown.
     * Thread-safe (immutable Instant fields).
     */
    public boolean isInCooldown() {
        if (lastTriggeredAt == null) {
            return false;
        }
        Instant cooldownEnd = lastTriggeredAt.plus(cooldown);
        return Instant.now().isBefore(cooldownEnd);
    }

    /**
     * Calculate cooldown window start (for DB query).
     */
    public Instant getCooldownWindowStart() {
        return Instant.now().minus(cooldown);
    }

    /**
     * Test if this rule matches the given context.
     * Simplified: always returns true (actual matching happens in service layer)
     */
    public boolean matches(Object context) {
        // Matching logic moved to RuleMatchingService for better testability
        return true;
    }

    // Helper methods for extracting type-specific fields

    private static String extractContractIdentifier(AlertRule rule) {
        try {
            var method = rule.getClass().getMethod("getContractIdentifier");
            return (String) method.invoke(rule);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractFunctionName(AlertRule rule) {
        try {
            var method = rule.getClass().getMethod("getFunctionName");
            return (String) method.invoke(rule);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractAssetIdentifier(AlertRule rule) {
        try {
            var method = rule.getClass().getMethod("getAssetIdentifier");
            return (String) method.invoke(rule);
        } catch (Exception e) {
            return null;
        }
    }
}

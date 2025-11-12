package com.stacksmonitoring.application.dto;

import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.model.monitoring.TokenTransferAlertRule;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.EventType;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RuleSnapshot (P1-1: Immutable DTO caching).
 * Tests that RuleSnapshot correctly extracts fields from AlertRule entities
 * and provides thread-safe immutable snapshots.
 */
class RuleSnapshotTest {

    @Test
    void testFromContractCallRule_ShouldExtractAllFields() {
        // Given
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setRuleName("Test Contract Call");
        rule.setRuleType(AlertRuleType.CONTRACT_CALL);
        rule.setSeverity(AlertSeverity.HIGH);
        rule.setContractIdentifier("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1");
        rule.setFunctionName("swap-x-for-y");
        rule.setCooldownMinutes(30);
        rule.setLastTriggeredAt(Instant.parse("2025-01-01T10:00:00Z"));
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK));

        // When
        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // Then
        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.ruleName()).isEqualTo("Test Contract Call");
        assertThat(snapshot.type()).isEqualTo(AlertRuleType.CONTRACT_CALL);
        assertThat(snapshot.severity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(snapshot.contractIdentifier()).isEqualTo("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1");
        assertThat(snapshot.functionName()).isEqualTo("swap-x-for-y");
        assertThat(snapshot.cooldown()).isEqualTo(Duration.ofMinutes(30));
        assertThat(snapshot.lastTriggeredAt()).isEqualTo(Instant.parse("2025-01-01T10:00:00Z"));
        assertThat(snapshot.channels()).containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK);
        // Note: matcher() method removed - matching logic in service layer
    }

    @Test
    void testFromTokenTransferRule_ShouldExtractAssetIdentifier() {
        // Given
        TokenTransferAlertRule rule = new TokenTransferAlertRule();
        rule.setId(2L);
        rule.setRuleName("STX Transfer Alert");
        rule.setRuleType(AlertRuleType.TOKEN_TRANSFER);
        rule.setSeverity(AlertSeverity.MEDIUM);
        rule.setAssetIdentifier("STX");
        rule.setEventType(EventType.STX_TRANSFER);
        rule.setCooldownMinutes(15);
        rule.setNotificationChannels(List.of(NotificationChannel.WEBHOOK));

        // When
        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // Then
        assertThat(snapshot.assetIdentifier()).isEqualTo("STX");
        assertThat(snapshot.contractIdentifier()).isNull(); // Not a contract call rule
        assertThat(snapshot.functionName()).isNull();
    }

    @Test
    void testIsInCooldown_WithNoLastTriggered_ShouldReturnFalse() {
        // Given
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setCooldownMinutes(30);
        rule.setLastTriggeredAt(null); // Never triggered
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));

        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // When
        boolean inCooldown = snapshot.isInCooldown();

        // Then
        assertThat(inCooldown).isFalse();
    }

    @Test
    void testIsInCooldown_WithinCooldownPeriod_ShouldReturnTrue() {
        // Given
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setCooldownMinutes(30);
        rule.setLastTriggeredAt(Instant.now().minusSeconds(10 * 60)); // 10 minutes ago
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));

        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // When
        boolean inCooldown = snapshot.isInCooldown();

        // Then
        assertThat(inCooldown).isTrue();
    }

    @Test
    void testIsInCooldown_AfterCooldownPeriod_ShouldReturnFalse() {
        // Given
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setCooldownMinutes(30);
        rule.setLastTriggeredAt(Instant.now().minusSeconds(40 * 60)); // 40 minutes ago (past cooldown)
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));

        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // When
        boolean inCooldown = snapshot.isInCooldown();

        // Then
        assertThat(inCooldown).isFalse();
    }

    @Test
    void testGetCooldownWindowStart_ShouldCalculateCorrectly() {
        // Given
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setCooldownMinutes(60);
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));

        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // When
        Instant now = Instant.now();
        Instant windowStart = snapshot.getCooldownWindowStart();

        // Then - window start should be ~60 minutes before now
        long minutesAgo = Duration.between(windowStart, now).toMinutes();
        assertThat(minutesAgo).isBetween(59L, 61L); // Allow 1 minute tolerance
    }

    @Test
    void testImmutability_ChannelsAreCopied() {
        // Given
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));

        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // When - modify original rule's channels
        rule.setNotificationChannels(List.of(NotificationChannel.WEBHOOK, NotificationChannel.SLACK));

        // Then - snapshot should remain unchanged (immutable)
        assertThat(snapshot.channels()).containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    void testThreadSafety_MultipleThreadsReadingSameSnapshot() throws InterruptedException {
        // Given
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setContractIdentifier("SP123.contract");
        rule.setFunctionName("transfer");
        rule.setCooldownMinutes(30);
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));

        RuleSnapshot snapshot = RuleSnapshot.from(rule);

        // When - multiple threads access the same snapshot
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                // Read operations should be thread-safe
                assertThat(snapshot.id()).isEqualTo(1L);
                assertThat(snapshot.contractIdentifier()).isEqualTo("SP123.contract");
                assertThat(snapshot.functionName()).isEqualTo("transfer");
                assertThat(snapshot.channels()).contains(NotificationChannel.EMAIL);
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - no exceptions thrown, all reads successful
        assertThat(snapshot.contractIdentifier()).isEqualTo("SP123.contract");
    }
}

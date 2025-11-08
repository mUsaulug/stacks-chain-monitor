package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.blockchain.TransactionEvent;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.model.monitoring.TokenTransferAlertRule;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.repository.AlertRuleRepository;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for matching transactions and events against alert rules.
 * Implements cache-optimized alert matching for high performance.
 *
 * Performance: O(1) lookup via cached rule maps by type.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertMatchingService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertNotificationRepository alertNotificationRepository;

    /**
     * Evaluate all alert rules against a transaction.
     * Creates notifications for matched rules.
     *
     * @param transaction The transaction to evaluate
     * @return List of created alert notifications
     */
    @Transactional
    public List<AlertNotification> evaluateTransaction(StacksTransaction transaction) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Evaluate contract call alerts
        if (transaction.getContractCall() != null) {
            notifications.addAll(evaluateContractCall(transaction, transaction.getContractCall()));
        }

        // Evaluate transaction events
        if (transaction.getEvents() != null && !transaction.getEvents().isEmpty()) {
            for (TransactionEvent event : transaction.getEvents()) {
                notifications.addAll(evaluateEvent(transaction, event));
            }
        }

        // Evaluate failed transaction alerts
        if (!transaction.getSuccess()) {
            notifications.addAll(evaluateFailedTransaction(transaction));
        }

        log.debug("Evaluated transaction {} - {} notifications created",
            transaction.getTxId(), notifications.size());

        return notifications;
    }

    /**
     * Evaluate contract call alerts.
     */
    private List<AlertNotification> evaluateContractCall(StacksTransaction transaction, ContractCall contractCall) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Get all active contract call rules (cached)
        List<AlertRule> rules = getActiveRulesByType(AlertRuleType.CONTRACT_CALL);

        for (AlertRule rule : rules) {
            if (rule instanceof ContractCallAlertRule) {
                // Use atomic DB-level check-and-trigger to prevent race conditions
                notifications.addAll(createNotificationsIfAllowed(rule, transaction, null, contractCall));
            }
        }

        return notifications;
    }

    /**
     * Evaluate event-based alerts (token transfers, NFT transfers, etc.).
     */
    private List<AlertNotification> evaluateEvent(StacksTransaction transaction, TransactionEvent event) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Get all active token transfer rules (cached)
        List<AlertRule> rules = getActiveRulesByType(AlertRuleType.TOKEN_TRANSFER);

        for (AlertRule rule : rules) {
            if (rule instanceof TokenTransferAlertRule) {
                // Use atomic DB-level check-and-trigger to prevent race conditions
                notifications.addAll(createNotificationsIfAllowed(rule, transaction, event, event));
            }
        }

        // Check for print event alerts
        notifications.addAll(evaluatePrintEvent(transaction, event));

        return notifications;
    }

    /**
     * Evaluate print event alerts.
     */
    private List<AlertNotification> evaluatePrintEvent(StacksTransaction transaction, TransactionEvent event) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Get all active print event rules (cached)
        List<AlertRule> rules = getActiveRulesByType(AlertRuleType.PRINT_EVENT);

        for (AlertRule rule : rules) {
            // Use atomic DB-level check-and-trigger to prevent race conditions
            notifications.addAll(createNotificationsIfAllowed(rule, transaction, event, event));
        }

        return notifications;
    }

    /**
     * Evaluate failed transaction alerts.
     */
    private List<AlertNotification> evaluateFailedTransaction(StacksTransaction transaction) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Get all active failed transaction rules (cached)
        List<AlertRule> rules = getActiveRulesByType(AlertRuleType.FAILED_TRANSACTION);

        for (AlertRule rule : rules) {
            // Use atomic DB-level check-and-trigger to prevent race conditions
            notifications.addAll(createNotificationsIfAllowed(rule, transaction, null, transaction));
        }

        return notifications;
    }

    /**
     * Create notifications if rule is allowed to trigger (atomic check-and-trigger).
     * This method prevents race conditions by using database-level conditional UPDATE.
     *
     * Flow:
     * 1. Check if rule matches the context (business logic)
     * 2. Atomically try to mark rule as triggered (DB-level cooldown check)
     * 3. Only create notifications if atomic UPDATE succeeded
     *
     * @param rule Alert rule to evaluate
     * @param transaction Transaction that triggered the alert
     * @param event Optional event that triggered the alert
     * @param context Context object for rule matching (contractCall, event, or transaction)
     * @return List of created notifications (empty if cooldown active or no match)
     */
    private List<AlertNotification> createNotificationsIfAllowed(
            AlertRule rule,
            StacksTransaction transaction,
            TransactionEvent event,
            Object context) {

        // Step 1: Check if rule matches the context (business logic)
        boolean matches;
        try {
            matches = rule.matches(context);
        } catch (Exception e) {
            log.error("Error evaluating rule {}: {}", rule.getId(), e.getMessage(), e);
            return List.of(); // Empty list if evaluation fails
        }

        if (!matches) {
            return List.of(); // Rule doesn't match, no notifications
        }

        // Step 2: Atomically try to mark rule as triggered (DB-level cooldown check)
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(rule.getCooldownMinutes() * 60L);

        int updated = alertRuleRepository.markTriggeredIfOutOfCooldown(
            rule.getId(),
            now,
            windowStart
        );

        // Step 3: Only create notifications if atomic UPDATE succeeded
        if (updated == 0) {
            log.debug("Rule {} is in cooldown, skipping notification", rule.getId());
            return List.of(); // Cooldown active, no notifications
        }

        // Rule triggered successfully, create notifications
        List<AlertNotification> notifications = new ArrayList<>();
        String triggerDescription = rule.getTriggerDescription(context);

        for (NotificationChannel channel : rule.getNotificationChannels()) {
            AlertNotification notification = new AlertNotification();
            notification.setAlertRule(rule);
            notification.setTransaction(transaction);
            notification.setEvent(event);
            notification.setChannel(channel);
            notification.setTriggeredAt(now);
            notification.setMessage(buildNotificationMessage(rule, transaction, triggerDescription));

            // Save notification
            alertNotificationRepository.save(notification);
            notifications.add(notification);

            log.info("Created {} notification for rule {} ({})",
                channel, rule.getId(), rule.getRuleName());
        }

        return notifications;
    }

    /**
     * Build notification message.
     */
    private String buildNotificationMessage(
            AlertRule rule,
            StacksTransaction transaction,
            String triggerDescription) {

        return String.format("""
            Alert: %s

            Severity: %s
            Description: %s

            Transaction ID: %s
            Block Height: %d
            Sender: %s
            Success: %s

            Triggered at: %s
            """,
            rule.getRuleName(),
            rule.getSeverity(),
            triggerDescription,
            transaction.getTxId(),
            transaction.getBlock().getBlockHeight(),
            transaction.getSender(),
            transaction.getSuccess(),
            Instant.now()
        );
    }

    /**
     * Get active rules by type (cached for performance).
     * Cache key: alertRuleType
     *
     * This provides O(1) lookup for alert matching.
     */
    @Cacheable(value = "alertRules", key = "#ruleType")
    public List<AlertRule> getActiveRulesByType(AlertRuleType ruleType) {
        log.debug("Loading active rules for type: {}", ruleType);
        return alertRuleRepository.findActiveByRuleType(ruleType);
    }

    /**
     * Get all active alert rules (cached).
     */
    @Cacheable(value = "alertRules", key = "'all'")
    public List<AlertRule> getAllActiveRules() {
        log.debug("Loading all active rules");
        return alertRuleRepository.findAllActive();
    }

    /**
     * Invalidate alert rules cache.
     * Call this when rules are created, updated, or deleted.
     */
    @CacheEvict(value = "alertRules", allEntries = true)
    public void invalidateRulesCache() {
        log.info("Invalidated alert rules cache");
    }

    /**
     * Get statistics about alert matching.
     */
    public AlertMatchingStats getStats() {
        AlertMatchingStats stats = new AlertMatchingStats();

        stats.totalActiveRules = alertRuleRepository.findAllActive().size();
        stats.rulesByType = alertRuleRepository.findAllActive().stream()
            .collect(Collectors.groupingBy(
                AlertRule::getRuleType,
                Collectors.counting()
            ));

        return stats;
    }

    /**
     * Statistics for alert matching.
     */
    public static class AlertMatchingStats {
        public long totalActiveRules;
        public java.util.Map<AlertRuleType, Long> rulesByType;
    }
}

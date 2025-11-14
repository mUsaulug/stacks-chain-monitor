package com.stacksmonitoring.application.service;

import com.stacksmonitoring.application.dto.RuleIndex;
import com.stacksmonitoring.application.dto.RuleSnapshot;
import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import com.stacksmonitoring.domain.model.blockchain.FTTransferEvent;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.blockchain.TransactionEvent;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.repository.AlertRuleRepository;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for matching transactions and events against alert rules.
 * Implements O(1) alert matching using immutable multi-level index.
 *
 * Performance Improvements (P1-1 + P1-3):
 * - Immutable DTO caching (thread-safe, no staleness)
 * - Multi-level index (contract + function + asset)
 * - O(1) candidate lookup (vs O(k) full scan)
 *
 * Observability (P1 - Micrometer):
 * - alert.matching.duration: Timer for transaction evaluation performance
 *
 * Reference: CLAUDE.md P1-1, P1-3, P2-5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertMatchingService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertNotificationRepository alertNotificationRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Evaluate all alert rules against a transaction.
     * Creates notifications for matched rules.
     *
     * Metrics: Tracks alert.matching.duration timer for performance monitoring.
     *
     * @param transaction The transaction to evaluate
     * @return List of created alert notifications
     */
    @Transactional
    public List<AlertNotification> evaluateTransaction(StacksTransaction transaction) {
        // P1 Metric: Track alert matching duration for performance monitoring
        return Timer.builder("alert.matching.duration")
                .description("Time taken to evaluate all alert rules against a transaction")
                .tag("tx_type", transaction.getTxType().name())
                .tag("has_contract_call", String.valueOf(transaction.getContractCall() != null))
                .tag("event_count", String.valueOf(transaction.getEvents() != null ? transaction.getEvents().size() : 0))
                .register(meterRegistry)
                .record(() -> evaluateTransactionInternal(transaction));
    }

    /**
     * Internal implementation of transaction evaluation (wrapped by metrics timer).
     */
    private List<AlertNotification> evaluateTransactionInternal(StacksTransaction transaction) {
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
     * Evaluate contract call alerts using O(1) index lookup.
     * BEFORE (P1-3): O(k) loop through all CONTRACT_CALL rules
     * AFTER: O(1) index lookup by contract + function
     */
    private List<AlertNotification> evaluateContractCall(StacksTransaction transaction, ContractCall contractCall) {
        List<AlertNotification> notifications = new ArrayList<>();

        // O(1) candidate lookup using multi-level index
        RuleIndex index = getRuleIndex();
        List<RuleSnapshot> candidates = index.getCandidatesForContractCall(
            contractCall.getContractIdentifier(),
            contractCall.getFunctionName()
        );

        log.debug("Contract call {} → {} candidates (O(1) lookup)",
            contractCall.getContractIdentifier(), candidates.size());

        for (RuleSnapshot snapshot : candidates) {
            if (snapshot.matches(contractCall)) {
                // Use atomic DB-level check-and-trigger to prevent race conditions
                notifications.addAll(createNotificationsFromSnapshot(snapshot, transaction, null, contractCall));
            }
        }

        return notifications;
    }

    /**
     * Evaluate event-based alerts using O(1) index lookup.
     * BEFORE: O(k) loop through all TOKEN_TRANSFER rules
     * AFTER: O(1) index lookup by asset identifier
     */
    private List<AlertNotification> evaluateEvent(StacksTransaction transaction, TransactionEvent event) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Token transfer events - O(1) lookup by asset
        if (event instanceof FTTransferEvent ftEvent) {
            RuleIndex index = getRuleIndex();
            List<RuleSnapshot> candidates = index.getCandidatesForTokenTransfer(
                ftEvent.getAssetIdentifier()
            );

            log.debug("Token transfer {} → {} candidates (O(1) lookup)",
                ftEvent.getAssetIdentifier(), candidates.size());

            for (RuleSnapshot snapshot : candidates) {
                if (snapshot.matches(event)) {
                    notifications.addAll(createNotificationsFromSnapshot(snapshot, transaction, event, event));
                }
            }
        }

        // Check for print event alerts (fallback to type-based for now)
        notifications.addAll(evaluatePrintEvent(transaction, event));

        return notifications;
    }

    /**
     * Evaluate print event alerts (type-based, no specialized index yet).
     */
    private List<AlertNotification> evaluatePrintEvent(StacksTransaction transaction, TransactionEvent event) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Fallback to type-based lookup (could be optimized with contract-based index)
        RuleIndex index = getRuleIndex();
        List<RuleSnapshot> candidates = index.getByType(AlertRuleType.PRINT_EVENT);

        for (RuleSnapshot snapshot : candidates) {
            if (snapshot.matches(event)) {
                notifications.addAll(createNotificationsFromSnapshot(snapshot, transaction, event, event));
            }
        }

        return notifications;
    }

    /**
     * Evaluate failed transaction alerts (type-based).
     */
    private List<AlertNotification> evaluateFailedTransaction(StacksTransaction transaction) {
        List<AlertNotification> notifications = new ArrayList<>();

        // Type-based lookup (no specialized index needed - rare event)
        RuleIndex index = getRuleIndex();
        List<RuleSnapshot> candidates = index.getByType(AlertRuleType.FAILED_TRANSACTION);

        for (RuleSnapshot snapshot : candidates) {
            if (snapshot.matches(transaction)) {
                notifications.addAll(createNotificationsFromSnapshot(snapshot, transaction, null, transaction));
            }
        }

        return notifications;
    }

    /**
     * Create notifications from immutable snapshot (atomic check-and-trigger).
     * Works with RuleSnapshot instead of mutable entity.
     *
     * Flow:
     * 1. Rule already matched (called from index lookup)
     * 2. Atomically try to mark rule as triggered (DB-level cooldown check)
     * 3. Only create notifications if atomic UPDATE succeeded
     *
     * @param snapshot Immutable rule snapshot
     * @param transaction Transaction that triggered the alert
     * @param event Optional event that triggered the alert
     * @param context Context object for notification message
     * @return List of created notifications (empty if cooldown active)
     */
    private List<AlertNotification> createNotificationsFromSnapshot(
            RuleSnapshot snapshot,
            StacksTransaction transaction,
            TransactionEvent event,
            Object context) {

        // Step 1: Atomically try to mark rule as triggered (DB-level cooldown check)
        Instant now = Instant.now();
        Instant windowStart = snapshot.getCooldownWindowStart();

        int updated = alertRuleRepository.markTriggeredIfOutOfCooldown(
            snapshot.id(),
            now,
            windowStart
        );

        // Step 2: Only create notifications if atomic UPDATE succeeded
        if (updated == 0) {
            log.debug("Rule {} is in cooldown, skipping notification", snapshot.id());
            return List.of(); // Cooldown active, no notifications
        }

        // Step 3: Rule triggered successfully, create notifications
        List<AlertNotification> notifications = new ArrayList<>();

        // Load full entity for notification (need @ManyToOne relationships)
        AlertRule rule = alertRuleRepository.findById(snapshot.id()).orElse(null);
        if (rule == null) {
            log.warn("Rule {} not found (deleted?), skipping notification", snapshot.id());
            return List.of();
        }

        String triggerDescription = rule.getTriggerDescription(context);

        for (NotificationChannel channel : snapshot.channels()) {
            AlertNotification notification = new AlertNotification();
            notification.setAlertRule(rule);
            notification.setTransaction(transaction);
            notification.setEvent(event);
            notification.setChannel(channel);
            notification.setTriggeredAt(now);
            notification.setMessage(buildNotificationMessage(rule, transaction, triggerDescription));

            // Save notification with idempotency (unique constraint prevents duplicates)
            try {
                alertNotificationRepository.save(notification);
                notifications.add(notification);

                log.info("Created {} notification for rule {} ({})",
                    channel, snapshot.id(), snapshot.ruleName());

            } catch (DataIntegrityViolationException e) {
                // Duplicate notification detected (webhook arrived multiple times)
                log.debug("Duplicate notification detected for rule {} (tx: {}, channel: {}), skipping",
                    snapshot.id(), transaction.getTxId(), channel);
            }
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
     * Get immutable rule index (cached for performance).
     *
     * BEFORE (P1-1): Cached mutable entities
     * - Thread safety issues
     * - Stale data (lastTriggeredAt changes)
     * - Redis serialization failures
     *
     * AFTER: Cached immutable DTOs
     * - Thread-safe (all fields final)
     * - No staleness (index rebuilt on invalidation)
     * - Serializable for Redis
     *
     * Cache Key: "ruleIndex" (single index for all rules)
     * TTL: 10 minutes (configured in application.yml)
     */
    @Cacheable(value = "alertRules", key = "'ruleIndex'")
    public RuleIndex getRuleIndex() {
        log.info("Building immutable rule index from database");
        List<AlertRule> activeRules = alertRuleRepository.findAllActive();
        RuleIndex index = RuleIndex.from(activeRules);

        log.info("Built rule index: {} total rules, {} contract/function combos",
            index.getStats().totalRules(),
            index.getStats().contractFunctionCombos());

        return index;
    }

    /**
     * Invalidate rule index cache.
     * Call this when rules are created, updated, or deleted.
     */
    @CacheEvict(value = "alertRules", allEntries = true)
    public void invalidateRuleIndex() {
        log.info("Invalidated immutable rule index cache");
    }

    /**
     * Get statistics about alert matching.
     * Uses cached index for fast stats.
     */
    public AlertMatchingStats getStats() {
        RuleIndex index = getRuleIndex();
        RuleIndex.IndexStats indexStats = index.getStats();

        AlertMatchingStats stats = new AlertMatchingStats();
        stats.totalActiveRules = indexStats.totalRules();
        stats.rulesByType = index.byType().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (long) e.getValue().size()
            ));
        stats.contractsIndexed = indexStats.contractsIndexed();
        stats.assetsIndexed = indexStats.assetsIndexed();
        stats.indexCreatedAt = index.createdAt();

        return stats;
    }

    /**
     * Statistics for alert matching.
     */
    public static class AlertMatchingStats {
        public long totalActiveRules;
        public Map<AlertRuleType, Long> rulesByType;
        public int contractsIndexed;
        public int assetsIndexed;
        public Instant indexCreatedAt;
    }
}

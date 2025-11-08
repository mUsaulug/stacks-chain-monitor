package com.stacksmonitoring.application.usecase;

import com.stacksmonitoring.api.dto.webhook.BlockEventDto;
import com.stacksmonitoring.api.dto.webhook.ChainhookPayloadDto;
import com.stacksmonitoring.api.dto.webhook.TransactionDto;
import com.stacksmonitoring.application.event.NotificationsReadyEvent;
import com.stacksmonitoring.application.service.AlertMatchingService;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import com.stacksmonitoring.infrastructure.parser.ChainhookPayloadParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Use case for processing Chainhook webhook payloads.
 * Handles both apply (new blocks) and rollback (blockchain reorganization) events.
 * Implements batch processing for optimal database performance.
 * Evaluates alert rules and triggers notifications after persisting transactions.
 *
 * CRITICAL: Notification Dispatch Timing (P0-6)
 * - Notifications are published as events INSIDE transaction
 * - NotificationDispatcher listens with @TransactionalEventListener(AFTER_COMMIT)
 * - Emails/webhooks sent ONLY if database commit succeeds
 * - Zero phantom notifications on rollback
 *
 * Reference: CLAUDE.md P0-6 (AFTER_COMMIT Notification Dispatch)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessChainhookPayloadUseCase {

    private final ChainhookPayloadParser parser;
    private final StacksBlockRepository blockRepository;
    private final AlertMatchingService alertMatchingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Process a complete Chainhook webhook payload.
     * Handles both apply and rollback events in a single transaction.
     */
    @Transactional
    public ProcessingResult processPayload(ChainhookPayloadDto payload) {
        log.info("Processing Chainhook payload with {} apply and {} rollback events",
            payload.getApply() != null ? payload.getApply().size() : 0,
            payload.getRollback() != null ? payload.getRollback().size() : 0);

        ProcessingResult result = new ProcessingResult();

        try {
            // First, handle rollbacks (blockchain reorganization)
            if (payload.getRollback() != null && !payload.getRollback().isEmpty()) {
                result.rollbackCount = handleRollbacks(payload.getRollback());
                log.info("Handled {} rollback blocks", result.rollbackCount);
            }

            // Then, apply new blocks
            if (payload.getApply() != null && !payload.getApply().isEmpty()) {
                result.applyCount = handleApplies(payload.getApply());
                log.info("Applied {} new blocks", result.applyCount);
            }

            result.success = true;
            log.info("Successfully processed Chainhook payload: {} applied, {} rolled back",
                result.applyCount, result.rollbackCount);

        } catch (Exception e) {
            log.error("Error processing Chainhook payload", e);
            result.success = false;
            result.errorMessage = e.getMessage();
            throw new RuntimeException("Failed to process Chainhook payload", e);
        }

        return result;
    }

    /**
     * Handle rollback events (blockchain reorganization).
     * Marks affected blocks and transactions as deleted via soft delete.
     */
    private int handleRollbacks(List<BlockEventDto> rollbackEvents) {
        int count = 0;

        for (BlockEventDto blockEvent : rollbackEvents) {
            String blockHash = blockEvent.getBlockIdentifier().getHash();

            Optional<StacksBlock> existingBlock = blockRepository.findByBlockHash(blockHash);
            if (existingBlock.isPresent()) {
                StacksBlock block = existingBlock.get();
                block.markAsDeleted();

                // Cascade soft delete to all transactions and events in the block
                block.getTransactions().forEach(tx -> {
                    tx.markAsDeleted();

                    // CRITICAL: Propagate soft delete to all events (P1-6 fix)
                    // Without this, events remain visible even after rollback
                    if (tx.getEvents() != null && !tx.getEvents().isEmpty()) {
                        tx.getEvents().forEach(event -> event.markAsDeleted());
                    }
                });

                blockRepository.save(block);
                count++;
                log.debug("Marked block {} (height {}) and all transactions/events as deleted due to rollback",
                    blockHash, block.getBlockHeight());
            } else {
                log.warn("Rollback requested for non-existent block: {}", blockHash);
            }
        }

        return count;
    }

    /**
     * Handle apply events (new blocks).
     * Processes blocks and transactions in batches for optimal performance.
     * Evaluates alert rules after persisting transactions.
     */
    private int handleApplies(List<BlockEventDto> applyEvents) {
        int count = 0;
        List<AlertNotification> allNotifications = new ArrayList<>();

        for (BlockEventDto blockEvent : applyEvents) {
            // Check if block already exists (idempotency)
            String blockHash = blockEvent.getBlockIdentifier().getHash();
            Optional<StacksBlock> existingBlock = blockRepository.findByBlockHash(blockHash);

            if (existingBlock.isPresent()) {
                StacksBlock block = existingBlock.get();
                if (block.getDeleted()) {
                    // Block was previously rolled back, restore it
                    block.setDeleted(false);
                    block.setDeletedAt(null);
                    blockRepository.save(block);
                    log.info("Restored previously deleted block {} (height {})",
                        blockHash, block.getBlockHeight());
                } else {
                    log.debug("Block {} already exists, skipping", blockHash);
                }
                continue;
            }

            // Parse and persist new block
            StacksBlock block = parser.parseBlock(blockEvent);

            // Parse and add all transactions to the block
            if (blockEvent.getTransactions() != null) {
                for (TransactionDto txDto : blockEvent.getTransactions()) {
                    try {
                        StacksTransaction transaction = parser.parseTransaction(txDto, block);
                        block.addTransaction(transaction);
                    } catch (Exception e) {
                        log.error("Error parsing transaction {}, skipping",
                            txDto.getTransactionIdentifier().getHash(), e);
                        // Continue processing other transactions
                    }
                }
            }

            // Save block with all transactions and events (cascaded)
            blockRepository.save(block);
            count++;

            log.info("Persisted block {} (height {}) with {} transactions",
                blockHash, block.getBlockHeight(), block.getTransactions().size());

            // Evaluate alert rules for each transaction
            for (StacksTransaction transaction : block.getTransactions()) {
                try {
                    List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);
                    allNotifications.addAll(notifications);

                    if (!notifications.isEmpty()) {
                        log.info("Transaction {} triggered {} alerts",
                            transaction.getTxId(), notifications.size());
                    }
                } catch (Exception e) {
                    log.error("Error evaluating alerts for transaction {}: {}",
                        transaction.getTxId(), e.getMessage(), e);
                    // Continue processing other transactions
                }
            }
        }

        // Publish notifications as event (dispatched AFTER commit succeeds)
        if (!allNotifications.isEmpty()) {
            log.info("Publishing event for {} notifications from {} blocks (dispatch after commit)",
                    allNotifications.size(), count);
            eventPublisher.publishEvent(new NotificationsReadyEvent(this, allNotifications));
        }

        return count;
    }

    /**
     * Process a single block event (for granular processing).
     */
    @Transactional
    public void processBlockEvent(BlockEventDto blockEvent, boolean isRollback) {
        if (isRollback) {
            handleRollbacks(List.of(blockEvent));
        } else {
            handleApplies(List.of(blockEvent));
        }
    }

    /**
     * Result of processing a Chainhook payload.
     */
    public static class ProcessingResult {
        public boolean success;
        public int applyCount;
        public int rollbackCount;
        public String errorMessage;
        public Instant processedAt = Instant.now();

        public String getSummary() {
            if (success) {
                return String.format("Successfully processed %d applies and %d rollbacks",
                    applyCount, rollbackCount);
            } else {
                return String.format("Failed to process payload: %s", errorMessage);
            }
        }
    }
}

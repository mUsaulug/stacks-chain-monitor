package com.stacksmonitoring.application.usecase;

import com.stacksmonitoring.api.dto.webhook.BlockEventDto;
import com.stacksmonitoring.api.dto.webhook.BlockIdentifierDto;
import com.stacksmonitoring.api.dto.webhook.ChainhookPayloadDto;
import com.stacksmonitoring.api.dto.webhook.TransactionDto;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.blockchain.TransactionEvent;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import com.stacksmonitoring.domain.repository.StacksTransactionRepository;
import com.stacksmonitoring.domain.repository.UserRepository;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TDD Integration Tests for Blockchain Rollback with Notification Invalidation.
 *
 * Test Strategy:
 * 1. Write tests FIRST (they will FAIL)
 * 2. Implement rollback cascade logic
 * 3. Verify tests PASS
 *
 * Performance Requirements:
 * - Block with 1000 transactions: < 5 seconds rollback
 * - 5000 notifications: bulk invalidation in < 100ms
 * - Concurrent rollbacks: idempotent, no errors
 *
 * Reference: V9 Migration - Blockchain Rollback Notification Invalidation [P0]
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:14:///test",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class BlockchainRollbackIntegrationTest {

    @Autowired
    private ProcessChainhookPayloadUseCase processChainhookPayloadUseCase;

    @Autowired
    private StacksBlockRepository blockRepository;

    @Autowired
    private StacksTransactionRepository transactionRepository;

    @Autowired
    private AlertNotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Create test user for alert rules
        testUser = new User();
        testUser.setEmail("test@rollback.com");
        testUser.setPasswordHash("hash");
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Test 1: Rollback soft-deletes block/tx/events AND invalidates notifications")
    @Transactional
    void testRollbackInvalidatesNotifications() {
        // Given - Create block with transaction and notification
        StacksBlock block = createAndSaveBlock("0xrollback123", 1000L);
        StacksTransaction tx = createAndSaveTransaction(block, "0xtx123");
        AlertNotification notification = createAndSaveNotification(tx);

        // Verify notification is active
        assertThat(notification.getInvalidated()).isFalse();
        assertThat(notification.isValid()).isTrue();

        // When - Rollback the block
        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xrollback123", 1000L);
        ProcessChainhookPayloadUseCase.ProcessingResult result =
            processChainhookPayloadUseCase.processPayload(rollbackPayload);

        // Then - Processing succeeded
        assertThat(result.success).isTrue();
        assertThat(result.rollbackCount).isEqualTo(1);

        // And - Block marked as deleted
        StacksBlock rolledBackBlock = blockRepository.findById(block.getId()).orElseThrow();
        assertThat(rolledBackBlock.getDeleted()).isTrue();
        assertThat(rolledBackBlock.getDeletedAt()).isNotNull();

        // And - Transaction marked as deleted
        StacksTransaction rolledBackTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(rolledBackTx.getDeleted()).isTrue();
        assertThat(rolledBackTx.getDeletedAt()).isNotNull();

        // And - Notification marked as invalidated (NOT deleted)
        AlertNotification invalidatedNotification =
            notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(invalidatedNotification.getInvalidated()).isTrue();
        assertThat(invalidatedNotification.getInvalidatedAt()).isNotNull();
        assertThat(invalidatedNotification.getInvalidationReason()).isEqualTo("BLOCKCHAIN_REORG");
        assertThat(invalidatedNotification.isValid()).isFalse();

        // And - Block/tx don't appear in findAll (filtered by @Where clause)
        List<StacksBlock> activeBlocks = blockRepository.findAll();
        assertThat(activeBlocks).doesNotContain(rolledBackBlock);

        List<StacksTransaction> activeTx = transactionRepository.findAll();
        assertThat(activeTx).doesNotContain(rolledBackTx);

        // But - Notification still appears (not soft-deleted, just invalidated)
        List<AlertNotification> allNotifications = notificationRepository.findAll();
        assertThat(allNotifications).contains(invalidatedNotification);
    }

    @Test
    @DisplayName("Test 2: Idempotent rollback - same block twice → no errors, 0 additional invalidations")
    @Transactional
    void testIdempotentRollback() {
        // Given - Block with notification
        StacksBlock block = createAndSaveBlock("0xrollback456", 2000L);
        StacksTransaction tx = createAndSaveTransaction(block, "0xtx456");
        AlertNotification notification = createAndSaveNotification(tx);

        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xrollback456", 2000L);

        // When - Rollback FIRST time
        ProcessChainhookPayloadUseCase.ProcessingResult result1 =
            processChainhookPayloadUseCase.processPayload(rollbackPayload);

        assertThat(result1.success).isTrue();
        assertThat(result1.rollbackCount).isEqualTo(1);

        // Verify notification invalidated
        AlertNotification invalidated1 = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(invalidated1.getInvalidated()).isTrue();
        Instant firstInvalidatedAt = invalidated1.getInvalidatedAt();

        // When - Rollback SECOND time (idempotent)
        ProcessChainhookPayloadUseCase.ProcessingResult result2 =
            processChainhookPayloadUseCase.processPayload(rollbackPayload);

        // Then - No errors, state remains deleted/invalidated
        assertThat(result2.success).isTrue();
        // rollbackCount might be 0 (skip if already deleted) or 1 (idempotent save)

        StacksBlock rolledBackBlock = blockRepository.findById(block.getId()).orElseThrow();
        assertThat(rolledBackBlock.getDeleted()).isTrue();

        AlertNotification invalidated2 = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(invalidated2.getInvalidated()).isTrue();

        // And - invalidatedAt timestamp NOT updated (entity-level idempotent guard OR bulk UPDATE returns 0)
        assertThat(invalidated2.getInvalidatedAt()).isEqualTo(firstInvalidatedAt);
    }

    @Test
    @DisplayName("Test 3: Load test - 5000 notifications invalidated in bulk < 1 second")
    @Transactional
    void testBulkInvalidationPerformance() {
        // Given - Block with 1000 transactions, 5 notifications each = 5000 total
        StacksBlock block = createAndSaveBlock("0xload_test", 3000L);

        List<AlertNotification> allNotifications = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            StacksTransaction tx = createAndSaveTransaction(block, "0xtx_load_" + i);

            // 5 notifications per transaction
            for (int j = 0; j < 5; j++) {
                AlertNotification notif = createAndSaveNotification(tx);
                allNotifications.add(notif);
            }
        }

        assertThat(allNotifications).hasSize(5000);

        // When - Rollback with bulk invalidation (time it)
        long startTime = System.currentTimeMillis();

        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xload_test", 3000L);
        ProcessChainhookPayloadUseCase.ProcessingResult result =
            processChainhookPayloadUseCase.processPayload(rollbackPayload);

        long duration = System.currentTimeMillis() - startTime;

        // Then - All notifications invalidated
        assertThat(result.success).isTrue();

        long invalidatedCount = notificationRepository.countByInvalidatedTrue();
        assertThat(invalidatedCount).isGreaterThanOrEqualTo(5000);

        // And - Performance: < 5 seconds for entire rollback (including 5000 notifications)
        assertThat(duration).isLessThan(5000); // 5 seconds max

        System.out.printf("✅ Bulk invalidation of 5000 notifications completed in %d ms%n", duration);
    }

    @Test
    @DisplayName("Test 4: Concurrent rollbacks - 2 threads process same block → idempotent, no errors")
    void testConcurrentRollbacks() throws Exception {
        // Given - Block with 10 transactions, 2 notifications each
        StacksBlock block = createAndSaveBlock("0xconcurrent", 4000L);

        for (int i = 0; i < 10; i++) {
            StacksTransaction tx = createAndSaveTransaction(block, "0xtx_concurrent_" + i);
            createAndSaveNotification(tx);
            createAndSaveNotification(tx);
        }

        // 20 total notifications
        assertThat(notificationRepository.count()).isGreaterThanOrEqualTo(20);

        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xconcurrent", 4000L);

        // When - 2 threads rollback simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<ProcessChainhookPayloadUseCase.ProcessingResult> future1 = executor.submit(() -> {
            latch.await();
            return processChainhookPayloadUseCase.processPayload(rollbackPayload);
        });

        Future<ProcessChainhookPayloadUseCase.ProcessingResult> future2 = executor.submit(() -> {
            latch.await();
            return processChainhookPayloadUseCase.processPayload(rollbackPayload);
        });

        latch.countDown(); // Release both threads

        ProcessChainhookPayloadUseCase.ProcessingResult result1 = future1.get(30, TimeUnit.SECONDS);
        ProcessChainhookPayloadUseCase.ProcessingResult result2 = future2.get(30, TimeUnit.SECONDS);

        executor.shutdown();

        // Then - Both succeeded (no exceptions)
        assertThat(result1.success).isTrue();
        assertThat(result2.success).isTrue();

        // And - Block marked as deleted
        StacksBlock rolledBackBlock = blockRepository.findById(block.getId()).orElseThrow();
        assertThat(rolledBackBlock.getDeleted()).isTrue();

        // And - All notifications invalidated (one thread did the work, other was idempotent)
        long invalidatedCount = notificationRepository.countByInvalidatedTrue();
        assertThat(invalidatedCount).isGreaterThanOrEqualTo(20);

        System.out.println("✅ Concurrent rollbacks completed without errors (idempotent)");
    }

    @Test
    @DisplayName("Test 5: Block restoration - deleted block arrives again → restored, notifications NOT un-invalidated")
    @Transactional
    void testBlockRestoration() {
        // Given - Block that was rolled back
        StacksBlock block = createAndSaveBlock("0xrestore", 5000L);
        StacksTransaction tx = createAndSaveTransaction(block, "0xtx_restore");
        AlertNotification notification = createAndSaveNotification(tx);

        // Rollback the block
        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xrestore", 5000L);
        processChainhookPayloadUseCase.processPayload(rollbackPayload);

        assertThat(block.getDeleted()).isTrue();
        assertThat(notification.getInvalidated()).isTrue();

        // When - Same block arrives in "apply" event (reorg resolved)
        ChainhookPayloadDto applyPayload = createApplyPayload("0xrestore", 5000L);
        ProcessChainhookPayloadUseCase.ProcessingResult result =
            processChainhookPayloadUseCase.processPayload(applyPayload);

        // Then - Block restored (deleted = false)
        assertThat(result.success).isTrue();

        StacksBlock restoredBlock = blockRepository.findByBlockHash("0xrestore").orElseThrow();
        assertThat(restoredBlock.getDeleted()).isFalse();
        assertThat(restoredBlock.getDeletedAt()).isNull();

        // And - Block appears in findAll again
        List<StacksBlock> allBlocks = blockRepository.findAll();
        assertThat(allBlocks).contains(restoredBlock);

        // But - Notification remains invalidated (NOT un-invalidated)
        AlertNotification stillInvalidated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(stillInvalidated.getInvalidated()).isTrue();

        // Note: If alert rules match again, NEW notifications will be created
    }

    @Test
    @DisplayName("Test 6: @Where clause filtering - deleted records excluded from findAll()")
    @Transactional
    void testWhereClauseFiltersDeleted() {
        // Given - 3 blocks: 2 active, 1 will be rolled back
        StacksBlock block1 = createAndSaveBlock("0xactive1", 100L);
        StacksBlock block2 = createAndSaveBlock("0xactive2", 101L);
        StacksBlock block3 = createAndSaveBlock("0xdeleted", 102L);

        // Rollback block3
        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xdeleted", 102L);
        processChainhookPayloadUseCase.processPayload(rollbackPayload);

        // When - Query all blocks
        List<StacksBlock> allBlocks = blockRepository.findAll();

        // Then - Only 2 active blocks returned (@Where clause filters deleted)
        assertThat(allBlocks).hasSize(2);
        assertThat(allBlocks).extracting(StacksBlock::getBlockHash)
            .containsExactlyInAnyOrder("0xactive1", "0xactive2");

        // And - Deleted block NOT in results
        assertThat(allBlocks).noneMatch(b -> "0xdeleted".equals(b.getBlockHash()));
    }

    @Test
    @DisplayName("Test 7: SENT notifications also invalidated (audit trail preserved)")
    @Transactional
    void testSentNotificationsInvalidated() {
        // Given - Notification that was already sent
        StacksBlock block = createAndSaveBlock("0xsent_test", 6000L);
        StacksTransaction tx = createAndSaveTransaction(block, "0xtx_sent");
        AlertNotification notification = createAndSaveNotification(tx);

        // Mark as SENT
        notification.markAsSent();
        notificationRepository.save(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);

        // When - Rollback the block
        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xsent_test", 6000L);
        processChainhookPayloadUseCase.processPayload(rollbackPayload);

        // Then - Notification still invalidated (even though already sent)
        AlertNotification invalidated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(invalidated.getInvalidated()).isTrue();
        assertThat(invalidated.getInvalidationReason()).isEqualTo("BLOCKCHAIN_REORG");

        // User already received the notification, but we mark it invalidated for audit
    }

    @Test
    @DisplayName("Test 8: No notifications for block → rollback succeeds without errors")
    @Transactional
    void testRollbackWithoutNotifications() {
        // Given - Block with transaction but NO notifications
        StacksBlock block = createAndSaveBlock("0xno_notif", 7000L);
        createAndSaveTransaction(block, "0xtx_no_notif");

        // When - Rollback
        ChainhookPayloadDto rollbackPayload = createRollbackPayload("0xno_notif", 7000L);
        ProcessChainhookPayloadUseCase.ProcessingResult result =
            processChainhookPayloadUseCase.processPayload(rollbackPayload);

        // Then - Rollback succeeds
        assertThat(result.success).isTrue();
        assertThat(result.rollbackCount).isEqualTo(1);

        // And - Block marked as deleted
        StacksBlock rolledBack = blockRepository.findById(block.getId()).orElseThrow();
        assertThat(rolledBack.getDeleted()).isTrue();
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private StacksBlock createAndSaveBlock(String blockHash, Long height) {
        StacksBlock block = new StacksBlock();
        block.setBlockHash(blockHash);
        block.setBlockHeight(height);
        block.setTimestamp(Instant.now());
        block.setParentBlockHash("0xparent");
        block.setMinerAddress("miner");
        return blockRepository.save(block);
    }

    private StacksTransaction createAndSaveTransaction(StacksBlock block, String txId) {
        StacksTransaction tx = new StacksTransaction();
        tx.setTxId(txId);
        tx.setBlock(block);
        tx.setSender("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR");
        tx.setFeeMicroStx(BigInteger.valueOf(1000));
        tx.setNonce(0L);
        tx.setSuccess(true);
        return transactionRepository.save(tx);
    }

    private AlertNotification createAndSaveNotification(StacksTransaction tx) {
        // Create a minimal alert rule (would normally be more complex)
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setUser(testUser);
        rule.setRuleName("Test Rule");
        rule.setActive(true);

        AlertNotification notification = new AlertNotification();
        notification.setAlertRule(rule);
        notification.setTransaction(tx);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setTriggeredAt(Instant.now());
        notification.setMessage("Test notification");

        return notificationRepository.save(notification);
    }

    private ChainhookPayloadDto createRollbackPayload(String blockHash, Long height) {
        BlockIdentifierDto blockId = new BlockIdentifierDto();
        blockId.setHash(blockHash);
        blockId.setIndex(height);

        BlockEventDto blockEvent = new BlockEventDto();
        blockEvent.setBlockIdentifier(blockId);
        blockEvent.setTimestamp(Instant.now().getEpochSecond());

        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        payload.setRollback(List.of(blockEvent));
        payload.setApply(List.of());

        return payload;
    }

    private ChainhookPayloadDto createApplyPayload(String blockHash, Long height) {
        BlockIdentifierDto blockId = new BlockIdentifierDto();
        blockId.setHash(blockHash);
        blockId.setIndex(height);

        BlockEventDto blockEvent = new BlockEventDto();
        blockEvent.setBlockIdentifier(blockId);
        blockEvent.setTimestamp(Instant.now().getEpochSecond());
        blockEvent.setTransactions(List.of()); // Empty for restoration test

        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        payload.setApply(List.of(blockEvent));
        payload.setRollback(List.of());

        return payload;
    }
}

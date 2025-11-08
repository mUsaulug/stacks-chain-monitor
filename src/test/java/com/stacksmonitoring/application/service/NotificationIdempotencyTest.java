package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.model.monitoring.MonitoredContract;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.repository.AlertRuleRepository;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import com.stacksmonitoring.domain.repository.StacksTransactionRepository;
import com.stacksmonitoring.domain.repository.UserRepository;
import com.stacksmonitoring.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for P1-5: Idempotency constraints on notifications.
 *
 * Tests that duplicate webhook deliveries don't create duplicate notifications
 * thanks to unique constraint on (alert_rule_id, transaction_id, event_id, channel).
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=false",
    "logging.level.com.stacksmonitoring=DEBUG"
})
class NotificationIdempotencyTest {

    @Autowired
    private AlertMatchingService alertMatchingService;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertNotificationRepository alertNotificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StacksBlockRepository stacksBlockRepository;

    @Autowired
    private StacksTransactionRepository stacksTransactionRepository;

    private User testUser;
    private MonitoredContract testContract;
    private ContractCallAlertRule testRule;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up
        alertNotificationRepository.deleteAll();
        alertRuleRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPassword("password");
        testUser = userRepository.save(testUser);

        // Create test contract
        testContract = new MonitoredContract();
        testContract.setContractIdentifier("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1");
        testContract.setUser(testUser);

        // Create test alert rule with 0-minute cooldown (no cooldown)
        testRule = new ContractCallAlertRule();
        testRule.setUser(testUser);
        testRule.setMonitoredContract(testContract);
        testRule.setRuleName("Test Idempotency Rule");
        testRule.setRuleType(AlertRuleType.CONTRACT_CALL);
        testRule.setSeverity(AlertSeverity.INFO);
        testRule.setIsActive(true);
        testRule.setCooldownMinutes(0); // No cooldown - allows multiple triggers
        testRule.setNotificationChannels(List.of(NotificationChannel.EMAIL));
        testRule.setNotificationEmails("test@example.com");
        testRule.setContractIdentifier("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1");
        testRule.setFunctionName("swap-x-for-y");
        testRule = (ContractCallAlertRule) alertRuleRepository.save(testRule);
    }

    @Test
    @DisplayName("✅ First webhook creates notification successfully")
    @Transactional
    void testFirstWebhookCreatesNotification() {
        // Given: A transaction that matches the rule
        StacksTransaction transaction = createMatchingTransaction("0x1111");

        // When: Evaluate transaction (webhook arrives)
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then: Should create exactly 1 notification
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getChannel()).isEqualTo(NotificationChannel.EMAIL);

        // Verify in database
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(1);
    }

    @Test
    @DisplayName("✅ Duplicate webhook (same tx) does NOT create duplicate notification")
    @Transactional
    void testDuplicateWebhookIdempotency() {
        // Given: A transaction that matches the rule
        StacksTransaction transaction1 = createMatchingTransaction("0x1111");

        // When: First webhook arrives
        List<AlertNotification> notifications1 = alertMatchingService.evaluateTransaction(transaction1);
        assertThat(notifications1).hasSize(1);

        // And: Duplicate webhook arrives (network retry, same transaction)
        StacksTransaction transaction2 = createMatchingTransaction("0x1111"); // SAME TX ID
        List<AlertNotification> notifications2 = alertMatchingService.evaluateTransaction(transaction2);

        // Then: Second evaluation should NOT create duplicate notification
        // (unique constraint prevents it)
        assertThat(notifications2).isEmpty();

        // Verify total notifications count (should still be 1)
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(1);
    }

    @Test
    @DisplayName("✅ Multiple webhooks with DIFFERENT transactions create separate notifications")
    @Transactional
    void testDifferentTransactionsCreateSeparateNotifications() {
        // Given: Two different transactions that both match the rule
        StacksTransaction transaction1 = createMatchingTransaction("0x1111");
        StacksTransaction transaction2 = createMatchingTransaction("0x2222"); // DIFFERENT TX

        // When: First webhook arrives
        List<AlertNotification> notifications1 = alertMatchingService.evaluateTransaction(transaction1);
        assertThat(notifications1).hasSize(1);

        // Wait to avoid cooldown (rule has 0 cooldown but need small delay for DB)
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // And: Second webhook arrives with different transaction
        List<AlertNotification> notifications2 = alertMatchingService.evaluateTransaction(transaction2);
        assertThat(notifications2).hasSize(1);

        // Then: Should have created 2 separate notifications
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(2);

        // Verify both transactions are present
        List<AlertNotification> allNotifications = alertNotificationRepository.findAll();
        assertThat(allNotifications)
            .extracting(n -> n.getTransaction().getTxId())
            .containsExactlyInAnyOrder("0x1111", "0x2222");
    }

    @Test
    @DisplayName("✅ Same transaction, same rule, DIFFERENT channels create 2 notifications")
    @Transactional
    void testDifferentChannelsAllowed() {
        // Given: Rule with TWO notification channels
        testRule.setNotificationChannels(List.of(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK));
        testRule.setWebhookUrl("https://example.com/webhook");
        alertRuleRepository.save(testRule);

        // When: Webhook arrives
        StacksTransaction transaction = createMatchingTransaction("0x1111");
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then: Should create 2 notifications (one per channel)
        assertThat(notifications).hasSize(2);

        // Verify channels
        assertThat(notifications)
            .extracting(AlertNotification::getChannel)
            .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK);

        // Verify in database
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(2);
    }

    @Test
    @DisplayName("🔥 IDEMPOTENCY TEST: Process same webhook 5 times → only 1 notification")
    @Transactional
    void testWebhookReprocessingIdempotency() {
        // Given: Same transaction will be processed multiple times
        String txId = "0x1111";

        // When: Process the same transaction 5 times (simulating webhook retries)
        for (int i = 0; i < 5; i++) {
            StacksTransaction transaction = createMatchingTransaction(txId);
            alertMatchingService.evaluateTransaction(transaction);
        }

        // Then: Should have created only 1 notification (idempotency)
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(1);

        // Verify the notification
        AlertNotification notification = alertNotificationRepository.findAll().get(0);
        assertThat(notification.getTransaction().getTxId()).isEqualTo(txId);
        assertThat(notification.getAlertRule().getId()).isEqualTo(testRule.getId());
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("✅ Blockchain reorg (same tx in different block) handled correctly")
    @Transactional
    void testBlockchainReorgIdempotency() {
        // Given: Transaction in original block
        StacksTransaction transaction1 = createMatchingTransaction("0x1111");
        transaction1.getBlock().setBlockHash("0xAAAA");
        transaction1.getBlock().setBlockHeight(100000L);

        // When: First webhook (original chain)
        List<AlertNotification> notifications1 = alertMatchingService.evaluateTransaction(transaction1);
        assertThat(notifications1).hasSize(1);

        // And: Blockchain reorg - same transaction appears in different block
        StacksTransaction transaction2 = createMatchingTransaction("0x1111"); // SAME TX ID
        transaction2.getBlock().setBlockHash("0xBBBB"); // DIFFERENT BLOCK
        transaction2.getBlock().setBlockHeight(100001L);

        List<AlertNotification> notifications2 = alertMatchingService.evaluateTransaction(transaction2);

        // Then: Should NOT create duplicate notification (same tx_id)
        assertThat(notifications2).isEmpty();

        // Verify total notifications
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(1);
    }

    // Helper Methods

    private StacksTransaction createMatchingTransaction(String txId) {
        // Create block
        StacksBlock block = new StacksBlock();
        block.setBlockHash("0x" + txId.substring(2) + "_block");
        block.setBlockHeight(100000L);
        block.setIndexBlockHash("0x5678");
        block.setBurnBlockHeight(50000L);
        block.setBurnBlockHash("0xabcd");
        block.setBurnBlockTime(Instant.now());
        block.setMinerTxId("0xminer");
        block.setParentBlockHash("0x0000");
        block.setDeleted(false);
        block = stacksBlockRepository.save(block);

        // Create transaction
        StacksTransaction transaction = new StacksTransaction();
        transaction.setTxId(txId);
        transaction.setTxIndex(0);
        transaction.setBlock(block);
        transaction.setSender("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR");
        transaction.setNonce(0L);
        transaction.setFee(1000L);
        transaction.setSponsored(false);
        transaction.setSuccess(true);
        transaction.setRawResult("0x03");
        transaction.setRawTx("0x00");
        transaction.setEvents(new ArrayList<>());
        transaction.setDeleted(false);

        // Create contract call that matches the rule
        ContractCall contractCall = new ContractCall();
        contractCall.setTransaction(transaction);
        contractCall.setContractIdentifier("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1");
        contractCall.setFunctionName("swap-x-for-y");
        contractCall.setFunctionArgs("[]");

        transaction.setContractCall(contractCall);
        transaction = stacksTransactionRepository.save(transaction);

        return transaction;
    }
}

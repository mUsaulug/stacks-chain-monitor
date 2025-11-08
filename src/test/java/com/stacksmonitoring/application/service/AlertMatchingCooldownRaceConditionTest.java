package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.monitoring.AlertRule;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for P1-4: DB-level cooldown race condition fix.
 *
 * Tests the atomic conditional UPDATE that prevents duplicate notifications
 * when multiple threads evaluate the same rule simultaneously.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=false",
    "logging.level.com.stacksmonitoring=DEBUG"
})
class AlertMatchingCooldownRaceConditionTest {

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

        // Create test alert rule with 1-minute cooldown
        testRule = new ContractCallAlertRule();
        testRule.setUser(testUser);
        testRule.setMonitoredContract(testContract);
        testRule.setRuleName("Test Cooldown Rule");
        testRule.setRuleType(AlertRuleType.CONTRACT_CALL);
        testRule.setSeverity(AlertSeverity.INFO);
        testRule.setIsActive(true);
        testRule.setCooldownMinutes(1); // 1 minute cooldown
        testRule.setNotificationChannels(List.of(NotificationChannel.EMAIL));
        testRule.setNotificationEmails("test@example.com");
        testRule.setContractIdentifier("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1");
        testRule.setFunctionName("swap-x-for-y");
        testRule = (ContractCallAlertRule) alertRuleRepository.save(testRule);
    }

    @Test
    @DisplayName("✅ Single thread triggers rule successfully")
    @Transactional
    void testSingleThreadTrigger() {
        // Given: A transaction that matches the rule
        StacksTransaction transaction = createMatchingTransaction();

        // When: Evaluate transaction
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then: Should create exactly 1 notification
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getChannel()).isEqualTo(NotificationChannel.EMAIL);

        // Verify rule was marked as triggered
        AlertRule updatedRule = alertRuleRepository.findById(testRule.getId()).orElseThrow();
        assertThat(updatedRule.getLastTriggeredAt()).isNotNull();
    }

    @Test
    @DisplayName("✅ Second trigger within cooldown is blocked")
    @Transactional
    void testCooldownBlocking() {
        // Given: A transaction that matches the rule
        StacksTransaction transaction1 = createMatchingTransaction();

        // When: First evaluation triggers the rule
        List<AlertNotification> notifications1 = alertMatchingService.evaluateTransaction(transaction1);
        assertThat(notifications1).hasSize(1);

        // And: Second evaluation within cooldown period
        StacksTransaction transaction2 = createMatchingTransaction();
        transaction2.setTxId("0x9999"); // Different tx
        List<AlertNotification> notifications2 = alertMatchingService.evaluateTransaction(transaction2);

        // Then: Second trigger should be blocked by cooldown
        assertThat(notifications2).isEmpty();

        // Verify total notifications count
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(1);
    }

    @Test
    @DisplayName("🔥 RACE CONDITION TEST: 10 concurrent threads → only 1 notification")
    void testConcurrentTriggersPreventDuplicates() throws InterruptedException {
        // Given: 10 threads that will evaluate the same rule simultaneously
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: All threads evaluate the same rule at the same time
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    // Create matching transaction
                    StacksTransaction transaction = createMatchingTransaction();

                    // Evaluate (this is where race condition would occur)
                    List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

                    if (!notifications.isEmpty()) {
                        successCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        executor.shutdown();

        // Then: Only ONE thread should have successfully created notifications
        // (DB-level atomic UPDATE ensures only one wins)
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(1);

        // At most 1 thread should report success
        assertThat(successCount.get()).isLessThanOrEqualTo(1);

        // Verify rule was triggered exactly once
        AlertRule updatedRule = alertRuleRepository.findById(testRule.getId()).orElseThrow();
        assertThat(updatedRule.getLastTriggeredAt()).isNotNull();
    }

    @Test
    @DisplayName("✅ Trigger after cooldown expires works correctly")
    @Transactional
    void testTriggerAfterCooldownExpires() {
        // Given: Rule with 0-second cooldown (expired immediately)
        testRule.setCooldownMinutes(0);
        alertRuleRepository.save(testRule);

        // When: First trigger
        StacksTransaction transaction1 = createMatchingTransaction();
        List<AlertNotification> notifications1 = alertMatchingService.evaluateTransaction(transaction1);
        assertThat(notifications1).hasSize(1);

        // And: Wait 1 second (cooldown expired)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // And: Second trigger
        StacksTransaction transaction2 = createMatchingTransaction();
        transaction2.setTxId("0x9999");
        List<AlertNotification> notifications2 = alertMatchingService.evaluateTransaction(transaction2);

        // Then: Second trigger should succeed (cooldown expired)
        assertThat(notifications2).hasSize(1);

        // Verify total notifications
        long totalNotifications = alertNotificationRepository.count();
        assertThat(totalNotifications).isEqualTo(2);
    }

    @Test
    @DisplayName("✅ Non-matching transaction doesn't trigger rule")
    @Transactional
    void testNonMatchingTransaction() {
        // Given: A transaction that doesn't match the rule
        StacksTransaction transaction = createMatchingTransaction();
        transaction.getContractCall().setContractIdentifier("DIFFERENT.contract");

        // When: Evaluate transaction
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then: Should not create any notifications
        assertThat(notifications).isEmpty();

        // Verify rule was NOT marked as triggered
        AlertRule updatedRule = alertRuleRepository.findById(testRule.getId()).orElseThrow();
        assertThat(updatedRule.getLastTriggeredAt()).isNull();
    }

    // Helper Methods

    private StacksTransaction createMatchingTransaction() {
        // Create block
        StacksBlock block = new StacksBlock();
        block.setBlockHash("0x1234");
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
        transaction.setTxId("0x1111");
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

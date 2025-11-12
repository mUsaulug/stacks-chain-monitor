package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.blockchain.*;
import com.stacksmonitoring.domain.model.monitoring.*;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.repository.AlertRuleRepository;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.EventType;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertMatchingService.
 */
@ExtendWith(MockitoExtension.class)
class AlertMatchingServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertNotificationRepository alertNotificationRepository;

    private AlertMatchingService alertMatchingService;

    @BeforeEach
    void setUp() {
        alertMatchingService = new AlertMatchingService(alertRuleRepository, alertNotificationRepository);
    }

    @Test
    void testEvaluateTransaction_WithContractCall_ShouldTriggerContractCallAlert() {
        // Given
        StacksTransaction transaction = createTestTransaction();
        ContractCall contractCall = new ContractCall();
        contractCall.setContractIdentifier("SP123.my-contract");
        contractCall.setFunctionName("transfer");
        transaction.setContractCall(contractCall);

        ContractCallAlertRule rule = createContractCallRule();

        // Mock index building (getRuleIndex calls findAllActive)
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));

        // Mock atomic cooldown check (returns 1 = successfully triggered)
        when(alertRuleRepository.markTriggeredIfOutOfCooldown(any(), any(), any())).thenReturn(1);

        // Mock full entity reload after cooldown check
        when(alertRuleRepository.findById(rule.getId())).thenReturn(java.util.Optional.of(rule));

        when(alertNotificationRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).hasSize(1);
        verify(alertNotificationRepository, times(1)).save(any(AlertNotification.class));
    }

    @Test
    void testEvaluateTransaction_WithFTTransferEvent_ShouldTriggerTokenTransferAlert() {
        // Given
        StacksTransaction transaction = createTestTransaction();

        FTTransferEvent ftEvent = new FTTransferEvent();
        ftEvent.setAssetIdentifier("SP123.token::my-token");
        ftEvent.setAmount(new BigDecimal("5000"));
        ftEvent.setSender("SP111");
        ftEvent.setRecipient("SP222");
        ftEvent.setEventType(EventType.FT_TRANSFER);
        ftEvent.setEventIndex(0);
        transaction.addEvent(ftEvent);

        TokenTransferAlertRule rule = createTokenTransferRule();

        // Mock index building
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));
        when(alertRuleRepository.markTriggeredIfOutOfCooldown(any(), any(), any())).thenReturn(1);
        when(alertRuleRepository.findById(rule.getId())).thenReturn(java.util.Optional.of(rule));
        when(alertNotificationRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getEvent()).isEqualTo(ftEvent);
    }

    @Test
    void testEvaluateTransaction_RuleInCooldown_ShouldNotTrigger() {
        // Given
        StacksTransaction transaction = createTestTransaction();
        ContractCall contractCall = new ContractCall();
        contractCall.setContractIdentifier("SP123.my-contract");
        contractCall.setFunctionName("transfer");
        transaction.setContractCall(contractCall);

        ContractCallAlertRule rule = createContractCallRule();
        rule.setLastTriggeredAt(Instant.now().minusSeconds(30 * 60)); // 30 minutes ago
        rule.setCooldownMinutes(60); // 1 hour cooldown

        // Mock index building
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));

        // Mock atomic cooldown check (returns 0 = cooldown active, no update)
        when(alertRuleRepository.markTriggeredIfOutOfCooldown(any(), any(), any())).thenReturn(0);

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).isEmpty();
        verify(alertNotificationRepository, never()).save(any());
    }

    @Test
    void testEvaluateTransaction_MultipleChannels_ShouldCreateMultipleNotifications() {
        // Given
        StacksTransaction transaction = createTestTransaction();
        ContractCall contractCall = new ContractCall();
        contractCall.setContractIdentifier("SP123.my-contract");
        contractCall.setFunctionName("transfer");
        transaction.setContractCall(contractCall);

        ContractCallAlertRule rule = createContractCallRule();
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL, NotificationChannel.WEBHOOK));

        // Mock index building
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));
        when(alertRuleRepository.markTriggeredIfOutOfCooldown(any(), any(), any())).thenReturn(1);
        when(alertRuleRepository.findById(rule.getId())).thenReturn(java.util.Optional.of(rule));
        when(alertNotificationRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).hasSize(2);
        assertThat(notifications.get(0).getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notifications.get(1).getChannel()).isEqualTo(NotificationChannel.WEBHOOK);
    }

    @Test
    void testEvaluateTransaction_FailedTransaction_ShouldTriggerFailedTransactionAlert() {
        // Given
        StacksTransaction transaction = createTestTransaction();
        transaction.setSuccess(false);

        FailedTransactionAlertRule rule = new FailedTransactionAlertRule();
        rule.setId(1L);
        rule.setRuleType(AlertRuleType.FAILED_TRANSACTION);
        rule.setSeverity(AlertSeverity.HIGH);
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));
        rule.setRuleName("Failed Transaction Alert");

        // Mock index building
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));
        when(alertRuleRepository.markTriggeredIfOutOfCooldown(any(), any(), any())).thenReturn(1);
        when(alertRuleRepository.findById(rule.getId())).thenReturn(java.util.Optional.of(rule));
        when(alertNotificationRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).hasSize(1);
        verify(alertNotificationRepository, times(1)).save(any(AlertNotification.class));
    }

    @Test
    void testEvaluateTransaction_NoMatchingRules_ShouldNotCreateNotifications() {
        // Given
        StacksTransaction transaction = createTestTransaction();
        ContractCall contractCall = new ContractCall();
        contractCall.setContractIdentifier("SP999.other-contract");
        contractCall.setFunctionName("other-function");
        transaction.setContractCall(contractCall);

        ContractCallAlertRule rule = createContractCallRule();

        // Mock index building (rule exists but won't match SP999.other-contract)
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).isEmpty();
        verify(alertNotificationRepository, never()).save(any());
    }

    @Test
    void testEvaluateTransaction_WithAmountThreshold_ShouldMatchAboveThreshold() {
        // Given
        StacksTransaction transaction = createTestTransaction();

        FTTransferEvent ftEvent = new FTTransferEvent();
        ftEvent.setAssetIdentifier("SP123.token::my-token");
        ftEvent.setAmount(new BigDecimal("10000")); // Above threshold
        ftEvent.setEventType(EventType.FT_TRANSFER);
        ftEvent.setEventIndex(0);
        transaction.addEvent(ftEvent);

        TokenTransferAlertRule rule = createTokenTransferRule();
        rule.setAmountThreshold(new BigDecimal("1000"));

        // Mock index building
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));
        when(alertRuleRepository.markTriggeredIfOutOfCooldown(any(), any(), any())).thenReturn(1);
        when(alertRuleRepository.findById(rule.getId())).thenReturn(java.util.Optional.of(rule));
        when(alertNotificationRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).hasSize(1);
    }

    @Test
    void testEvaluateTransaction_BelowAmountThreshold_ShouldNotMatch() {
        // Given
        StacksTransaction transaction = createTestTransaction();

        FTTransferEvent ftEvent = new FTTransferEvent();
        ftEvent.setAssetIdentifier("SP123.token::my-token");
        ftEvent.setAmount(new BigDecimal("500")); // Below threshold
        ftEvent.setEventType(EventType.FT_TRANSFER);
        ftEvent.setEventIndex(0);
        transaction.addEvent(ftEvent);

        TokenTransferAlertRule rule = createTokenTransferRule();
        rule.setAmountThreshold(new BigDecimal("1000"));

        // Mock index building (rule won't match because amount is below threshold)
        when(alertRuleRepository.findAllActive()).thenReturn(List.of(rule));

        // When
        List<AlertNotification> notifications = alertMatchingService.evaluateTransaction(transaction);

        // Then
        assertThat(notifications).isEmpty();
    }

    // Note: getActiveRulesByType() is in AlertRuleService, not AlertMatchingService
    // @Test
    // void testGetActiveRulesByType_ShouldReturnCachedRules() {
    //     // Given
    //     AlertRuleType ruleType = AlertRuleType.CONTRACT_CALL;
    //     List<AlertRule> rules = List.of(createContractCallRule());
    //
    //     when(alertRuleRepository.findActiveByRuleType(ruleType)).thenReturn(rules);
    //
    //     // When
    //     List<AlertRule> result = alertMatchingService.getActiveRulesByType(ruleType);
    //
    //     // Then
    //     assertThat(result).hasSize(1);
    //     verify(alertRuleRepository, times(1)).findActiveByRuleType(ruleType);
    // }

    // Helper methods

    private StacksTransaction createTestTransaction() {
        StacksTransaction transaction = new StacksTransaction();
        transaction.setTxId("0xtx123");
        transaction.setSender("SP123");
        transaction.setSuccess(true);

        StacksBlock block = new StacksBlock();
        block.setBlockHeight(1000L);
        block.setBlockHash("0xblock123");
        transaction.setBlock(block);

        return transaction;
    }

    private ContractCallAlertRule createContractCallRule() {
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(1L);
        rule.setRuleType(AlertRuleType.CONTRACT_CALL);
        rule.setContractIdentifier("SP123.my-contract");
        rule.setFunctionName("transfer");
        rule.setSeverity(AlertSeverity.MEDIUM);
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));
        rule.setRuleName("Contract Call Alert");
        rule.setCooldownMinutes(60);
        return rule;
    }

    private TokenTransferAlertRule createTokenTransferRule() {
        TokenTransferAlertRule rule = new TokenTransferAlertRule();
        rule.setId(2L);
        rule.setRuleType(AlertRuleType.TOKEN_TRANSFER);
        rule.setAssetIdentifier("SP123.token::my-token");
        rule.setEventType(EventType.FT_TRANSFER);
        rule.setSeverity(AlertSeverity.LOW);
        rule.setNotificationChannels(List.of(NotificationChannel.WEBHOOK));
        rule.setRuleName("Token Transfer Alert");
        rule.setCooldownMinutes(60);
        return rule;
    }
}

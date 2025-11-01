package com.stacksmonitoring.application.service;

import com.stacksmonitoring.api.dto.alert.CreateAlertRuleRequest;
import com.stacksmonitoring.domain.model.monitoring.*;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.repository.AlertRuleRepository;
import com.stacksmonitoring.domain.repository.UserRepository;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing alert rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final UserRepository userRepository;
    private final AlertMatchingService alertMatchingService;

    /**
     * Create a new alert rule.
     */
    @Transactional
    public AlertRule createRule(CreateAlertRuleRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        AlertRule rule = switch (request.getRuleType()) {
            case CONTRACT_CALL -> createContractCallRule(request);
            case TOKEN_TRANSFER -> createTokenTransferRule(request);
            case FAILED_TRANSACTION -> createFailedTransactionRule(request);
            case PRINT_EVENT -> createPrintEventRule(request);
            case ADDRESS_ACTIVITY -> createAddressActivityRule(request);
        };

        rule.setUser(user);
        rule.setRuleName(request.getRuleName());
        rule.setDescription(request.getDescription());
        rule.setSeverity(request.getSeverity());
        rule.setCooldownMinutes(request.getCooldownMinutes());
        rule.setNotificationChannels(request.getNotificationChannels());
        rule.setNotificationEmails(request.getNotificationEmails());
        rule.setWebhookUrl(request.getWebhookUrl());
        rule.setIsActive(true);

        AlertRule savedRule = alertRuleRepository.save(rule);

        // Invalidate cache
        alertMatchingService.invalidateRulesCache();

        log.info("Created alert rule {} for user {}", savedRule.getId(), email);

        return savedRule;
    }

    private ContractCallAlertRule createContractCallRule(CreateAlertRuleRequest request) {
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setContractIdentifier(request.getContractIdentifier());
        rule.setFunctionName(request.getFunctionName());
        rule.setAmountThreshold(request.getAmountThreshold());
        return rule;
    }

    private TokenTransferAlertRule createTokenTransferRule(CreateAlertRuleRequest request) {
        TokenTransferAlertRule rule = new TokenTransferAlertRule();
        rule.setAssetIdentifier(request.getAssetIdentifier());
        rule.setAmountThreshold(request.getAmountThreshold());
        if (request.getEventType() != null) {
            rule.setEventType(EventType.valueOf(request.getEventType()));
        }
        return rule;
    }

    private FailedTransactionAlertRule createFailedTransactionRule(CreateAlertRuleRequest request) {
        FailedTransactionAlertRule rule = new FailedTransactionAlertRule();
        rule.setContractIdentifier(request.getContractIdentifier());
        return rule;
    }

    private PrintEventAlertRule createPrintEventRule(CreateAlertRuleRequest request) {
        PrintEventAlertRule rule = new PrintEventAlertRule();
        rule.setContractIdentifier(request.getContractIdentifier());
        return rule;
    }

    private AddressActivityAlertRule createAddressActivityRule(CreateAlertRuleRequest request) {
        AddressActivityAlertRule rule = new AddressActivityAlertRule();
        // Address activity specific fields would be set here
        return rule;
    }

    /**
     * Get all rules for a user.
     */
    public List<AlertRule> getUserRules(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        return alertRuleRepository.findByUserId(user.getId());
    }

    /**
     * Get active rules for a user.
     */
    public List<AlertRule> getActiveUserRules(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        return alertRuleRepository.findActiveByUserId(user.getId());
    }

    /**
     * Get a specific rule.
     */
    public AlertRule getRule(Long ruleId) {
        return alertRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));
    }

    /**
     * Update rule status (activate/deactivate).
     */
    @Transactional
    public AlertRule updateRuleStatus(Long ruleId, boolean active) {
        AlertRule rule = getRule(ruleId);
        rule.setIsActive(active);

        AlertRule updatedRule = alertRuleRepository.save(rule);

        // Invalidate cache
        alertMatchingService.invalidateRulesCache();

        log.info("Updated rule {} status to {}", ruleId, active);

        return updatedRule;
    }

    /**
     * Delete a rule.
     */
    @Transactional
    public void deleteRule(Long ruleId) {
        alertRuleRepository.deleteById(ruleId);

        // Invalidate cache
        alertMatchingService.invalidateRulesCache();

        log.info("Deleted alert rule {}", ruleId);
    }

    /**
     * Get all active rules by type.
     */
    public List<AlertRule> getActiveRulesByType(AlertRuleType ruleType) {
        return alertRuleRepository.findActiveByRuleType(ruleType);
    }
}

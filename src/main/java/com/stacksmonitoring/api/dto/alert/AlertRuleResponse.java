package com.stacksmonitoring.api.dto.alert;

import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.model.monitoring.TokenTransferAlertRule;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response DTO for alert rule.
 */
@Data
public class AlertRuleResponse {

    private Long id;
    private String ruleName;
    private String description;
    private AlertRuleType ruleType;
    private AlertSeverity severity;
    private Boolean isActive;
    private Integer cooldownMinutes;
    private Instant lastTriggeredAt;
    private List<NotificationChannel> notificationChannels;
    private String notificationEmails;
    private String webhookUrl;
    private Instant createdAt;
    private Instant updatedAt;

    // Type-specific fields
    private String contractIdentifier;
    private String functionName;
    private String assetIdentifier;
    private BigDecimal amountThreshold;
    private String eventType;

    /**
     * Convert domain entity to response DTO.
     */
    public static AlertRuleResponse fromEntity(AlertRule rule) {
        AlertRuleResponse response = new AlertRuleResponse();

        response.setId(rule.getId());
        response.setRuleName(rule.getRuleName());
        response.setDescription(rule.getDescription());
        response.setRuleType(rule.getRuleType());
        response.setSeverity(rule.getSeverity());
        response.setIsActive(rule.getIsActive());
        response.setCooldownMinutes(rule.getCooldownMinutes());
        response.setLastTriggeredAt(rule.getLastTriggeredAt());
        response.setNotificationChannels(rule.getNotificationChannels());
        response.setNotificationEmails(rule.getNotificationEmails());
        response.setWebhookUrl(rule.getWebhookUrl());
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());

        // Type-specific fields
        if (rule instanceof ContractCallAlertRule) {
            ContractCallAlertRule contractCallRule = (ContractCallAlertRule) rule;
            response.setContractIdentifier(contractCallRule.getContractIdentifier());
            response.setFunctionName(contractCallRule.getFunctionName());
            response.setAmountThreshold(contractCallRule.getAmountThreshold());
        } else if (rule instanceof TokenTransferAlertRule) {
            TokenTransferAlertRule tokenRule = (TokenTransferAlertRule) rule;
            response.setAssetIdentifier(tokenRule.getAssetIdentifier());
            response.setAmountThreshold(tokenRule.getAmountThreshold());
            if (tokenRule.getEventType() != null) {
                response.setEventType(tokenRule.getEventType().toString());
            }
        }

        return response;
    }
}

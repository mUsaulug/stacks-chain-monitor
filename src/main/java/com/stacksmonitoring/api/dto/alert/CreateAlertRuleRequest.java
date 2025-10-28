package com.stacksmonitoring.api.dto.alert;

import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for creating a new alert rule.
 */
@Data
public class CreateAlertRuleRequest {

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    private String description;

    @NotNull(message = "Rule type is required")
    private AlertRuleType ruleType;

    @NotNull(message = "Severity is required")
    private AlertSeverity severity;

    private Integer cooldownMinutes = 60;

    @NotNull(message = "At least one notification channel is required")
    private List<NotificationChannel> notificationChannels;

    private String notificationEmails;

    private String webhookUrl;

    // Type-specific fields
    private String contractIdentifier;
    private String functionName;
    private String assetIdentifier;
    private BigDecimal amountThreshold;
    private String eventType;
}

package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all alert rules.
 * Uses SINGLE_TABLE inheritance strategy where all subtypes share one table.
 * This allows for efficient querying and simple rule management.
 */
@Entity
@Table(name = "alert_rule", indexes = {
    @Index(name = "idx_alert_rule_user", columnList = "user_id"),
    @Index(name = "idx_alert_rule_contract", columnList = "monitored_contract_id"),
    @Index(name = "idx_alert_rule_active", columnList = "is_active"),
    @Index(name = "idx_alert_rule_type", columnList = "rule_type")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "rule_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alert_rule_seq_gen")
    @SequenceGenerator(name = "alert_rule_seq_gen", sequenceName = "alert_rule_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitored_contract_id")
    private MonitoredContract monitoredContract;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, insertable = false, updatable = false)
    private AlertRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity = AlertSeverity.INFO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Cooldown period in minutes to prevent spam.
     * The rule won't trigger again within this period after being triggered.
     */
    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes = 60;

    /**
     * Last time this rule was triggered.
     * Used for cooldown calculation.
     */
    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    /**
     * Notification channels for this rule (EMAIL, WEBHOOK).
     * Stored as JSON array.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_channels", columnDefinition = "jsonb")
    private List<NotificationChannel> notificationChannels = new ArrayList<>();

    /**
     * Email addresses to notify (if EMAIL channel is enabled).
     */
    @Column(name = "notification_emails", length = 500)
    private String notificationEmails;

    /**
     * Webhook URL to call (if WEBHOOK channel is enabled).
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @OneToMany(mappedBy = "alertRule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertNotification> notifications = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Check if this rule is currently in cooldown period.
     */
    public boolean isInCooldown() {
        if (lastTriggeredAt == null) {
            return false;
        }
        Instant cooldownEndTime = lastTriggeredAt.plusSeconds(cooldownMinutes * 60L);
        return Instant.now().isBefore(cooldownEndTime);
    }

    /**
     * Mark this rule as triggered.
     * Updates lastTriggeredAt timestamp.
     */
    public void markAsTriggered() {
        this.lastTriggeredAt = Instant.now();
    }

    /**
     * Check if a specific notification channel is enabled.
     */
    public boolean hasChannel(NotificationChannel channel) {
        return notificationChannels != null && notificationChannels.contains(channel);
    }

    /**
     * Abstract method to be implemented by each rule type.
     * Determines if this rule matches the given event/transaction.
     */
    public abstract boolean matches(Object context);

    /**
     * Get a human-readable description of what triggered this alert.
     */
    public abstract String getTriggerDescription(Object context);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertRule)) return false;
        AlertRule alertRule = (AlertRule) o;
        return id != null && id.equals(alertRule.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

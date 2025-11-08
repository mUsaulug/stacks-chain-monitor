package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.blockchain.TransactionEvent;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Represents a notification sent when an alert rule is triggered.
 * Tracks delivery status and failure reasons for retry logic.
 *
 * Idempotency: Unique constraint on (alert_rule_id, transaction_id, event_id, channel)
 * ensures duplicate notifications are prevented even if webhooks arrive multiple times.
 */
@Entity
@Table(name = "alert_notification",
    indexes = {
        @Index(name = "idx_notification_rule", columnList = "alert_rule_id"),
        @Index(name = "idx_notification_transaction", columnList = "transaction_id"),
        @Index(name = "idx_notification_triggered_at", columnList = "triggered_at"),
        @Index(name = "idx_notification_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_notification_rule_tx_event_channel",
            columnNames = {"alert_rule_id", "transaction_id", "event_id", "channel"}
        )
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private StacksTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private TransactionEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private Instant triggeredAt;

    @Column
    private Instant sentAt;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /**
     * Number of send attempts (for retry logic).
     */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Mark notification as sent successfully.
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Mark notification as failed with reason.
     */
    public void markAsFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * Increment attempt count.
     */
    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    /**
     * Check if retry should be attempted.
     * Max 3 attempts.
     */
    public boolean shouldRetry() {
        return attemptCount < 3 && status == NotificationStatus.FAILED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertNotification)) return false;
        AlertNotification that = (AlertNotification) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

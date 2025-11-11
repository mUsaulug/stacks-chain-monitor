package com.stacksmonitoring.domain.model.monitoring;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Dead Letter Queue entry for permanently failed notifications.
 * Stores notifications that exceeded retry limits or encountered permanent failures.
 */
@Entity
@Table(name = "notification_dead_letter_queue", indexes = {
    @Index(name = "idx_dlq_pending", columnList = "queued_at"),
    @Index(name = "idx_dlq_notification", columnList = "notification_id"),
    @Index(name = "idx_dlq_failure_reason", columnList = "failure_reason"),
    @Index(name = "idx_dlq_channel", columnList = "channel")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDeadLetterQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_dlq_seq_gen")
    @SequenceGenerator(name = "notification_dlq_seq_gen", sequenceName = "notification_dead_letter_queue_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private AlertNotification notification;

    @Column(name = "alert_rule_id", nullable = false)
    private Long alertRuleId;

    @Column(name = "alert_rule_name", nullable = false, length = 200)
    private String alertRuleName;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "recipient", nullable = false, length = 500)
    private String recipient;

    @Column(name = "failure_reason", nullable = false, length = 100)
    private String failureReason;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "first_attempt_at", nullable = false)
    private Instant firstAttemptAt;

    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;

    @CreatedDate
    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    /**
     * Mark this DLQ entry as processed.
     */
    public void markAsProcessed(String processedBy, String resolutionNotes) {
        this.processed = true;
        this.processedAt = Instant.now();
        this.processedBy = processedBy;
        this.resolutionNotes = resolutionNotes;
    }

    /**
     * Create a DLQ entry from a failed notification.
     */
    public static NotificationDeadLetterQueue fromNotification(
        AlertNotification notification,
        String failureReason,
        String errorMessage,
        String errorStackTrace,
        Integer attemptCount,
        Instant firstAttemptAt,
        Instant lastAttemptAt
    ) {
        NotificationDeadLetterQueue dlq = new NotificationDeadLetterQueue();
        dlq.setNotification(notification);
        dlq.setAlertRuleId(notification.getAlertRule().getId());
        dlq.setAlertRuleName(notification.getAlertRule().getRuleName());
        dlq.setChannel(notification.getChannel().name());

        // Determine recipient based on channel
        String recipient = switch (notification.getChannel()) {
            case EMAIL -> notification.getAlertRule().getNotificationEmails();
            case WEBHOOK -> notification.getAlertRule().getWebhookUrl();
            default -> "UNKNOWN";
        };
        dlq.setRecipient(recipient);

        dlq.setFailureReason(failureReason);
        dlq.setErrorMessage(errorMessage);
        dlq.setErrorStackTrace(errorStackTrace);
        dlq.setAttemptCount(attemptCount);
        dlq.setFirstAttemptAt(firstAttemptAt);
        dlq.setLastAttemptAt(lastAttemptAt);

        return dlq;
    }
}

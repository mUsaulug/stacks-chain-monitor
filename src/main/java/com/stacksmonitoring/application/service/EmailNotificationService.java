package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Email notification service using Spring Mail.
 * Sends alert notifications via SMTP with circuit breaker and retry support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final DeadLetterQueueService dlqService;
    private final AlertNotificationRepository notificationRepository;

    @Value("${app.notifications.email.from:noreply@stacksmonitoring.com}")
    private String fromEmail;

    @Value("${app.notifications.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Send email notification with circuit breaker and retry support.
     *
     * Circuit breaker opens after 50% failure rate (10 calls window).
     * Retry: 3 attempts with exponential backoff (1s, 2s, 4s).
     * Fallback: Move to dead-letter queue on permanent failure.
     */
    @Override
    @CircuitBreaker(name = "emailNotification", fallbackMethod = "sendFallback")
    @Retry(name = "emailNotification")
    public void send(AlertNotification notification) throws NotificationException {
        if (!emailEnabled) {
            log.warn("Email notifications are disabled. Skipping notification {}", notification.getId());
            throw new NotificationException("Email notifications are disabled");
        }

        String toEmails = notification.getAlertRule().getNotificationEmails();
        if (toEmails == null || toEmails.trim().isEmpty()) {
            throw new NotificationException("No email addresses configured for rule");
        }

        // Track delivery attempt
        Instant firstAttempt = notification.getDeliveryAttemptCount() == 0
            ? Instant.now()
            : notification.getLastDeliveryAttemptAt();

        notification.markAsDelivering();
        notificationRepository.save(notification);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmails.split(","));
            message.setSubject(buildSubject(notification));
            message.setText(notification.getMessage());

            mailSender.send(message);

            // Mark as successfully delivered
            notification.markAsSent();
            notificationRepository.save(notification);

            log.info("Sent email notification {} to {} (attempt {})",
                notification.getId(),
                toEmails,
                notification.getDeliveryAttemptCount() + 1
            );

        } catch (MailException e) {
            // Mark as retrying (will trigger retry via Resilience4j)
            notification.markAsRetrying(e.getMessage());
            notificationRepository.save(notification);

            log.error("Failed to send email notification {} (attempt {}): {}",
                notification.getId(),
                notification.getDeliveryAttemptCount(),
                e.getMessage(),
                e
            );
            throw new NotificationException("Failed to send email", e);
        }
    }

    /**
     * Fallback method called when circuit breaker opens or max retries exceeded.
     * Moves notification to dead-letter queue for manual intervention.
     */
    private void sendFallback(AlertNotification notification, Exception e) {
        log.error("Email notification {} failed permanently. Moving to DLQ. Error: {}",
            notification.getId(),
            e.getMessage()
        );

        try {
            // Determine failure reason
            String failureReason = e.getClass().getSimpleName().contains("CircuitBreaker")
                ? "CIRCUIT_OPEN"
                : "MAX_RETRIES_EXCEEDED";

            // Calculate first attempt time
            Instant firstAttempt = notification.getLastDeliveryAttemptAt() != null
                ? notification.getLastDeliveryAttemptAt()
                : Instant.now();

            // Add to dead-letter queue
            dlqService.addToDeadLetterQueue(
                notification,
                failureReason,
                e,
                notification.getDeliveryAttemptCount(),
                firstAttempt,
                Instant.now()
            );

            // Mark notification as dead-letter
            notification.markAsDeadLetter(e.getMessage());
            notificationRepository.save(notification);

        } catch (Exception dlqError) {
            log.error("Failed to add notification {} to DLQ: {}",
                notification.getId(),
                dlqError.getMessage(),
                dlqError
            );
        }
    }

    @Override
    public boolean supports(AlertNotification notification) {
        return notification.getChannel() == NotificationChannel.EMAIL;
    }

    /**
     * Build email subject line.
     */
    private String buildSubject(AlertNotification notification) {
        return String.format("[%s] %s",
            notification.getAlertRule().getSeverity(),
            notification.getAlertRule().getRuleName()
        );
    }
}

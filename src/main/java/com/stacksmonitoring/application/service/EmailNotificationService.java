package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email notification service using Spring Mail.
 * Sends alert notifications via SMTP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.notifications.email.from:noreply@stacksmonitoring.com}")
    private String fromEmail;

    @Value("${app.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Override
    public void send(AlertNotification notification) throws NotificationException {
        if (!emailEnabled) {
            log.warn("Email notifications are disabled. Skipping notification {}", notification.getId());
            throw new NotificationException("Email notifications are disabled");
        }

        String toEmails = notification.getAlertRule().getNotificationEmails();
        if (toEmails == null || toEmails.trim().isEmpty()) {
            throw new NotificationException("No email addresses configured for rule");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmails.split(","));
            message.setSubject(buildSubject(notification));
            message.setText(notification.getMessage());

            mailSender.send(message);

            log.info("Sent email notification {} to {}", notification.getId(), toEmails);

        } catch (MailException e) {
            log.error("Failed to send email notification {}: {}",
                notification.getId(), e.getMessage(), e);
            throw new NotificationException("Failed to send email", e);
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

package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;

/**
 * Interface for notification delivery services.
 * Implementations handle different notification channels (EMAIL, WEBHOOK).
 */
public interface NotificationService {

    /**
     * Send a notification.
     *
     * @param notification The notification to send
     * @throws NotificationException if sending fails
     */
    void send(AlertNotification notification) throws NotificationException;

    /**
     * Check if this service supports the given notification channel.
     */
    boolean supports(AlertNotification notification);

    /**
     * Exception thrown when notification delivery fails.
     */
    class NotificationException extends Exception {
        public NotificationException(String message) {
            super(message);
        }

        public NotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dispatcher for alert notifications.
 * Coordinates multiple notification services and handles delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final List<NotificationService> notificationServices;
    private final AlertNotificationRepository alertNotificationRepository;

    /**
     * Dispatch a single notification to the appropriate service.
     */
    @Async
    @Transactional
    public void dispatch(AlertNotification notification) {
        log.debug("Dispatching notification {} via channel {}",
            notification.getId(), notification.getChannel());

        // Find supporting service
        NotificationService service = notificationServices.stream()
            .filter(s -> s.supports(notification))
            .findFirst()
            .orElse(null);

        if (service == null) {
            log.error("No notification service found for channel {}",
                notification.getChannel());
            notification.markAsFailed("No service supports channel: " + notification.getChannel());
            alertNotificationRepository.save(notification);
            return;
        }

        // Attempt to send
        try {
            notification.incrementAttemptCount();
            service.send(notification);

            notification.markAsSent();
            alertNotificationRepository.save(notification);

            log.info("Successfully sent notification {} via {}",
                notification.getId(), notification.getChannel());

        } catch (NotificationService.NotificationException e) {
            log.error("Failed to send notification {}: {}",
                notification.getId(), e.getMessage());

            notification.markAsFailed(e.getMessage());
            alertNotificationRepository.save(notification);
        }
    }

    /**
     * Dispatch multiple notifications (batch processing).
     */
    @Async
    public void dispatchBatch(List<AlertNotification> notifications) {
        log.info("Dispatching batch of {} notifications", notifications.size());

        for (AlertNotification notification : notifications) {
            try {
                dispatch(notification);
            } catch (Exception e) {
                log.error("Error dispatching notification {}: {}",
                    notification.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Retry failed notifications that are eligible for retry.
     */
    @Transactional
    public void retryFailedNotifications() {
        List<AlertNotification> pendingRetries =
            alertNotificationRepository.findPendingRetries(NotificationStatus.FAILED);

        if (pendingRetries.isEmpty()) {
            log.debug("No failed notifications to retry");
            return;
        }

        log.info("Retrying {} failed notifications", pendingRetries.size());

        for (AlertNotification notification : pendingRetries) {
            if (notification.shouldRetry()) {
                dispatch(notification);
            }
        }
    }
}

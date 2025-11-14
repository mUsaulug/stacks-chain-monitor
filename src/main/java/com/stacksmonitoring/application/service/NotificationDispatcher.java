package com.stacksmonitoring.application.service;

import com.stacksmonitoring.application.event.NotificationsReadyEvent;
import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.repository.AlertNotificationRepository;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Dispatcher for alert notifications.
 * Coordinates multiple notification services and handles delivery.
 *
 * CRITICAL: Transaction-Bound Event Listener (P0-6)
 * - Listens to NotificationsReadyEvent with @TransactionalEventListener(AFTER_COMMIT)
 * - Notifications dispatched ONLY if database commit succeeds
 * - Zero phantom notifications on rollback
 * - Prevents sending emails/webhooks before data is persisted
 *
 * Flow:
 * 1. ProcessChainhookPayloadUseCase publishes NotificationsReadyEvent (inside @Transactional)
 * 2. Spring delays event processing until AFTER_COMMIT phase
 * 3. If commit succeeds → handleNotificationsReady() called → notifications sent
 * 4. If commit fails/rollback → event listener never called → no phantom notifications
 *
 * Observability (P1 - Micrometer):
 * - notification.dispatched: Counter for each dispatch attempt (tagged by channel and status)
 *
 * Reference: CLAUDE.md P0-6 (AFTER_COMMIT Notification Dispatch), P2-5 (Metrics)
 * Spring Docs: https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final List<NotificationService> notificationServices;
    private final AlertNotificationRepository alertNotificationRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Dispatch a single notification to the appropriate service.
     *
     * Metrics: Increments notification.dispatched counter tagged by channel and status.
     */
    @Async
    @Transactional
    public void dispatch(AlertNotification notification) {
        log.debug("Dispatching notification {} via channel {}",
            notification.getId(), notification.getChannel());

        String channel = notification.getChannel().name();

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

            // P1 Metric: Track failed dispatch (no service found)
            incrementNotificationCounter(channel, "no_service");
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

            // P1 Metric: Track successful dispatch
            incrementNotificationCounter(channel, "success");

        } catch (NotificationService.NotificationException e) {
            log.error("Failed to send notification {}: {}",
                notification.getId(), e.getMessage());

            notification.markAsFailed(e.getMessage());
            alertNotificationRepository.save(notification);

            // P1 Metric: Track failed dispatch
            incrementNotificationCounter(channel, "failure");
        }
    }

    /**
     * Increment notification.dispatched counter with channel and status tags.
     */
    private void incrementNotificationCounter(String channel, String status) {
        Counter.builder("notification.dispatched")
                .description("Total notifications dispatched by channel and status")
                .tag("channel", channel)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Listen for NotificationsReadyEvent and dispatch notifications AFTER commit.
     * This method is called ONLY if the transaction commits successfully.
     *
     * Critical: Prevents phantom notifications
     * - Event published inside @Transactional method
     * - Listener waits for AFTER_COMMIT phase
     * - If commit fails, listener never called
     * - Emails/webhooks sent only if data persisted
     *
     * @param event NotificationsReadyEvent containing list of notifications
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleNotificationsReady(NotificationsReadyEvent event) {
        List<AlertNotification> notifications = event.getNotifications();
        log.info("Transaction committed successfully - dispatching {} notifications",
                notifications.size());

        dispatchBatch(notifications);
    }

    /**
     * Dispatch multiple notifications (batch processing).
     * Can be called directly or via event listener.
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

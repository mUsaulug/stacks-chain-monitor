package com.stacksmonitoring.application.event;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Application event published when alert notifications are ready to be dispatched.
 * This event is published AFTER the transaction commits successfully.
 *
 * Use Case:
 * - ProcessChainhookPayloadUseCase publishes this event after persisting blocks/transactions
 * - NotificationDispatcher listens with @TransactionalEventListener(phase = AFTER_COMMIT)
 * - Notifications are sent only if database commit succeeds
 *
 * Problem Solved:
 * BEFORE: Notifications dispatched inside @Transactional method
 *         - Email/webhook sent before commit completes
 *         - If commit fails, phantom notifications sent to users
 *         - Rollback doesn't undo external side effects
 *
 * AFTER:  Notifications dispatched after commit
 *         - Event published inside transaction
 *         - Listener waits for AFTER_COMMIT phase
 *         - Emails/webhooks sent only if commit succeeds
 *         - Zero phantom notifications on rollback
 *
 * Reference: CLAUDE.md P0-6 (AFTER_COMMIT Notification Dispatch)
 * Spring Docs: Transaction-bound Events
 * https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
 */
@Getter
public class NotificationsReadyEvent extends ApplicationEvent {

    private final List<AlertNotification> notifications;

    public NotificationsReadyEvent(Object source, List<AlertNotification> notifications) {
        super(source);
        this.notifications = notifications;
    }

    /**
     * Get count of notifications to be dispatched.
     */
    public int getNotificationCount() {
        return notifications != null ? notifications.size() : 0;
    }
}

package com.stacksmonitoring.domain.valueobject;

/**
 * Notification delivery lifecycle states.
 * Tracks the delivery status from initial queuing to final delivery or permanent failure.
 */
public enum NotificationDeliveryStatus {
    /**
     * Notification has been created but not yet sent.
     */
    PENDING,

    /**
     * Notification is currently being delivered.
     */
    DELIVERING,

    /**
     * Notification has been successfully delivered.
     */
    DELIVERED,

    /**
     * Notification delivery failed with a transient error and will be retried.
     */
    RETRYING,

    /**
     * Notification delivery permanently failed and has been moved to dead-letter queue.
     */
    DEAD_LETTER
}

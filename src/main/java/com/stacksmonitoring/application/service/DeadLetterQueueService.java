package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.model.monitoring.NotificationDeadLetterQueue;
import com.stacksmonitoring.domain.repository.NotificationDeadLetterQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

/**
 * Service for managing the notification dead-letter queue.
 * Handles permanently failed notifications that require manual intervention.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueService {

    private final NotificationDeadLetterQueueRepository dlqRepository;

    /**
     * Add a failed notification to the dead-letter queue.
     *
     * @param notification the notification that failed
     * @param failureReason the reason for failure (e.g., CIRCUIT_OPEN, MAX_RETRIES_EXCEEDED)
     * @param error the exception that caused the failure
     * @param attemptCount the number of delivery attempts made
     * @param firstAttemptAt the timestamp of the first delivery attempt
     * @param lastAttemptAt the timestamp of the last delivery attempt
     */
    @Transactional
    public void addToDeadLetterQueue(
        AlertNotification notification,
        String failureReason,
        Exception error,
        Integer attemptCount,
        Instant firstAttemptAt,
        Instant lastAttemptAt
    ) {
        try {
            // Extract error details
            String errorMessage = error.getMessage();
            String stackTrace = getStackTraceAsString(error);

            // Create DLQ entry
            NotificationDeadLetterQueue dlq = NotificationDeadLetterQueue.fromNotification(
                notification,
                failureReason,
                errorMessage,
                stackTrace,
                attemptCount,
                firstAttemptAt,
                lastAttemptAt
            );

            // Save to database
            dlqRepository.save(dlq);

            log.error("Notification {} added to DLQ. Reason: {}, Attempts: {}, Error: {}",
                notification.getId(),
                failureReason,
                attemptCount,
                errorMessage
            );

        } catch (Exception e) {
            log.error("Failed to add notification {} to DLQ: {}",
                notification.getId(),
                e.getMessage(),
                e
            );
        }
    }

    /**
     * Mark a DLQ entry as processed after manual intervention.
     *
     * @param dlqId the ID of the DLQ entry
     * @param processedBy the user or system that processed the entry
     * @param resolutionNotes notes about how the issue was resolved
     */
    @Transactional
    public void markAsProcessed(Long dlqId, String processedBy, String resolutionNotes) {
        dlqRepository.findById(dlqId).ifPresent(dlq -> {
            dlq.markAsProcessed(processedBy, resolutionNotes);
            dlqRepository.save(dlq);

            log.info("DLQ entry {} marked as processed by {}. Resolution: {}",
                dlqId,
                processedBy,
                resolutionNotes
            );
        });
    }

    /**
     * Get count of unprocessed DLQ entries.
     */
    @Transactional(readOnly = true)
    public long getUnprocessedCount() {
        return dlqRepository.countByProcessedFalse();
    }

    /**
     * Convert exception stack trace to string.
     */
    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}

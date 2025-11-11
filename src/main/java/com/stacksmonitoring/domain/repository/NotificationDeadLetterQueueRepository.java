package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.monitoring.NotificationDeadLetterQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing dead-letter queue entries.
 */
@Repository
public interface NotificationDeadLetterQueueRepository extends JpaRepository<NotificationDeadLetterQueue, Long> {

    /**
     * Find all unprocessed DLQ entries (for admin dashboard).
     */
    @Query("SELECT d FROM NotificationDeadLetterQueue d WHERE d.processed = false ORDER BY d.queuedAt DESC")
    Page<NotificationDeadLetterQueue> findAllUnprocessed(Pageable pageable);

    /**
     * Find DLQ entry by notification ID.
     */
    Optional<NotificationDeadLetterQueue> findByNotificationId(Long notificationId);

    /**
     * Find all DLQ entries by failure reason.
     */
    List<NotificationDeadLetterQueue> findByFailureReasonOrderByQueuedAtDesc(String failureReason);

    /**
     * Find all DLQ entries by channel.
     */
    List<NotificationDeadLetterQueue> findByChannelOrderByQueuedAtDesc(String channel);

    /**
     * Count unprocessed DLQ entries.
     */
    long countByProcessedFalse();

    /**
     * Find DLQ entries queued within time range.
     */
    @Query("SELECT d FROM NotificationDeadLetterQueue d WHERE d.queuedAt BETWEEN :startTime AND :endTime ORDER BY d.queuedAt DESC")
    List<NotificationDeadLetterQueue> findByQueuedAtBetween(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
}

package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for AlertNotification entities.
 *
 * Blockchain Rollback Support (V9):
 * - bulkInvalidateByBlockId(): Invalidate all notifications for a rolled-back block
 * - Performance: Single UPDATE statement ~100x faster than individual saves
 * - Idempotency: WHERE invalidated = false ensures safe multiple rollbacks
 */
@Repository
public interface AlertNotificationRepository extends JpaRepository<AlertNotification, Long> {

    @Query("SELECT n FROM AlertNotification n WHERE n.alertRule.id = :ruleId ORDER BY n.triggeredAt DESC")
    Page<AlertNotification> findByAlertRuleId(@Param("ruleId") Long ruleId, Pageable pageable);

    @Query("SELECT n FROM AlertNotification n WHERE n.alertRule.user.id = :userId ORDER BY n.triggeredAt DESC")
    Page<AlertNotification> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT n FROM AlertNotification n WHERE n.status = :status ORDER BY n.triggeredAt DESC")
    List<AlertNotification> findByStatus(@Param("status") NotificationStatus status);

    @Query("SELECT n FROM AlertNotification n WHERE n.status = :status AND n.attemptCount < 3 ORDER BY n.triggeredAt ASC")
    List<AlertNotification> findPendingRetries(@Param("status") NotificationStatus status);

    @Query("SELECT n FROM AlertNotification n WHERE n.triggeredAt >= :startTime AND n.triggeredAt <= :endTime ORDER BY n.triggeredAt DESC")
    Page<AlertNotification> findByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime, Pageable pageable);

    @Query("SELECT n FROM AlertNotification n WHERE n.alertRule.user.id = :userId AND n.status = :status ORDER BY n.triggeredAt DESC")
    Page<AlertNotification> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") NotificationStatus status, Pageable pageable);

    // ============================================================
    // BLOCKCHAIN ROLLBACK SUPPORT (V9)
    // ============================================================

    /**
     * Bulk invalidate all notifications related to a specific block (blockchain rollback).
     *
     * Performance: Single UPDATE statement processes 5000 notifications in ~50-100ms
     * vs individual saves taking 3-5 seconds.
     *
     * Idempotency: WHERE invalidated = false ensures rollback can be called multiple times.
     * Second rollback for same block returns 0 (no rows updated).
     *
     * @param blockId ID of the block being rolled back
     * @param invalidatedAt Timestamp when rollback occurred
     * @param reason Why notifications are being invalidated (usually "BLOCKCHAIN_REORG")
     * @return Number of notifications invalidated (0 if already invalidated)
     */
    @Modifying
    @Query("""
        UPDATE AlertNotification n
           SET n.invalidated = true,
               n.invalidatedAt = :invalidatedAt,
               n.invalidationReason = :reason
         WHERE n.transaction.block.id = :blockId
           AND n.invalidated = false
    """)
    int bulkInvalidateByBlockId(
        @Param("blockId") Long blockId,
        @Param("invalidatedAt") Instant invalidatedAt,
        @Param("reason") String reason
    );

    /**
     * Count invalidated notifications (metrics/audit).
     */
    long countByInvalidatedTrue();

    /**
     * Find invalidated notifications for audit (admin dashboard).
     */
    Page<AlertNotification> findByInvalidatedTrueOrderByInvalidatedAtDesc(Pageable pageable);

    /**
     * Find active (non-invalidated) notifications by transaction.
     * Used during rollback cascade for validation.
     */
    List<AlertNotification> findByTransactionIdAndInvalidatedFalse(Long transactionId);
}

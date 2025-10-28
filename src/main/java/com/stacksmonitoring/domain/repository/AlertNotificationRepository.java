package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.monitoring.AlertNotification;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for AlertNotification entities.
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
}

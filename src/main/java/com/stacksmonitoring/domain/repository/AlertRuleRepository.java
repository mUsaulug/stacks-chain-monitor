package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for AlertRule entities.
 * Critical for high-performance alert matching.
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    @Query("SELECT r FROM AlertRule r WHERE r.user.id = :userId ORDER BY r.createdAt DESC")
    List<AlertRule> findByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM AlertRule r WHERE r.user.id = :userId AND r.isActive = true")
    List<AlertRule> findActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM AlertRule r WHERE r.isActive = true")
    List<AlertRule> findAllActive();

    @Query("SELECT r FROM AlertRule r WHERE r.ruleType = :ruleType AND r.isActive = true")
    List<AlertRule> findActiveByRuleType(@Param("ruleType") AlertRuleType ruleType);

    @Query("SELECT r FROM AlertRule r WHERE r.monitoredContract.id = :contractId AND r.isActive = true")
    List<AlertRule> findActiveByMonitoredContractId(@Param("contractId") Long contractId);

    /**
     * Critical query for cache-indexed alert matching.
     * Used to build cache maps: eventType -> contractId -> rules
     */
    @Query("SELECT r FROM AlertRule r WHERE r.isActive = true AND r.ruleType = :ruleType")
    List<AlertRule> findForAlertMatching(@Param("ruleType") AlertRuleType ruleType);
}

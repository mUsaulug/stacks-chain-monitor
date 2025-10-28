package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.monitoring.MonitoredContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MonitoredContract entities.
 */
@Repository
public interface MonitoredContractRepository extends JpaRepository<MonitoredContract, Long> {

    @Query("SELECT m FROM MonitoredContract m WHERE m.user.id = :userId AND m.isActive = true ORDER BY m.createdAt DESC")
    List<MonitoredContract> findActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM MonitoredContract m WHERE m.user.id = :userId ORDER BY m.createdAt DESC")
    List<MonitoredContract> findByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM MonitoredContract m WHERE m.contractIdentifier = :contractIdentifier")
    List<MonitoredContract> findByContractIdentifier(@Param("contractIdentifier") String contractIdentifier);

    @Query("SELECT m FROM MonitoredContract m WHERE m.user.id = :userId AND m.contractIdentifier = :contractIdentifier")
    Optional<MonitoredContract> findByUserIdAndContractIdentifier(@Param("userId") Long userId, @Param("contractIdentifier") String contractIdentifier);

    @Query("SELECT m FROM MonitoredContract m WHERE m.isActive = true")
    List<MonitoredContract> findAllActive();
}

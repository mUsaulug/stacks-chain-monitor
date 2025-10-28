package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.blockchain.TransactionEvent;
import com.stacksmonitoring.domain.valueobject.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TransactionEvent entities.
 * This is critical for alert matching - 80% of alert rules query events.
 */
@Repository
public interface TransactionEventRepository extends JpaRepository<TransactionEvent, Long> {

    @Query("SELECT e FROM TransactionEvent e WHERE e.transaction.id = :transactionId ORDER BY e.eventIndex ASC")
    List<TransactionEvent> findByTransactionId(@Param("transactionId") Long transactionId);

    @Query("SELECT e FROM TransactionEvent e WHERE e.eventType = :eventType ORDER BY e.createdAt DESC")
    Page<TransactionEvent> findByEventType(@Param("eventType") EventType eventType, Pageable pageable);

    @Query("SELECT e FROM TransactionEvent e WHERE e.eventType = :eventType AND e.contractIdentifier = :contractIdentifier ORDER BY e.createdAt DESC")
    Page<TransactionEvent> findByEventTypeAndContractIdentifier(
        @Param("eventType") EventType eventType,
        @Param("contractIdentifier") String contractIdentifier,
        Pageable pageable
    );

    @Query("SELECT e FROM TransactionEvent e WHERE e.contractIdentifier = :contractIdentifier ORDER BY e.createdAt DESC")
    Page<TransactionEvent> findByContractIdentifier(@Param("contractIdentifier") String contractIdentifier, Pageable pageable);

    /**
     * Critical query for alert matching optimization.
     * Returns events by type and contract for cache-indexed rule lookup.
     */
    @Query("SELECT e FROM TransactionEvent e WHERE e.eventType = :eventType AND e.contractIdentifier = :contractIdentifier")
    List<TransactionEvent> findForAlertMatching(@Param("eventType") EventType eventType, @Param("contractIdentifier") String contractIdentifier);
}

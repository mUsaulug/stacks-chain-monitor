package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.valueobject.TransactionType;
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
 * Repository for StacksTransaction entities.
 */
@Repository
public interface StacksTransactionRepository extends JpaRepository<StacksTransaction, Long> {

    Optional<StacksTransaction> findByTxId(String txId);

    boolean existsByTxId(String txId);

    @Query("SELECT t FROM StacksTransaction t WHERE t.sender = :sender AND t.deleted = false ORDER BY t.createdAt DESC")
    Page<StacksTransaction> findBySender(@Param("sender") String sender, Pageable pageable);

    @Query("SELECT t FROM StacksTransaction t WHERE t.success = :success AND t.deleted = false ORDER BY t.createdAt DESC")
    Page<StacksTransaction> findBySuccess(@Param("success") Boolean success, Pageable pageable);

    @Query("SELECT t FROM StacksTransaction t WHERE t.txType = :txType AND t.deleted = false ORDER BY t.createdAt DESC")
    Page<StacksTransaction> findByTxType(@Param("txType") TransactionType txType, Pageable pageable);

    @Query("SELECT t FROM StacksTransaction t WHERE t.block.id = :blockId AND t.deleted = false ORDER BY t.txIndex ASC")
    List<StacksTransaction> findByBlockId(@Param("blockId") Long blockId);

    @Query("SELECT t FROM StacksTransaction t WHERE t.createdAt >= :startTime AND t.createdAt <= :endTime AND t.deleted = false ORDER BY t.createdAt DESC")
    Page<StacksTransaction> findByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime, Pageable pageable);
}

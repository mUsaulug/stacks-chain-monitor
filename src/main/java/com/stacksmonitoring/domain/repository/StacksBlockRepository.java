package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for StacksBlock entities.
 */
@Repository
public interface StacksBlockRepository extends JpaRepository<StacksBlock, Long> {

    Optional<StacksBlock> findByBlockHash(String blockHash);

    Optional<StacksBlock> findByBlockHeight(Long blockHeight);

    boolean existsByBlockHash(String blockHash);

    boolean existsByBlockHeight(Long blockHeight);

    @Query("SELECT b FROM StacksBlock b WHERE b.deleted = false ORDER BY b.blockHeight DESC")
    List<StacksBlock> findActiveBlocks();

    @Query("SELECT b FROM StacksBlock b WHERE b.timestamp >= :startTime AND b.timestamp <= :endTime AND b.deleted = false ORDER BY b.blockHeight ASC")
    List<StacksBlock> findBlocksByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT MAX(b.blockHeight) FROM StacksBlock b WHERE b.deleted = false")
    Optional<Long> findMaxBlockHeight();
}

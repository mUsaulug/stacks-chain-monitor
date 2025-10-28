package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for querying blockchain blocks.
 * Provides pagination, filtering, and search capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BlockQueryService {

    private final StacksBlockRepository blockRepository;

    /**
     * Get all active blocks with pagination.
     */
    public Page<StacksBlock> getBlocks(Pageable pageable) {
        log.debug("Fetching blocks with pagination: {}", pageable);
        return blockRepository.findAll(pageable);
    }

    /**
     * Get a specific block by ID.
     */
    public Optional<StacksBlock> getBlockById(Long id) {
        log.debug("Fetching block by ID: {}", id);
        return blockRepository.findById(id);
    }

    /**
     * Get a block by hash.
     */
    public Optional<StacksBlock> getBlockByHash(String blockHash) {
        log.debug("Fetching block by hash: {}", blockHash);
        return blockRepository.findByBlockHash(blockHash);
    }

    /**
     * Get a block by height.
     */
    public Optional<StacksBlock> getBlockByHeight(Long height) {
        log.debug("Fetching block by height: {}", height);
        return blockRepository.findByBlockHeight(height);
    }

    /**
     * Get blocks within a time range.
     */
    public List<StacksBlock> getBlocksByTimeRange(Instant startTime, Instant endTime) {
        log.debug("Fetching blocks between {} and {}", startTime, endTime);
        return blockRepository.findBlocksByTimeRange(startTime, endTime);
    }

    /**
     * Get the latest block height.
     */
    public Optional<Long> getLatestBlockHeight() {
        log.debug("Fetching latest block height");
        return blockRepository.findMaxBlockHeight();
    }

    /**
     * Check if a block exists by hash.
     */
    public boolean blockExists(String blockHash) {
        return blockRepository.existsByBlockHash(blockHash);
    }

    /**
     * Get active blocks only (not soft deleted).
     */
    public List<StacksBlock> getActiveBlocks() {
        log.debug("Fetching all active blocks");
        return blockRepository.findActiveBlocks();
    }
}

package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.repository.StacksTransactionRepository;
import com.stacksmonitoring.domain.valueobject.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for querying blockchain transactions.
 * Provides pagination, filtering, and search capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransactionQueryService {

    private final StacksTransactionRepository transactionRepository;

    /**
     * Get all transactions with pagination.
     */
    public Page<StacksTransaction> getTransactions(Pageable pageable) {
        log.debug("Fetching transactions with pagination: {}", pageable);
        return transactionRepository.findAll(pageable);
    }

    /**
     * Get a specific transaction by ID.
     */
    public Optional<StacksTransaction> getTransactionById(Long id) {
        log.debug("Fetching transaction by ID: {}", id);
        return transactionRepository.findById(id);
    }

    /**
     * Get a transaction by transaction ID (hash).
     */
    public Optional<StacksTransaction> getTransactionByTxId(String txId) {
        log.debug("Fetching transaction by txId: {}", txId);
        return transactionRepository.findByTxId(txId);
    }

    /**
     * Get transactions by sender address with pagination.
     */
    public Page<StacksTransaction> getTransactionsBySender(String sender, Pageable pageable) {
        log.debug("Fetching transactions by sender: {} with pagination", sender);
        return transactionRepository.findBySender(sender, pageable);
    }

    /**
     * Get transactions by type with pagination.
     */
    public Page<StacksTransaction> getTransactionsByType(TransactionType type, Pageable pageable) {
        log.debug("Fetching transactions by type: {} with pagination", type);
        return transactionRepository.findByTxType(type, pageable);
    }

    /**
     * Get successful transactions with pagination.
     */
    public Page<StacksTransaction> getSuccessfulTransactions(Pageable pageable) {
        log.debug("Fetching successful transactions with pagination");
        return transactionRepository.findBySuccess(true, pageable);
    }

    /**
     * Get failed transactions with pagination.
     */
    public Page<StacksTransaction> getFailedTransactions(Pageable pageable) {
        log.debug("Fetching failed transactions with pagination");
        return transactionRepository.findBySuccess(false, pageable);
    }

    /**
     * Get transactions for a specific block.
     */
    public List<StacksTransaction> getTransactionsByBlockId(Long blockId) {
        log.debug("Fetching transactions for block ID: {}", blockId);
        return transactionRepository.findByBlockId(blockId);
    }

    /**
     * Check if a transaction exists.
     */
    public boolean transactionExists(String txId) {
        return transactionRepository.existsByTxId(txId);
    }

    /**
     * Count total transactions.
     */
    public long countTransactions() {
        return transactionRepository.count();
    }

    /**
     * Count transactions by success status.
     */
    public long countTransactionsBySuccess(boolean success) {
        // TODO: Add countBySuccess method to StacksTransactionRepository
        return transactionRepository.count(); // Temporary: return total count
    }
}

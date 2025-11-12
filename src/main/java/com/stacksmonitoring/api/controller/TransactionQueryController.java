package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.application.service.TransactionQueryService;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.valueobject.TransactionType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * REST controller for querying blockchain transactions.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionQueryController {

    private final TransactionQueryService transactionQueryService;

    /**
     * Get all transactions with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        log.debug("Fetching transactions: page={}, size={}, sortBy={}, direction={}",
            page, size, sortBy, direction);

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<StacksTransaction> transactions = transactionQueryService.getTransactions(pageable);

        Page<TransactionResponse> response = transactions.map(TransactionResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific transaction by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable Long id) {
        log.debug("Fetching transaction by ID: {}", id);

        return transactionQueryService.getTransactionById(id)
                .map(TransactionResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a transaction by transaction ID (hash).
     */
    @GetMapping("/txid/{txId}")
    public ResponseEntity<TransactionResponse> getTransactionByTxId(@PathVariable String txId) {
        log.debug("Fetching transaction by txId: {}", txId);

        return transactionQueryService.getTransactionByTxId(txId)
                .map(TransactionResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get transactions by sender address.
     */
    @GetMapping("/sender/{sender}")
    public ResponseEntity<Page<TransactionResponse>> getTransactionsBySender(
            @PathVariable String sender,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Fetching transactions by sender: {}", sender);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StacksTransaction> transactions = transactionQueryService.getTransactionsBySender(sender, pageable);

        Page<TransactionResponse> response = transactions.map(TransactionResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Get transactions by type.
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<Page<TransactionResponse>> getTransactionsByType(
            @PathVariable TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Fetching transactions by type: {}", type);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StacksTransaction> transactions = transactionQueryService.getTransactionsByType(type, pageable);

        Page<TransactionResponse> response = transactions.map(TransactionResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Get successful transactions.
     */
    @GetMapping("/successful")
    public ResponseEntity<Page<TransactionResponse>> getSuccessfulTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Fetching successful transactions");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StacksTransaction> transactions = transactionQueryService.getSuccessfulTransactions(pageable);

        Page<TransactionResponse> response = transactions.map(TransactionResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Get failed transactions.
     */
    @GetMapping("/failed")
    public ResponseEntity<Page<TransactionResponse>> getFailedTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Fetching failed transactions");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StacksTransaction> transactions = transactionQueryService.getFailedTransactions(pageable);

        Page<TransactionResponse> response = transactions.map(TransactionResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Transaction response DTO.
     */
    @Data
    public static class TransactionResponse {
        private Long id;
        private String txId;
        private Long blockId;
        private Long blockHeight;
        private String sender;
        private String sponsorAddress;
        private String txType;
        private Boolean success;
        private Integer txIndex;
        private Long nonce;
        private BigDecimal feeRate;
        private Long executionCostReadCount;
        private Long executionCostReadLength;
        private Long executionCostRuntime;
        private Long executionCostWriteCount;
        private Long executionCostWriteLength;
        private Boolean deleted;

        public static TransactionResponse fromEntity(StacksTransaction transaction) {
            TransactionResponse response = new TransactionResponse();
            response.setId(transaction.getId());
            response.setTxId(transaction.getTxId());
            response.setBlockId(transaction.getBlock().getId());
            response.setBlockHeight(transaction.getBlock().getBlockHeight());
            response.setSender(transaction.getSender());
            response.setSponsorAddress(transaction.getSponsorAddress());
            response.setTxType(transaction.getTxType() != null ? transaction.getTxType().toString() : null);
            response.setSuccess(transaction.getSuccess());
            response.setTxIndex(transaction.getTxIndex());
            response.setNonce(transaction.getNonce());
            // response.setFeeRate(transaction.getFeeRate()); // Field removed - use fee instead
            response.setExecutionCostReadCount(transaction.getExecutionCostReadCount());
            response.setExecutionCostReadLength(transaction.getExecutionCostReadLength());
            response.setExecutionCostRuntime(transaction.getExecutionCostRuntime());
            response.setExecutionCostWriteCount(transaction.getExecutionCostWriteCount());
            response.setExecutionCostWriteLength(transaction.getExecutionCostWriteLength());
            response.setDeleted(transaction.getDeleted());
            return response;
        }
    }
}

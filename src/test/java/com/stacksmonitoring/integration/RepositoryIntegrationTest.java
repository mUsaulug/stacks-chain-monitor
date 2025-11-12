package com.stacksmonitoring.integration;

import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import com.stacksmonitoring.domain.repository.StacksTransactionRepository;
import com.stacksmonitoring.domain.valueobject.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for repositories using TestContainers.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
        .withDatabaseName("stacks_monitor_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private StacksBlockRepository blockRepository;

    @Autowired
    private StacksTransactionRepository transactionRepository;

    @Test
    void shouldSaveAndRetrieveBlock() {
        // Given
        StacksBlock block = new StacksBlock();
        block.setBlockHeight(1000L);
        block.setBlockHash("0x123abc");
        block.setIndexBlockHash("0x456def");
        block.setTimestamp(Instant.now());
        block.setTransactionCount(0);
        block.setDeleted(false);

        // When
        StacksBlock saved = blockRepository.save(block);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(blockRepository.findByBlockHash("0x123abc")).isPresent();
        assertThat(blockRepository.findByBlockHeight(1000L)).isPresent();
    }

    @Test
    void shouldSaveBlockWithTransactions() {
        // Given
        StacksBlock block = new StacksBlock();
        block.setBlockHeight(2000L);
        block.setBlockHash("0xblock2000");
        block.setIndexBlockHash("0xindex2000");
        block.setTimestamp(Instant.now());
        block.setTransactionCount(0);
        block.setDeleted(false);

        StacksTransaction tx = new StacksTransaction();
        tx.setTxId("0xtx123");
        tx.setSender("SP123");
        tx.setTxType(TransactionType.CONTRACT_CALL);
        tx.setSuccess(true);
        tx.setTxIndex(0);
        tx.setNonce(1L);
        tx.setDeleted(false);

        block.addTransaction(tx);

        // When
        StacksBlock saved = blockRepository.save(block);

        // Then
        assertThat(saved.getTransactions()).hasSize(1);
        assertThat(saved.getTransactionCount()).isEqualTo(1);

        StacksTransaction savedTx = transactionRepository.findByTxId("0xtx123").orElseThrow();
        assertThat(savedTx.getBlock().getBlockHeight()).isEqualTo(2000L);
    }

    @Test
    void shouldFindTransactionsBySender() {
        // Given
        StacksBlock block = createBlock(3000L);
        StacksTransaction tx1 = createTransaction("0xtx1", "SP_SENDER_1");
        StacksTransaction tx2 = createTransaction("0xtx2", "SP_SENDER_1");
        StacksTransaction tx3 = createTransaction("0xtx3", "SP_SENDER_2");

        block.addTransaction(tx1);
        block.addTransaction(tx2);
        block.addTransaction(tx3);

        blockRepository.save(block);

        // When
        var results = transactionRepository.findBySender("SP_SENDER_1",
            org.springframework.data.domain.Pageable.unpaged());

        // Then
        assertThat(results.getContent()).hasSize(2);
    }

    private StacksBlock createBlock(Long height) {
        StacksBlock block = new StacksBlock();
        block.setBlockHeight(height);
        block.setBlockHash("0xblock" + height);
        block.setIndexBlockHash("0xindex" + height);
        block.setTimestamp(Instant.now());
        block.setTransactionCount(0);
        block.setDeleted(false);
        return block;
    }

    private StacksTransaction createTransaction(String txId, String sender) {
        StacksTransaction tx = new StacksTransaction();
        tx.setTxId(txId);
        tx.setSender(sender);
        tx.setTxType(TransactionType.CONTRACT_CALL);
        tx.setSuccess(true);
        tx.setTxIndex(0);
        tx.setNonce(1L);
        tx.setDeleted(false);
        return tx;
    }
}

package com.stacksmonitoring.domain;

import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.valueobject.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StacksBlock entity.
 */
class StacksBlockTest {

    private StacksBlock block;

    @BeforeEach
    void setUp() {
        block = new StacksBlock();
        block.setBlockHeight(1000L);
        block.setBlockHash("0x123abc");
        block.setIndexBlockHash("0x456def");
        block.setTimestamp(Instant.now());
        block.setTransactionCount(0);
    }

    @Test
    void shouldAddTransactionToBlock() {
        // Given
        StacksTransaction transaction = new StacksTransaction();
        transaction.setTxId("0xtx123");
        transaction.setSender("SP123");
        transaction.setTxType(TransactionType.CONTRACT_CALL);
        transaction.setSuccess(true);
        transaction.setNonce(1L);

        // When
        block.addTransaction(transaction);

        // Then
        assertThat(block.getTransactions()).hasSize(1);
        assertThat(block.getTransactionCount()).isEqualTo(1);
        assertThat(transaction.getBlock()).isEqualTo(block);
    }

    @Test
    void shouldMarkBlockAsDeleted() {
        // When
        block.markAsDeleted();

        // Then
        assertThat(block.getDeleted()).isTrue();
        assertThat(block.getDeletedAt()).isNotNull();
    }

    @Test
    void shouldUseBusinessKeyForEquals() {
        // Given
        StacksBlock block1 = new StacksBlock();
        block1.setBlockHash("0x123");

        StacksBlock block2 = new StacksBlock();
        block2.setBlockHash("0x123");

        StacksBlock block3 = new StacksBlock();
        block3.setBlockHash("0x456");

        // Then
        assertThat(block1).isEqualTo(block2);
        assertThat(block1).isNotEqualTo(block3);
    }

    @Test
    void shouldHaveConsistentHashCode() {
        // Given
        StacksBlock block1 = new StacksBlock();
        StacksBlock block2 = new StacksBlock();

        // Then
        assertThat(block1.hashCode()).isEqualTo(block2.hashCode());
    }
}

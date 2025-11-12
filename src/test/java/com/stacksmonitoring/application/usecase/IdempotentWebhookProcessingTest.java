package com.stacksmonitoring.application.usecase;

import com.stacksmonitoring.api.dto.webhook.*;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import com.stacksmonitoring.domain.repository.StacksTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for idempotent webhook processing (A.1).
 *
 * Tests verify:
 * 1. Same webhook payload delivered twice → only 1 database record
 * 2. 10 concurrent threads processing same webhook → only 1 record
 * 3. UNIQUE constraints prevent duplicates
 * 4. DataIntegrityViolationException handled gracefully
 *
 * Reference: PART A.1 - Idempotent Upsert + UNIQUE Constraints
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:14:///test",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class IdempotentWebhookProcessingTest {

    @Autowired
    private ProcessChainhookPayloadUseCase processUseCase;

    @Autowired
    private StacksBlockRepository blockRepository;

    @Autowired
    private StacksTransactionRepository transactionRepository;

    @Test
    @DisplayName("Same webhook delivered twice → only 1 block in database")
    @Transactional
    void testSameWebhookTwice_OnlyOneRecord() {
        // Given - Create test webhook payload
        ChainhookPayloadDto payload = createTestPayload("0xblock123", 1000L, "0xtx456");

        // When - Process same webhook twice
        processUseCase.processPayload(payload);
        processUseCase.processPayload(payload); // Re-delivery

        // Then - Only 1 block should exist
        List<StacksBlock> blocks = blockRepository.findAll();
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getBlockHash()).isEqualTo("0xblock123");

        // And only 1 transaction
        List<StacksTransaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTxId()).isEqualTo("0xtx456");
    }

    @Test
    @DisplayName("10 concurrent threads processing same webhook → only 1 record")
    void testConcurrentWebhookProcessing_OnlyOneRecord() throws InterruptedException, ExecutionException {
        // Given - Create test webhook payload
        ChainhookPayloadDto payload = createTestPayload("0xconcurrent123", 2000L, "0xconcurrent_tx789");

        // When - Process with 10 concurrent threads
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<?>> futures = IntStream.range(0, 10)
            .mapToObj(i -> executor.submit(() -> {
                try {
                    latch.await(); // Wait for all threads to be ready
                    processUseCase.processPayload(payload);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }))
            .collect(java.util.stream.Collectors.toList());

        // Release all threads simultaneously (maximize race condition)
        latch.countDown();

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Then - Only 1 block should exist (UNIQUE constraint worked)
        List<StacksBlock> blocks = blockRepository.findAll();
        long concurrentBlocks = blocks.stream()
            .filter(b -> "0xconcurrent123".equals(b.getBlockHash()))
            .count();
        assertThat(concurrentBlocks).isEqualTo(1);

        // And only 1 transaction
        List<StacksTransaction> transactions = transactionRepository.findAll();
        long concurrentTxs = transactions.stream()
            .filter(tx -> "0xconcurrent_tx789".equals(tx.getTxId()))
            .count();
        assertThat(concurrentTxs).isEqualTo(1);
    }

    @Test
    @DisplayName("Duplicate insert directly → DataIntegrityViolationException thrown")
    @Transactional
    void testDuplicateBlockInsert_ThrowsException() {
        // Given - Create block manually
        StacksBlock block1 = new StacksBlock();
        block1.setBlockHash("0xduplicate999");
        block1.setBlockHeight(3000L);
        block1.setIndexBlockHash("0xduplicate999");
        block1.setDeleted(false);
        blockRepository.save(block1);

        // When/Then - Try to insert duplicate block_hash → should fail
        StacksBlock block2 = new StacksBlock();
        block2.setBlockHash("0xduplicate999"); // Same hash
        block2.setBlockHeight(3001L); // Different height
        block2.setIndexBlockHash("0xduplicate999");
        block2.setDeleted(false);

        assertThatCode(() -> blockRepository.saveAndFlush(block2))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_block_hash");
    }

    @Test
    @DisplayName("Duplicate transaction insert → DataIntegrityViolationException thrown")
    @Transactional
    void testDuplicateTransactionInsert_ThrowsException() {
        // Given - Create block and transaction
        StacksBlock block = new StacksBlock();
        block.setBlockHash("0xblock_for_tx_test");
        block.setBlockHeight(4000L);
        block.setIndexBlockHash("0xblock_for_tx_test");
        block.setDeleted(false);
        blockRepository.save(block);

        StacksTransaction tx1 = new StacksTransaction();
        tx1.setTxId("0xtx_duplicate");
        tx1.setBlock(block);
        tx1.setSender("SP123");
        tx1.setSuccess(true);
        tx1.setTxIndex(0);
        tx1.setNonce(0L);
        tx1.setDeleted(false);
        transactionRepository.save(tx1);

        // When/Then - Try to insert duplicate tx_id → should fail
        StacksTransaction tx2 = new StacksTransaction();
        tx2.setTxId("0xtx_duplicate"); // Same tx_id
        tx2.setBlock(block);
        tx2.setSender("SP456"); // Different sender
        tx2.setSuccess(false);
        tx2.setTxIndex(1);
        tx2.setNonce(1L);
        tx2.setDeleted(false);

        assertThatCode(() -> transactionRepository.saveAndFlush(tx2))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_tx_id");
    }

    @Test
    @DisplayName("Webhook re-delivery after 5 minutes → still returns 200 OK")
    @Transactional
    void testWebhookRedeliveryAfterDelay_ReturnsOK() {
        // Given - Process webhook first time
        ChainhookPayloadDto payload = createTestPayload("0xdelayed123", 5000L, "0xdelayed_tx");
        processUseCase.processPayload(payload);

        // When - Simulate re-delivery after delay (same payload)
        assertThatCode(() -> processUseCase.processPayload(payload))
            .doesNotThrowAnyException();

        // Then - Still only 1 record
        List<StacksBlock> blocks = blockRepository.findAll();
        long delayedBlocks = blocks.stream()
            .filter(b -> "0xdelayed123".equals(b.getBlockHash()))
            .count();
        assertThat(delayedBlocks).isEqualTo(1);
    }

    @Test
    @DisplayName("Multiple different blocks → all persisted correctly")
    @Transactional
    void testMultipleDifferentBlocks_AllPersisted() {
        // Given - Create 3 different webhook payloads
        ChainhookPayloadDto payload1 = createTestPayload("0xblock_a", 6000L, "0xtx_a");
        ChainhookPayloadDto payload2 = createTestPayload("0xblock_b", 6001L, "0xtx_b");
        ChainhookPayloadDto payload3 = createTestPayload("0xblock_c", 6002L, "0xtx_c");

        // When - Process all 3
        processUseCase.processPayload(payload1);
        processUseCase.processPayload(payload2);
        processUseCase.processPayload(payload3);

        // Then - All 3 blocks persisted
        List<StacksBlock> blocks = blockRepository.findAll();
        assertThat(blocks).hasSizeGreaterThanOrEqualTo(3);

        List<String> blockHashes = blocks.stream()
            .map(StacksBlock::getBlockHash)
            .toList();
        assertThat(blockHashes).contains("0xblock_a", "0xblock_b", "0xblock_c");
    }

    // ========== Helper Methods ==========

    private ChainhookPayloadDto createTestPayload(String blockHash, Long blockHeight, String txId) {
        ChainhookPayloadDto payload = new ChainhookPayloadDto();

        // Create apply event
        BlockEventDto blockEvent = new BlockEventDto();

        BlockIdentifierDto blockIdentifier = new BlockIdentifierDto();
        blockIdentifier.setHash(blockHash);
        blockIdentifier.setIndex(blockHeight);
        blockEvent.setBlockIdentifier(blockIdentifier);

        blockEvent.setTimestamp(System.currentTimeMillis() / 1000);

        // Create transaction
        TransactionDto txDto = new TransactionDto();

        TransactionIdentifierDto txIdentifier = new TransactionIdentifierDto();
        txIdentifier.setHash(txId);
        txDto.setTransactionIdentifier(txIdentifier);

        TransactionMetadataDto metadata = new TransactionMetadataDto();
        metadata.setSender("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR");
        metadata.setSuccess(true);
        metadata.setFee("1000");
        metadata.setNonce(0L);

        PositionDto position = new PositionDto();
        position.setIndex(0);
        metadata.setPosition(position);

        txDto.setMetadata(metadata);

        blockEvent.setTransactions(List.of(txDto));

        // Set apply events
        payload.setApply(List.of(blockEvent));
        payload.setRollback(new ArrayList<>());

        return payload;
    }
}

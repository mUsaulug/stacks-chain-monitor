package com.stacksmonitoring.application.usecase;

import com.stacksmonitoring.api.dto.webhook.BlockEventDto;
import com.stacksmonitoring.api.dto.webhook.BlockIdentifierDto;
import com.stacksmonitoring.api.dto.webhook.ChainhookPayloadDto;
import com.stacksmonitoring.domain.model.blockchain.*;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import com.stacksmonitoring.domain.repository.StacksTransactionRepository;
import com.stacksmonitoring.domain.repository.TransactionEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for P1-6: Complete soft delete propagation to events.
 *
 * Tests that blockchain reorganizations (rollbacks) properly mark ALL entities
 * as deleted: blocks → transactions → events
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.show-sql=false",
    "logging.level.com.stacksmonitoring=DEBUG"
})
class SoftDeletePropagationTest {

    @Autowired
    private ProcessChainhookPayloadUseCase processPayloadUseCase;

    @Autowired
    private StacksBlockRepository blockRepository;

    @Autowired
    private StacksTransactionRepository transactionRepository;

    @Autowired
    private TransactionEventRepository eventRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up
        eventRepository.deleteAll();
        transactionRepository.deleteAll();
        blockRepository.deleteAll();
    }

    @Test
    @DisplayName("✅ Rollback marks block, transactions, AND events as deleted")
    @Transactional
    void testRollbackPropagatesSoftDelete() {
        // Given: A block with transaction and events
        StacksBlock block = createBlockWithTransactionAndEvents("0xABCD", 100000L);
        blockRepository.save(block);

        // Verify initial state (not deleted)
        assertThat(block.getDeleted()).isFalse();
        assertThat(block.getTransactions().get(0).getDeleted()).isFalse();
        assertThat(block.getTransactions().get(0).getEvents().get(0).getDeleted()).isFalse();

        // When: Rollback webhook arrives
        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        payload.setRollback(List.of(createRollbackEvent("0xABCD")));

        processPayloadUseCase.processPayload(payload);

        // Then: Block, transaction, AND events should all be marked as deleted
        StacksBlock updatedBlock = blockRepository.findByBlockHash("0xABCD").orElseThrow();
        StacksTransaction updatedTx = updatedBlock.getTransactions().get(0);

        assertThat(updatedBlock.getDeleted()).isTrue();
        assertThat(updatedBlock.getDeletedAt()).isNotNull();

        assertThat(updatedTx.getDeleted()).isTrue();
        assertThat(updatedTx.getDeletedAt()).isNotNull();

        // CRITICAL: Events must also be marked as deleted
        assertThat(updatedTx.getEvents()).isNotEmpty();
        TransactionEvent updatedEvent = updatedTx.getEvents().get(0);
        assertThat(updatedEvent.getDeleted()).isTrue();
        assertThat(updatedEvent.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("✅ @Where clause filters out deleted events from queries")
    @Transactional
    void testWhereClauseFiltersDeletedEvents() {
        // Given: Block with transaction and events
        StacksBlock block = createBlockWithTransactionAndEvents("0xABCD", 100000L);
        blockRepository.save(block);

        Long eventId = block.getTransactions().get(0).getEvents().get(0).getId();

        // Verify event is visible
        assertThat(eventRepository.findById(eventId)).isPresent();

        // When: Mark event as deleted
        block.getTransactions().get(0).getEvents().get(0).markAsDeleted();
        blockRepository.save(block);

        // Then: @Where(clause = "deleted = false") should filter it out
        assertThat(eventRepository.findById(eventId)).isEmpty();

        // But if we query without @Where clause, it's still in DB
        // (This proves soft delete is working - data preserved but hidden)
    }

    @Test
    @DisplayName("✅ Multiple events in transaction all marked as deleted")
    @Transactional
    void testMultipleEventsAllDeleted() {
        // Given: Block with transaction containing 3 events
        StacksBlock block = createBlockWithMultipleEvents("0xABCD", 100000L, 3);
        blockRepository.save(block);

        StacksTransaction tx = block.getTransactions().get(0);
        assertThat(tx.getEvents()).hasSize(3);
        assertThat(tx.getEvents()).allMatch(e -> !e.getDeleted());

        // When: Rollback
        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        payload.setRollback(List.of(createRollbackEvent("0xABCD")));
        processPayloadUseCase.processPayload(payload);

        // Then: All 3 events should be marked as deleted
        StacksBlock updatedBlock = blockRepository.findByBlockHash("0xABCD").orElseThrow();
        StacksTransaction updatedTx = updatedBlock.getTransactions().get(0);

        assertThat(updatedTx.getEvents()).hasSize(3);
        assertThat(updatedTx.getEvents()).allMatch(TransactionEvent::getDeleted);
        assertThat(updatedTx.getEvents()).allMatch(e -> e.getDeletedAt() != null);
    }

    @Test
    @DisplayName("✅ Multiple transactions in block all have events deleted")
    @Transactional
    void testMultipleTransactionsEventsDeleted() {
        // Given: Block with 2 transactions, each with 2 events
        StacksBlock block = new StacksBlock();
        block.setBlockHash("0xABCD");
        block.setBlockHeight(100000L);
        block.setIndexBlockHash("0x5678");
        block.setBurnBlockHeight(50000L);
        block.setBurnBlockHash("0xabcd");
        block.setBurnBlockTimestamp(Instant.now());
        block.setMinerAddress("0xminer");
        block.setParentBlockHash("0x0000");
        block.setDeleted(false);
        block.setTransactions(new ArrayList<>());

        // Transaction 1 with 2 events
        StacksTransaction tx1 = createTransactionWithEvents(block, "0xTX1", 2);
        block.getTransactions().add(tx1);

        // Transaction 2 with 2 events
        StacksTransaction tx2 = createTransactionWithEvents(block, "0xTX2", 2);
        block.getTransactions().add(tx2);

        blockRepository.save(block);

        // Verify initial state: 2 transactions, 4 total events
        assertThat(block.getTransactions()).hasSize(2);
        assertThat(block.getTransactions()).flatExtracting(StacksTransaction::getEvents).hasSize(4);

        // When: Rollback
        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        payload.setRollback(List.of(createRollbackEvent("0xABCD")));
        processPayloadUseCase.processPayload(payload);

        // Then: All transactions and all events should be marked as deleted
        StacksBlock updatedBlock = blockRepository.findByBlockHash("0xABCD").orElseThrow();

        assertThat(updatedBlock.getTransactions()).hasSize(2);
        assertThat(updatedBlock.getTransactions()).allMatch(StacksTransaction::getDeleted);

        // CRITICAL: All 4 events should be marked as deleted
        assertThat(updatedBlock.getTransactions())
            .flatExtracting(StacksTransaction::getEvents)
            .hasSize(4)
            .allMatch(TransactionEvent::getDeleted);
    }

    @Test
    @DisplayName("✅ Transaction with no events doesn't cause errors")
    @Transactional
    void testTransactionWithNoEventsHandledGracefully() {
        // Given: Block with transaction that has NO events
        StacksBlock block = new StacksBlock();
        block.setBlockHash("0xABCD");
        block.setBlockHeight(100000L);
        block.setIndexBlockHash("0x5678");
        block.setBurnBlockHeight(50000L);
        block.setBurnBlockHash("0xabcd");
        block.setBurnBlockTimestamp(Instant.now());
        block.setMinerAddress("0xminer");
        block.setParentBlockHash("0x0000");
        block.setDeleted(false);
        block.setTransactions(new ArrayList<>());

        StacksTransaction tx = new StacksTransaction();
        tx.setTxId("0xTX1");
        tx.setTxIndex(0);
        tx.setBlock(block);
        tx.setSender("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR");
        tx.setNonce(0L);
        tx.setFeeMicroStx(java.math.BigInteger.valueOf(1000L));
        tx.setSuccess(true);
        tx.setDeleted(false);
        tx.setEvents(new ArrayList<>()); // Empty events list

        block.getTransactions().add(tx);
        blockRepository.save(block);

        // When: Rollback
        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        payload.setRollback(List.of(createRollbackEvent("0xABCD")));

        // Then: Should not throw error, should mark block and tx as deleted
        processPayloadUseCase.processPayload(payload);

        StacksBlock updatedBlock = blockRepository.findByBlockHash("0xABCD").orElseThrow();
        assertThat(updatedBlock.getDeleted()).isTrue();
        assertThat(updatedBlock.getTransactions().get(0).getDeleted()).isTrue();
    }

    // Helper Methods

    private StacksBlock createBlockWithTransactionAndEvents(String blockHash, Long blockHeight) {
        return createBlockWithMultipleEvents(blockHash, blockHeight, 1);
    }

    private StacksBlock createBlockWithMultipleEvents(String blockHash, Long blockHeight, int eventCount) {
        StacksBlock block = new StacksBlock();
        block.setBlockHash(blockHash);
        block.setBlockHeight(blockHeight);
        block.setIndexBlockHash("0x5678");
        block.setBurnBlockHeight(50000L);
        block.setBurnBlockHash("0xabcd");
        block.setBurnBlockTimestamp(Instant.now());
        block.setMinerAddress("0xminer");
        block.setParentBlockHash("0x0000");
        block.setDeleted(false);
        block.setTransactions(new ArrayList<>());

        StacksTransaction tx = createTransactionWithEvents(block, "0xTX1", eventCount);
        block.getTransactions().add(tx);

        return block;
    }

    private StacksTransaction createTransactionWithEvents(StacksBlock block, String txId, int eventCount) {
        StacksTransaction tx = new StacksTransaction();
        tx.setTxId(txId);
        tx.setTxIndex(0);
        tx.setBlock(block);
        tx.setSender("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR");
        tx.setNonce(0L);
        tx.setFeeMicroStx(java.math.BigInteger.valueOf(1000L));
        tx.setSuccess(true);
        tx.setDeleted(false);
        tx.setEvents(new ArrayList<>());

        for (int i = 0; i < eventCount; i++) {
            FTTransferEvent event = new FTTransferEvent();
            event.setTransaction(tx);
            event.setEventIndex(i);
            event.setContractIdentifier("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.token");
            event.setAssetIdentifier("STX");
            event.setSender("SP111");
            event.setRecipient("SP222");
            event.setAmount(java.math.BigDecimal.valueOf(1000L));
            event.setDeleted(false);

            tx.getEvents().add(event);
        }

        return tx;
    }

    private BlockEventDto createRollbackEvent(String blockHash) {
        BlockEventDto event = new BlockEventDto();

        BlockIdentifierDto identifier = new BlockIdentifierDto();
        identifier.setHash(blockHash);
        identifier.setIndex(100000L);
        event.setBlockIdentifier(identifier);

        return event;
    }
}

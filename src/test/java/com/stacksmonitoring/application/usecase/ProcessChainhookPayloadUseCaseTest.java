package com.stacksmonitoring.application.usecase;

import com.stacksmonitoring.api.dto.webhook.*;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import com.stacksmonitoring.infrastructure.parser.ChainhookPayloadParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessChainhookPayloadUseCase.
 */
@ExtendWith(MockitoExtension.class)
class ProcessChainhookPayloadUseCaseTest {

    @Mock
    private ChainhookPayloadParser parser;

    @Mock
    private StacksBlockRepository blockRepository;

    @Mock
    private com.stacksmonitoring.domain.repository.StacksTransactionRepository transactionRepository;

    @Mock
    private com.stacksmonitoring.application.service.AlertMatchingService alertMatchingService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.stacksmonitoring.domain.repository.AlertNotificationRepository alertNotificationRepository;

    private ProcessChainhookPayloadUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessChainhookPayloadUseCase(parser, blockRepository, transactionRepository,
            alertMatchingService, eventPublisher, alertNotificationRepository);
    }

    @Test
    void testProcessPayload_WithApplyEvents_ShouldPersistBlocks() {
        // Given
        ChainhookPayloadDto payload = createTestPayload(2, 0);

        StacksBlock block1 = createTestBlock(100L, "0xblock100");
        StacksBlock block2 = createTestBlock(101L, "0xblock101");

        when(parser.parseBlock(any())).thenReturn(block1, block2);
        when(parser.parseTransaction(any(), any())).thenReturn(new StacksTransaction());
        when(blockRepository.findByBlockHash(anyString())).thenReturn(Optional.empty());
        when(blockRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        ProcessChainhookPayloadUseCase.ProcessingResult result = useCase.processPayload(payload);

        // Then
        assertThat(result.success).isTrue();
        assertThat(result.applyCount).isEqualTo(2);
        assertThat(result.rollbackCount).isEqualTo(0);
        verify(blockRepository, times(2)).save(any(StacksBlock.class));
    }

    @Test
    void testProcessPayload_WithRollbackEvents_ShouldMarkBlocksAsDeleted() {
        // Given
        ChainhookPayloadDto payload = createTestPayload(0, 1);

        StacksBlock existingBlock = createTestBlock(100L, "0xblock100");
        existingBlock.setDeleted(false);

        when(blockRepository.findByBlockHash("0xblock100")).thenReturn(Optional.of(existingBlock));
        when(blockRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        ProcessChainhookPayloadUseCase.ProcessingResult result = useCase.processPayload(payload);

        // Then
        assertThat(result.success).isTrue();
        assertThat(result.applyCount).isEqualTo(0);
        assertThat(result.rollbackCount).isEqualTo(1);

        ArgumentCaptor<StacksBlock> blockCaptor = ArgumentCaptor.forClass(StacksBlock.class);
        verify(blockRepository).save(blockCaptor.capture());

        StacksBlock savedBlock = blockCaptor.getValue();
        assertThat(savedBlock.getDeleted()).isTrue();
        assertThat(savedBlock.getDeletedAt()).isNotNull();
    }

    @Test
    void testProcessPayload_WithBothApplyAndRollback_ShouldHandleBoth() {
        // Given
        ChainhookPayloadDto payload = createTestPayload(1, 1);

        StacksBlock existingBlock = createTestBlock(99L, "0xblock99");
        StacksBlock newBlock = createTestBlock(100L, "0xblock100");

        when(parser.parseBlock(any())).thenReturn(newBlock);
        when(parser.parseTransaction(any(), any())).thenReturn(new StacksTransaction());
        when(blockRepository.findByBlockHash("0xblock99")).thenReturn(Optional.of(existingBlock));
        when(blockRepository.findByBlockHash("0xblock100")).thenReturn(Optional.empty());
        when(blockRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        ProcessChainhookPayloadUseCase.ProcessingResult result = useCase.processPayload(payload);

        // Then
        assertThat(result.success).isTrue();
        assertThat(result.applyCount).isEqualTo(1);
        assertThat(result.rollbackCount).isEqualTo(1);
        verify(blockRepository, times(2)).save(any(StacksBlock.class));
    }

    @Test
    void testProcessPayload_WithExistingBlock_ShouldSkip() {
        // Given
        ChainhookPayloadDto payload = createTestPayload(1, 0);

        StacksBlock existingBlock = createTestBlock(100L, "0xblock100");
        existingBlock.setDeleted(false);

        when(blockRepository.findByBlockHash("0xblock100")).thenReturn(Optional.of(existingBlock));

        // When
        ProcessChainhookPayloadUseCase.ProcessingResult result = useCase.processPayload(payload);

        // Then
        assertThat(result.success).isTrue();
        assertThat(result.applyCount).isEqualTo(0); // Skipped because block exists
        verify(blockRepository, never()).save(any());
    }

    @Test
    void testProcessPayload_WithDeletedBlockBeingReapplied_ShouldRestoreBlock() {
        // Given
        ChainhookPayloadDto payload = createTestPayload(1, 0);

        StacksBlock deletedBlock = createTestBlock(100L, "0xblock100");
        deletedBlock.markAsDeleted();

        when(blockRepository.findByBlockHash("0xblock100")).thenReturn(Optional.of(deletedBlock));
        when(blockRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        ProcessChainhookPayloadUseCase.ProcessingResult result = useCase.processPayload(payload);

        // Then
        assertThat(result.success).isTrue();
        assertThat(result.applyCount).isEqualTo(0); // No new blocks, but restored

        ArgumentCaptor<StacksBlock> blockCaptor = ArgumentCaptor.forClass(StacksBlock.class);
        verify(blockRepository).save(blockCaptor.capture());

        StacksBlock restoredBlock = blockCaptor.getValue();
        assertThat(restoredBlock.getDeleted()).isFalse();
        assertThat(restoredBlock.getDeletedAt()).isNull();
    }

    @Test
    void testProcessPayload_WithTransactions_ShouldPersistTransactionsWithBlock() {
        // Given
        ChainhookPayloadDto payload = createTestPayloadWithTransactions(1, 3);

        StacksBlock block = createTestBlock(100L, "0xblock100");
        StacksTransaction tx1 = createTestTransaction("0xtx1");
        StacksTransaction tx2 = createTestTransaction("0xtx2");
        StacksTransaction tx3 = createTestTransaction("0xtx3");

        when(parser.parseBlock(any())).thenReturn(block);
        when(parser.parseTransaction(any(), any())).thenReturn(tx1, tx2, tx3);
        when(blockRepository.findByBlockHash(anyString())).thenReturn(Optional.empty());
        when(blockRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        ProcessChainhookPayloadUseCase.ProcessingResult result = useCase.processPayload(payload);

        // Then
        assertThat(result.success).isTrue();
        assertThat(result.applyCount).isEqualTo(1);

        ArgumentCaptor<StacksBlock> blockCaptor = ArgumentCaptor.forClass(StacksBlock.class);
        verify(blockRepository).save(blockCaptor.capture());

        StacksBlock savedBlock = blockCaptor.getValue();
        assertThat(savedBlock.getTransactions()).hasSize(3);
    }

    @Test
    void testProcessPayload_WithRollbackOfNonExistentBlock_ShouldLogWarning() {
        // Given
        ChainhookPayloadDto payload = createTestPayload(0, 1);

        when(blockRepository.findByBlockHash(anyString())).thenReturn(Optional.empty());

        // When
        ProcessChainhookPayloadUseCase.ProcessingResult result = useCase.processPayload(payload);

        // Then
        assertThat(result.success).isTrue();
        assertThat(result.rollbackCount).isEqualTo(0); // Cannot rollback non-existent block
        verify(blockRepository, never()).save(any());
    }

    // Helper methods

    private ChainhookPayloadDto createTestPayload(int applyCount, int rollbackCount) {
        ChainhookPayloadDto payload = new ChainhookPayloadDto();

        if (applyCount > 0) {
            List<BlockEventDto> applyEvents = new ArrayList<>();
            for (int i = 0; i < applyCount; i++) {
                applyEvents.add(createBlockEventDto(100L + i, "0xblock" + (100 + i)));
            }
            payload.setApply(applyEvents);
        }

        if (rollbackCount > 0) {
            List<BlockEventDto> rollbackEvents = new ArrayList<>();
            for (int i = 0; i < rollbackCount; i++) {
                rollbackEvents.add(createBlockEventDto(100L - i - 1, "0xblock" + (99 - i)));
            }
            payload.setRollback(rollbackEvents);
        }

        return payload;
    }

    private ChainhookPayloadDto createTestPayloadWithTransactions(int blockCount, int txPerBlock) {
        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        List<BlockEventDto> applyEvents = new ArrayList<>();

        for (int i = 0; i < blockCount; i++) {
            BlockEventDto blockEvent = createBlockEventDto(100L + i, "0xblock" + (100 + i));

            List<TransactionDto> transactions = new ArrayList<>();
            for (int j = 0; j < txPerBlock; j++) {
                transactions.add(createTransactionDto("0xtx" + i + "_" + j));
            }
            blockEvent.setTransactions(transactions);

            applyEvents.add(blockEvent);
        }

        payload.setApply(applyEvents);
        return payload;
    }

    private BlockEventDto createBlockEventDto(Long height, String hash) {
        BlockEventDto blockEvent = new BlockEventDto();

        BlockIdentifierDto identifier = new BlockIdentifierDto();
        identifier.setIndex(height);
        identifier.setHash(hash);
        blockEvent.setBlockIdentifier(identifier);

        blockEvent.setTimestamp(1234567890L);

        return blockEvent;
    }

    private TransactionDto createTransactionDto(String txId) {
        TransactionDto txDto = new TransactionDto();

        TransactionIdentifierDto identifier = new TransactionIdentifierDto();
        identifier.setHash(txId);
        txDto.setTransactionIdentifier(identifier);

        TransactionMetadataDto metadata = new TransactionMetadataDto();
        metadata.setSender("SP123");
        metadata.setSuccess(true);
        metadata.setFee("1000");

        PositionDto position = new PositionDto();
        position.setIndex(0);
        metadata.setPosition(position);

        txDto.setMetadata(metadata);

        return txDto;
    }

    private StacksBlock createTestBlock(Long height, String hash) {
        StacksBlock block = new StacksBlock();
        block.setBlockHeight(height);
        block.setBlockHash(hash);
        block.setDeleted(false);
        return block;
    }

    private StacksTransaction createTestTransaction(String txId) {
        StacksTransaction tx = new StacksTransaction();
        tx.setTxId(txId);
        tx.setDeleted(false);
        return tx;
    }
}

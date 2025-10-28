package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import com.stacksmonitoring.domain.repository.StacksBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BlockQueryService.
 */
@ExtendWith(MockitoExtension.class)
class BlockQueryServiceTest {

    @Mock
    private StacksBlockRepository blockRepository;

    private BlockQueryService blockQueryService;

    @BeforeEach
    void setUp() {
        blockQueryService = new BlockQueryService(blockRepository);
    }

    @Test
    void testGetBlocks_WithPagination_ShouldReturnPageOfBlocks() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        StacksBlock block1 = createTestBlock(1L, "0xblock1", 100L);
        StacksBlock block2 = createTestBlock(2L, "0xblock2", 101L);
        Page<StacksBlock> mockPage = new PageImpl<>(List.of(block1, block2), pageable, 2);

        when(blockRepository.findAll(any(Pageable.class))).thenReturn(mockPage);

        // When
        Page<StacksBlock> result = blockQueryService.getBlocks(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(blockRepository, times(1)).findAll(pageable);
    }

    @Test
    void testGetBlockById_WhenExists_ShouldReturnBlock() {
        // Given
        Long blockId = 1L;
        StacksBlock mockBlock = createTestBlock(blockId, "0xblock123", 100L);

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(mockBlock));

        // When
        Optional<StacksBlock> result = blockQueryService.getBlockById(blockId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(blockId);
        assertThat(result.get().getBlockHash()).isEqualTo("0xblock123");
        verify(blockRepository, times(1)).findById(blockId);
    }

    @Test
    void testGetBlockById_WhenNotExists_ShouldReturnEmpty() {
        // Given
        Long blockId = 999L;

        when(blockRepository.findById(blockId)).thenReturn(Optional.empty());

        // When
        Optional<StacksBlock> result = blockQueryService.getBlockById(blockId);

        // Then
        assertThat(result).isEmpty();
        verify(blockRepository, times(1)).findById(blockId);
    }

    @Test
    void testGetBlockByHash_WhenExists_ShouldReturnBlock() {
        // Given
        String blockHash = "0xblock123";
        StacksBlock mockBlock = createTestBlock(1L, blockHash, 100L);

        when(blockRepository.findByBlockHash(blockHash)).thenReturn(Optional.of(mockBlock));

        // When
        Optional<StacksBlock> result = blockQueryService.getBlockByHash(blockHash);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getBlockHash()).isEqualTo(blockHash);
        verify(blockRepository, times(1)).findByBlockHash(blockHash);
    }

    @Test
    void testGetBlockByHeight_WhenExists_ShouldReturnBlock() {
        // Given
        Long height = 100L;
        StacksBlock mockBlock = createTestBlock(1L, "0xblock123", height);

        when(blockRepository.findByBlockHeight(height)).thenReturn(Optional.of(mockBlock));

        // When
        Optional<StacksBlock> result = blockQueryService.getBlockByHeight(height);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getBlockHeight()).isEqualTo(height);
        verify(blockRepository, times(1)).findByBlockHeight(height);
    }

    @Test
    void testGetBlocksByTimeRange_ShouldReturnListOfBlocks() {
        // Given
        Instant startTime = Instant.now().minusSeconds(3600);
        Instant endTime = Instant.now();
        StacksBlock block1 = createTestBlock(1L, "0xblock1", 100L);
        StacksBlock block2 = createTestBlock(2L, "0xblock2", 101L);

        when(blockRepository.findBlocksByTimeRange(startTime, endTime))
                .thenReturn(List.of(block1, block2));

        // When
        List<StacksBlock> result = blockQueryService.getBlocksByTimeRange(startTime, endTime);

        // Then
        assertThat(result).hasSize(2);
        verify(blockRepository, times(1)).findBlocksByTimeRange(startTime, endTime);
    }

    @Test
    void testGetLatestBlockHeight_WhenExists_ShouldReturnHeight() {
        // Given
        Long latestHeight = 150234L;

        when(blockRepository.findMaxBlockHeight()).thenReturn(Optional.of(latestHeight));

        // When
        Optional<Long> result = blockQueryService.getLatestBlockHeight();

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(latestHeight);
        verify(blockRepository, times(1)).findMaxBlockHeight();
    }

    @Test
    void testBlockExists_WhenExists_ShouldReturnTrue() {
        // Given
        String blockHash = "0xblock123";

        when(blockRepository.existsByBlockHash(blockHash)).thenReturn(true);

        // When
        boolean result = blockQueryService.blockExists(blockHash);

        // Then
        assertThat(result).isTrue();
        verify(blockRepository, times(1)).existsByBlockHash(blockHash);
    }

    @Test
    void testBlockExists_WhenNotExists_ShouldReturnFalse() {
        // Given
        String blockHash = "0xnonexistent";

        when(blockRepository.existsByBlockHash(blockHash)).thenReturn(false);

        // When
        boolean result = blockQueryService.blockExists(blockHash);

        // Then
        assertThat(result).isFalse();
        verify(blockRepository, times(1)).existsByBlockHash(blockHash);
    }

    @Test
    void testGetActiveBlocks_ShouldReturnOnlyActiveBlocks() {
        // Given
        StacksBlock block1 = createTestBlock(1L, "0xblock1", 100L);
        block1.setDeleted(false);
        StacksBlock block2 = createTestBlock(2L, "0xblock2", 101L);
        block2.setDeleted(false);

        when(blockRepository.findActiveBlocks()).thenReturn(List.of(block1, block2));

        // When
        List<StacksBlock> result = blockQueryService.getActiveBlocks();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(block -> !block.getDeleted());
        verify(blockRepository, times(1)).findActiveBlocks();
    }

    // Helper methods

    private StacksBlock createTestBlock(Long id, String blockHash, Long height) {
        StacksBlock block = new StacksBlock();
        block.setId(id);
        block.setBlockHash(blockHash);
        block.setBlockHeight(height);
        block.setIndexBlockHash(blockHash);
        block.setTimestamp(Instant.now());
        block.setTransactionCount(0);
        block.setDeleted(false);
        return block;
    }
}

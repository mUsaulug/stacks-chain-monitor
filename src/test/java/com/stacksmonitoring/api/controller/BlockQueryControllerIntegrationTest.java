package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.application.service.BlockQueryService;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BlockQueryController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockQueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlockQueryService blockQueryService;

    @Test
    void testGetBlocks_ShouldReturnPagedBlocks() throws Exception {
        // Given
        StacksBlock block1 = createTestBlock(1L, "0xblock1", 100L);
        StacksBlock block2 = createTestBlock(2L, "0xblock2", 101L);
        Page<StacksBlock> mockPage = new PageImpl<>(List.of(block1, block2));

        when(blockQueryService.getBlocks(any(Pageable.class))).thenReturn(mockPage);

        // When & Then
        mockMvc.perform(get("/api/v1/blocks")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].blockHash").value("0xblock1"))
                .andExpect(jsonPath("$.content[1].blockHash").value("0xblock2"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void testGetBlockById_WhenExists_ShouldReturnBlock() throws Exception {
        // Given
        Long blockId = 1L;
        StacksBlock mockBlock = createTestBlock(blockId, "0xblock123", 100L);

        when(blockQueryService.getBlockById(blockId)).thenReturn(Optional.of(mockBlock));

        // When & Then
        mockMvc.perform(get("/api/v1/blocks/{id}", blockId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(blockId))
                .andExpect(jsonPath("$.blockHash").value("0xblock123"))
                .andExpect(jsonPath("$.blockHeight").value(100));
    }

    @Test
    void testGetBlockById_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        Long blockId = 999L;

        when(blockQueryService.getBlockById(blockId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/blocks/{id}", blockId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetBlockByHash_WhenExists_ShouldReturnBlock() throws Exception {
        // Given
        String blockHash = "0xblock123";
        StacksBlock mockBlock = createTestBlock(1L, blockHash, 100L);

        when(blockQueryService.getBlockByHash(blockHash)).thenReturn(Optional.of(mockBlock));

        // When & Then
        mockMvc.perform(get("/api/v1/blocks/hash/{blockHash}", blockHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockHash").value(blockHash))
                .andExpect(jsonPath("$.blockHeight").value(100));
    }

    @Test
    void testGetBlockByHeight_WhenExists_ShouldReturnBlock() throws Exception {
        // Given
        Long height = 100L;
        StacksBlock mockBlock = createTestBlock(1L, "0xblock123", height);

        when(blockQueryService.getBlockByHeight(height)).thenReturn(Optional.of(mockBlock));

        // When & Then
        mockMvc.perform(get("/api/v1/blocks/height/{height}", height))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockHeight").value(height));
    }

    @Test
    void testGetBlocksByTimeRange_ShouldReturnBlocks() throws Exception {
        // Given
        Instant startTime = Instant.parse("2025-10-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-10-28T00:00:00Z");

        StacksBlock block1 = createTestBlock(1L, "0xblock1", 100L);
        StacksBlock block2 = createTestBlock(2L, "0xblock2", 101L);

        when(blockQueryService.getBlocksByTimeRange(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(block1, block2));

        // When & Then
        mockMvc.perform(get("/api/v1/blocks/range")
                .param("startTime", startTime.toString())
                .param("endTime", endTime.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].blockHash").value("0xblock1"))
                .andExpect(jsonPath("$[1].blockHash").value("0xblock2"));
    }

    @Test
    void testGetLatestBlockHeight_WhenExists_ShouldReturnHeight() throws Exception {
        // Given
        Long latestHeight = 150234L;

        when(blockQueryService.getLatestBlockHeight()).thenReturn(Optional.of(latestHeight));

        // When & Then
        mockMvc.perform(get("/api/v1/blocks/latest/height"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.height").value(latestHeight))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetLatestBlockHeight_WhenNotExists_ShouldReturn404() throws Exception {
        // Given
        when(blockQueryService.getLatestBlockHeight()).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/blocks/latest/height"))
                .andExpect(status().isNotFound());
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

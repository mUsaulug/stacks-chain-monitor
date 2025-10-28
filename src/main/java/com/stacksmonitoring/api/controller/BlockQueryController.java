package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.application.service.BlockQueryService;
import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for querying blockchain blocks.
 */
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
@Slf4j
public class BlockQueryController {

    private final BlockQueryService blockQueryService;

    /**
     * Get all blocks with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<BlockResponse>> getBlocks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "blockHeight") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        log.debug("Fetching blocks: page={}, size={}, sortBy={}, direction={}",
            page, size, sortBy, direction);

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<StacksBlock> blocks = blockQueryService.getBlocks(pageable);

        Page<BlockResponse> response = blocks.map(BlockResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific block by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<BlockResponse> getBlockById(@PathVariable Long id) {
        log.debug("Fetching block by ID: {}", id);

        return blockQueryService.getBlockById(id)
                .map(BlockResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a block by hash.
     */
    @GetMapping("/hash/{blockHash}")
    public ResponseEntity<BlockResponse> getBlockByHash(@PathVariable String blockHash) {
        log.debug("Fetching block by hash: {}", blockHash);

        return blockQueryService.getBlockByHash(blockHash)
                .map(BlockResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get a block by height.
     */
    @GetMapping("/height/{height}")
    public ResponseEntity<BlockResponse> getBlockByHeight(@PathVariable Long height) {
        log.debug("Fetching block by height: {}", height);

        return blockQueryService.getBlockByHeight(height)
                .map(BlockResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get blocks within a time range.
     */
    @GetMapping("/range")
    public ResponseEntity<List<BlockResponse>> getBlocksByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

        log.debug("Fetching blocks between {} and {}", startTime, endTime);

        List<StacksBlock> blocks = blockQueryService.getBlocksByTimeRange(startTime, endTime);
        List<BlockResponse> response = blocks.stream()
                .map(BlockResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get the latest block height.
     */
    @GetMapping("/latest/height")
    public ResponseEntity<LatestHeightResponse> getLatestBlockHeight() {
        log.debug("Fetching latest block height");

        return blockQueryService.getLatestBlockHeight()
                .map(height -> new LatestHeightResponse(height, Instant.now()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Block response DTO.
     */
    @Data
    public static class BlockResponse {
        private Long id;
        private Long blockHeight;
        private String blockHash;
        private String indexBlockHash;
        private String parentBlockHash;
        private Instant timestamp;
        private Integer transactionCount;
        private Long burnBlockHeight;
        private String burnBlockHash;
        private Instant burnBlockTimestamp;
        private String minerAddress;
        private Boolean deleted;
        private Instant createdAt;

        public static BlockResponse fromEntity(StacksBlock block) {
            BlockResponse response = new BlockResponse();
            response.setId(block.getId());
            response.setBlockHeight(block.getBlockHeight());
            response.setBlockHash(block.getBlockHash());
            response.setIndexBlockHash(block.getIndexBlockHash());
            response.setParentBlockHash(block.getParentBlockHash());
            response.setTimestamp(block.getTimestamp());
            response.setTransactionCount(block.getTransactionCount());
            response.setBurnBlockHeight(block.getBurnBlockHeight());
            response.setBurnBlockHash(block.getBurnBlockHash());
            response.setBurnBlockTimestamp(block.getBurnBlockTimestamp());
            response.setMinerAddress(block.getMinerAddress());
            response.setDeleted(block.getDeleted());
            response.setCreatedAt(block.getCreatedAt());
            return response;
        }
    }

    /**
     * Latest height response DTO.
     */
    @Data
    @RequiredArgsConstructor
    public static class LatestHeightResponse {
        private final Long height;
        private final Instant timestamp;
    }
}

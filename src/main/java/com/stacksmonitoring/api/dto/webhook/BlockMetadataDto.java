package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Block metadata containing burn block information.
 * Maps to Chainhook webhook block metadata schema.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlockMetadataDto {

    @JsonProperty("bitcoin_anchor_block_identifier")
    private BlockIdentifierDto bitcoinAnchorBlockIdentifier;

    @JsonProperty("pox_cycle_index")
    private Integer poxCycleIndex;

    @JsonProperty("pox_cycle_position")
    private Integer poxCyclePosition;

    @JsonProperty("pox_cycle_length")
    private Integer poxCycleLength;

    @JsonProperty("confirm_microblock_identifier")
    private BlockIdentifierDto confirmMicroblockIdentifier;

    @JsonProperty("stacks_block_hash")
    private String stacksBlockHash;

    // Burn block information
    @JsonProperty("burn_block_height")
    private Long burnBlockHeight;

    @JsonProperty("burn_block_hash")
    private String burnBlockHash;

    @JsonProperty("burn_block_time")
    private Long burnBlockTime;

    @JsonProperty("parent_burn_block_hash")
    private String parentBurnBlockHash;

    @JsonProperty("parent_burn_block_time")
    private Long parentBurnBlockTime;

    // Mining and consensus
    @JsonProperty("miner")
    private String miner;

    @JsonProperty("consensus_hash")
    private String consensusHash;
}

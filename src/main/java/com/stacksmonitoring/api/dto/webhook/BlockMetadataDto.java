package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Block metadata containing burn block information.
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
}

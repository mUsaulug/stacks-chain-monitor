package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Block event DTO containing block and transaction data.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlockEventDto {

    @JsonProperty("block_identifier")
    private BlockIdentifierDto blockIdentifier;

    @JsonProperty("parent_block_identifier")
    private BlockIdentifierDto parentBlockIdentifier;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("transactions")
    private List<TransactionDto> transactions;

    @JsonProperty("metadata")
    private BlockMetadataDto metadata;
}

package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Transaction metadata from Chainhook webhook.
 * Contains detailed transaction execution information.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionMetadataDto {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("raw_tx")
    private String rawTx;

    @JsonProperty("result")
    private String result;

    @JsonProperty("sender")
    private String sender;

    /**
     * Fee in microSTX (1 STX = 1,000,000 microSTX).
     * String to avoid precision loss with large numbers.
     */
    @JsonProperty("fee")
    private String fee;

    @JsonProperty("nonce")
    private Long nonce;

    @JsonProperty("kind")
    private TransactionKindDto kind;

    @JsonProperty("receipt")
    private TransactionReceiptDto receipt;

    @JsonProperty("description")
    private String description;

    @JsonProperty("sponsor")
    private String sponsor;

    @JsonProperty("execution_cost")
    private ExecutionCostDto executionCost;

    @JsonProperty("position")
    private PositionDto position;
}

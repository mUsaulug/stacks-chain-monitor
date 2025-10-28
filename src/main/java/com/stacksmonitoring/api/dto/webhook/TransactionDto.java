package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Transaction DTO from Chainhook webhook.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionDto {

    @JsonProperty("transaction_identifier")
    private TransactionIdentifierDto transactionIdentifier;

    @JsonProperty("operations")
    private List<Object> operations;

    @JsonProperty("metadata")
    private TransactionMetadataDto metadata;
}

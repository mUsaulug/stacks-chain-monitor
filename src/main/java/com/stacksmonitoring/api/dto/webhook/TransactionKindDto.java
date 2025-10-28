package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Transaction kind DTO (ContractCall, ContractDeployment, etc.).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionKindDto {

    @JsonProperty("type")
    private String type;

    @JsonProperty("data")
    private Map<String, Object> data;
}

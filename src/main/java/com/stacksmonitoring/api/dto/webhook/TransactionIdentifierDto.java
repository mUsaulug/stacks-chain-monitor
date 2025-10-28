package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Transaction identifier DTO.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionIdentifierDto {

    @JsonProperty("hash")
    private String hash;
}

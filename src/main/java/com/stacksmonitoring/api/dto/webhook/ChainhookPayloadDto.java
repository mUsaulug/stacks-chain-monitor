package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Root DTO for Chainhook webhook payload.
 * Represents the complete payload received from Chainhook service.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChainhookPayloadDto {

    @JsonProperty("chainhook")
    private ChainhookMetadataDto chainhook;

    @JsonProperty("apply")
    private List<BlockEventDto> apply;

    @JsonProperty("rollback")
    private List<BlockEventDto> rollback;
}

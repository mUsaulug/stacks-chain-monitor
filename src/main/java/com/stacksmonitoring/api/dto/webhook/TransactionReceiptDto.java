package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Transaction receipt DTO containing events.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionReceiptDto {

    @JsonProperty("mutated_contracts_radius")
    private List<String> mutatedContractsRadius;

    @JsonProperty("mutated_assets_radius")
    private List<String> mutatedAssetsRadius;

    @JsonProperty("events")
    private List<EventDto> events;
}

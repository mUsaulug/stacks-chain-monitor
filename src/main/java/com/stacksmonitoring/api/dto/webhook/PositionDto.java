package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Position DTO for transaction/event index.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionDto {

    @JsonProperty("index")
    private Integer index;
}

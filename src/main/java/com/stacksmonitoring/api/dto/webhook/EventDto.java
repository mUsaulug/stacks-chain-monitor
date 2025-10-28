package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Event DTO from transaction receipt.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventDto {

    @JsonProperty("position")
    private PositionDto position;

    @JsonProperty("type")
    private String type;

    @JsonProperty("data")
    private Map<String, Object> data;
}

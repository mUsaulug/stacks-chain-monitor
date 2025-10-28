package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Metadata about the Chainhook configuration.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChainhookMetadataDto {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("predicate")
    private Object predicate;

    @JsonProperty("name")
    private String name;
}

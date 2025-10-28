package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Block identifier containing hash and index.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlockIdentifierDto {

    @JsonProperty("hash")
    private String hash;

    @JsonProperty("index")
    private Long index;
}

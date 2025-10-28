package com.stacksmonitoring.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Execution cost DTO.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionCostDto {

    @JsonProperty("read_count")
    private Long readCount;

    @JsonProperty("read_length")
    private Long readLength;

    @JsonProperty("runtime")
    private Long runtime;

    @JsonProperty("write_count")
    private Long writeCount;

    @JsonProperty("write_length")
    private Long writeLength;
}

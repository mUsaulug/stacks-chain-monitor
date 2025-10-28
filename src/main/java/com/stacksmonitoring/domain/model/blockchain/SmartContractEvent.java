package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Smart contract print event.
 * Emitted by contracts using the (print) function for custom event logging.
 */
@Entity
@Table(name = "smart_contract_event", indexes = {
    @Index(name = "idx_sc_event_topic", columnList = "topic"),
    @Index(name = "idx_sc_event_contract", columnList = "contract_identifier")
})
@DiscriminatorValue("SMART_CONTRACT_EVENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SmartContractEvent extends TransactionEvent {

    @Column(length = 100)
    private String topic;

    /**
     * Decoded event value stored as JSON.
     * Format depends on the contract's print event structure.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_decoded", columnDefinition = "jsonb")
    private Map<String, Object> valueDecoded;

    /**
     * Raw hex-encoded event value.
     */
    @Column(name = "value_raw", columnDefinition = "TEXT")
    private String valueRaw;

    @Override
    public String getEventDescription() {
        return String.format("Smart Contract Event: %s (topic: %s)",
            getContractIdentifier(), topic != null ? topic : "none");
    }
}

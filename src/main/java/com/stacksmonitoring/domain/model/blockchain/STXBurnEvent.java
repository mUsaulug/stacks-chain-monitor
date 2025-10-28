package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * STX burn event.
 * Emitted when STX tokens are burned (destroyed).
 */
@Entity
@Table(name = "stx_burn_event", indexes = {
    @Index(name = "idx_stx_burn_sender", columnList = "sender")
})
@DiscriminatorValue("STX_BURN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class STXBurnEvent extends TransactionEvent {

    @Column(nullable = false, precision = 30)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String sender;

    @Override
    public String getEventDescription() {
        return String.format("STX Burn: %s microSTX from %s",
            amount, sender);
    }
}

package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * STX mint event.
 * Emitted when new STX tokens are minted (e.g., coinbase rewards).
 */
@Entity
@Table(name = "stx_mint_event", indexes = {
    @Index(name = "idx_stx_mint_recipient", columnList = "recipient")
})
@DiscriminatorValue("STX_MINT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class STXMintEvent extends TransactionEvent {

    @Column(nullable = false, precision = 30)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Override
    public String getEventDescription() {
        return String.format("STX Mint: %s microSTX to %s",
            amount, recipient);
    }
}

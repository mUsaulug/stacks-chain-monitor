package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * STX (native token) transfer event.
 * Emitted when STX tokens are transferred between addresses.
 */
@Entity
@Table(name = "stx_transfer_event", indexes = {
    @Index(name = "idx_stx_sender", columnList = "sender"),
    @Index(name = "idx_stx_recipient", columnList = "recipient"),
    @Index(name = "idx_stx_amount", columnList = "amount")
})
@DiscriminatorValue("STX_TRANSFER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class STXTransferEvent extends TransactionEvent {

    @Column(nullable = false, precision = 30)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Override
    public String getEventDescription() {
        return String.format("STX Transfer: %s microSTX from %s to %s",
            amount, sender, recipient);
    }
}

package com.stacksmonitoring.domain.model.blockchain;

import com.stacksmonitoring.domain.valueobject.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Fungible Token (FT) transfer event.
 * Emitted when fungible tokens are transferred between addresses.
 */
@Entity
@Table(name = "ft_transfer_event", indexes = {
    @Index(name = "idx_ft_asset", columnList = "asset_identifier"),
    @Index(name = "idx_ft_sender", columnList = "sender"),
    @Index(name = "idx_ft_recipient", columnList = "recipient"),
    @Index(name = "idx_ft_amount", columnList = "amount")
})
@DiscriminatorValue("FT_TRANSFER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FTTransferEvent extends TransactionEvent {

    @Column(name = "asset_identifier", nullable = false, length = 150)
    private String assetIdentifier;

    @Column(nullable = false, precision = 30)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Override
    public String getEventDescription() {
        return String.format("FT Transfer: %s tokens from %s to %s (amount: %s)",
            assetIdentifier, sender, recipient, amount);
    }
}

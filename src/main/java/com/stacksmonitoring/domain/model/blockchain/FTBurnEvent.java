package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Fungible Token (FT) burn event.
 * Emitted when fungible tokens are burned (destroyed).
 */
@Entity
@Table(name = "ft_burn_event", indexes = {
    @Index(name = "idx_ft_burn_asset", columnList = "asset_identifier"),
    @Index(name = "idx_ft_burn_sender", columnList = "sender")
})
@DiscriminatorValue("FT_BURN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FTBurnEvent extends TransactionEvent {

    @Column(name = "asset_identifier", nullable = false, length = 150)
    private String assetIdentifier;

    @Column(nullable = false, precision = 30)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String sender;

    @Override
    public String getEventDescription() {
        return String.format("FT Burn: %s tokens from %s (amount: %s)",
            assetIdentifier, sender, amount);
    }
}

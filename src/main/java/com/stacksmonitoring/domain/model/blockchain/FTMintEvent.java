package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Fungible Token (FT) mint event.
 * Emitted when new fungible tokens are minted.
 */
@Entity
@Table(name = "ft_mint_event", indexes = {
    @Index(name = "idx_ft_mint_asset", columnList = "asset_identifier"),
    @Index(name = "idx_ft_mint_recipient", columnList = "recipient")
})
@DiscriminatorValue("FT_MINT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FTMintEvent extends TransactionEvent {

    @Column(name = "asset_identifier", nullable = false, length = 150)
    private String assetIdentifier;

    @Column(nullable = false, precision = 30)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Override
    public String getEventDescription() {
        return String.format("FT Mint: %s tokens to %s (amount: %s)",
            assetIdentifier, recipient, amount);
    }
}

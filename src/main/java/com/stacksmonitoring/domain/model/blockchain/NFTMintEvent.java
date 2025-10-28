package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Non-Fungible Token (NFT) mint event.
 * Emitted when a new NFT is minted.
 */
@Entity
@Table(name = "nft_mint_event", indexes = {
    @Index(name = "idx_nft_mint_asset_class", columnList = "asset_class_identifier"),
    @Index(name = "idx_nft_mint_recipient", columnList = "recipient")
})
@DiscriminatorValue("NFT_MINT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NFTMintEvent extends TransactionEvent {

    @Column(name = "asset_class_identifier", nullable = false, length = 150)
    private String assetClassIdentifier;

    @Column(name = "asset_identifier", nullable = false, columnDefinition = "TEXT")
    private String assetIdentifier;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Column(name = "raw_value", columnDefinition = "TEXT")
    private String rawValue;

    @Override
    public String getEventDescription() {
        return String.format("NFT Mint: %s to %s",
            assetClassIdentifier, recipient);
    }
}

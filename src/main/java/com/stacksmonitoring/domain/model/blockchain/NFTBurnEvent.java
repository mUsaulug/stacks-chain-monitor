package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Non-Fungible Token (NFT) burn event.
 * Emitted when an NFT is burned (destroyed).
 */
@Entity
@Table(name = "nft_burn_event", indexes = {
    @Index(name = "idx_nft_burn_asset_class", columnList = "asset_class_identifier"),
    @Index(name = "idx_nft_burn_sender", columnList = "sender")
})
@DiscriminatorValue("NFT_BURN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NFTBurnEvent extends TransactionEvent {

    @Column(name = "asset_class_identifier", nullable = false, length = 150)
    private String assetClassIdentifier;

    @Column(name = "asset_identifier", nullable = false, columnDefinition = "TEXT")
    private String assetIdentifier;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(name = "raw_value", columnDefinition = "TEXT")
    private String rawValue;

    @Override
    public String getEventDescription() {
        return String.format("NFT Burn: %s from %s",
            assetClassIdentifier, sender);
    }
}

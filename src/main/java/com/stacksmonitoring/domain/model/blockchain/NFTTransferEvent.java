package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Non-Fungible Token (NFT) transfer event.
 * Emitted when an NFT is transferred between addresses.
 */
@Entity
@Table(name = "nft_transfer_event", indexes = {
    @Index(name = "idx_nft_asset_class", columnList = "asset_class_identifier"),
    @Index(name = "idx_nft_sender", columnList = "sender"),
    @Index(name = "idx_nft_recipient", columnList = "recipient")
})
@DiscriminatorValue("NFT_TRANSFER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NFTTransferEvent extends TransactionEvent {

    @Column(name = "asset_class_identifier", nullable = false, length = 150)
    private String assetClassIdentifier;

    @Column(name = "asset_identifier", nullable = false, columnDefinition = "TEXT")
    private String assetIdentifier;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(nullable = false, length = 50)
    private String recipient;

    /**
     * Raw hex-encoded asset identifier value.
     */
    @Column(name = "raw_value", columnDefinition = "TEXT")
    private String rawValue;

    @Override
    public String getEventDescription() {
        return String.format("NFT Transfer: %s from %s to %s",
            assetClassIdentifier, sender, recipient);
    }
}

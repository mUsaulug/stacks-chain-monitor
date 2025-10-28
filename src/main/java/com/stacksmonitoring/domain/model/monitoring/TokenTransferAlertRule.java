package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.FTTransferEvent;
import com.stacksmonitoring.domain.model.blockchain.TransactionEvent;
import com.stacksmonitoring.domain.valueobject.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Alert rule triggered when token transfers occur.
 * Supports FT_TRANSFER, FT_MINT, FT_BURN events with optional amount threshold.
 */
@Entity
@DiscriminatorValue("TOKEN_TRANSFER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenTransferAlertRule extends AlertRule {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    private EventType eventType;

    @Column(name = "asset_identifier", length = 150)
    private String assetIdentifier;

    @Column(name = "amount_threshold", precision = 30)
    private BigDecimal amountThreshold;

    @Override
    public boolean matches(Object context) {
        if (!(context instanceof TransactionEvent)) {
            return false;
        }

        TransactionEvent event = (TransactionEvent) context;

        // Check event type
        if (eventType != null && !eventType.equals(event.getEventType())) {
            return false;
        }

        // Check asset identifier (for FT events)
        if (context instanceof FTTransferEvent) {
            FTTransferEvent ftEvent = (FTTransferEvent) context;
            if (assetIdentifier != null && !assetIdentifier.equals(ftEvent.getAssetIdentifier())) {
                return false;
            }

            // Check amount threshold
            if (amountThreshold != null && ftEvent.getAmount().compareTo(amountThreshold) < 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getTriggerDescription(Object context) {
        if (!(context instanceof TransactionEvent)) {
            return "Invalid context";
        }

        TransactionEvent event = (TransactionEvent) context;
        return String.format("Token transfer detected: %s", event.getEventDescription());
    }
}

package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Alert rule triggered when a watched address has activity.
 * Can filter by activity types (SENDER, RECIPIENT, CONTRACT_DEPLOYER).
 */
@Entity
@DiscriminatorValue("ADDRESS_ACTIVITY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressActivityAlertRule extends AlertRule {

    @Column(name = "watched_address", nullable = false, length = 50)
    private String watchedAddress;

    /**
     * Activity types to watch for: SENDER, RECIPIENT, CONTRACT_DEPLOYER.
     * Stored as JSON array.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "activity_types", columnDefinition = "jsonb")
    private List<String> activityTypes;

    @Override
    public boolean matches(Object context) {
        if (!(context instanceof StacksTransaction)) {
            return false;
        }

        StacksTransaction tx = (StacksTransaction) context;

        // Check if watched address is the sender
        if (activityTypes != null && activityTypes.contains("SENDER")) {
            if (watchedAddress.equals(tx.getSender())) {
                return true;
            }
        }

        // TODO: Assumption - Recipient checking requires parsing transaction events
        // For MVP, we'll only check sender activity
        // Production: Parse events to check recipient addresses

        return false;
    }

    @Override
    public String getTriggerDescription(Object context) {
        if (!(context instanceof StacksTransaction)) {
            return "Invalid context";
        }

        StacksTransaction tx = (StacksTransaction) context;
        return String.format("Address activity detected: %s sent transaction %s",
            watchedAddress, tx.getTxId());
    }
}

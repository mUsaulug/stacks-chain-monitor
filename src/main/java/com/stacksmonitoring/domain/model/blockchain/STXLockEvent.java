package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * STX lock event.
 * Emitted when STX tokens are locked (e.g., for stacking/PoX).
 */
@Entity
@Table(name = "stx_lock_event", indexes = {
    @Index(name = "idx_stx_lock_sender", columnList = "locked_address"),
    @Index(name = "idx_stx_lock_unlock_height", columnList = "unlock_height")
})
@DiscriminatorValue("STX_LOCK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class STXLockEvent extends TransactionEvent {

    @Column(name = "lock_amount", nullable = false, precision = 30)
    private BigDecimal lockAmount;

    @Column(name = "unlock_height", nullable = false)
    private Long unlockHeight;

    @Column(name = "locked_address", nullable = false, length = 50)
    private String lockedAddress;

    @Override
    public String getEventDescription() {
        return String.format("STX Lock: %s microSTX from %s until block %d",
            lockAmount, lockedAddress, unlockHeight);
    }
}

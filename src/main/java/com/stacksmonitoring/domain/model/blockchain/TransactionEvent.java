package com.stacksmonitoring.domain.model.blockchain;

import com.stacksmonitoring.domain.valueobject.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for all transaction events in the Stacks blockchain.
 * Uses JOINED inheritance strategy where each subtype has its own table.
 * This is the most critical entity for alert matching - 80% of alert rules operate on events.
 */
@Entity
@Table(name = "transaction_event", indexes = {
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_event_contract", columnList = "event_type, contract_identifier"),
    @Index(name = "idx_event_transaction", columnList = "transaction_id")
})
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "event_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class TransactionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private StacksTransaction transaction;

    @Column(name = "event_index", nullable = false)
    private Integer eventIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, insertable = false, updatable = false)
    private EventType eventType;

    @Column(name = "contract_identifier", length = 150)
    private String contractIdentifier;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Business key based equals using transaction and eventIndex.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionEvent)) return false;
        TransactionEvent that = (TransactionEvent) o;
        return transaction != null && transaction.equals(that.transaction)
            && eventIndex != null && eventIndex.equals(that.eventIndex);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Get a human-readable description of this event.
     * Subclasses should override to provide specific details.
     */
    public abstract String getEventDescription();
}

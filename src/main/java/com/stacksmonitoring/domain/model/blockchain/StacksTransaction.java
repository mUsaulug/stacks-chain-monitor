package com.stacksmonitoring.domain.model.blockchain;

import com.stacksmonitoring.domain.valueobject.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a transaction in the Stacks blockchain.
 * Can be a token transfer, contract call, contract deployment, or other transaction types.
 */
@Entity
@Table(name = "stacks_transaction", indexes = {
    @Index(name = "idx_tx_id", columnList = "tx_id", unique = true),
    @Index(name = "idx_tx_sender", columnList = "sender"),
    @Index(name = "idx_tx_block", columnList = "block_id"),
    @Index(name = "idx_tx_success", columnList = "success"),
    @Index(name = "idx_tx_type", columnList = "tx_type")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StacksTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stacks_transaction_seq_gen")
    @SequenceGenerator(name = "stacks_transaction_seq_gen", sequenceName = "stacks_transaction_seq", allocationSize = 50)
    private Long id;

    @Column(name = "tx_id", nullable = false, unique = true, length = 66)
    private String txId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    private StacksBlock block;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(name = "sponsor_address", length = 50)
    private String sponsorAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false)
    private TransactionType txType;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "tx_index", nullable = false)
    private Integer txIndex;

    @Column(nullable = false)
    private Long nonce;

    /**
     * Transaction fee in microSTX (1 STX = 1,000,000 microSTX).
     * Stored as String to avoid precision loss with very large numbers.
     * Use getFeeInStx() for display purposes.
     */
    @Column(name = "fee_micro_stx", precision = 50)
    private BigInteger feeMicroStx;

    @Column(name = "execution_cost_read_count")
    private Long executionCostReadCount;

    @Column(name = "execution_cost_read_length")
    private Long executionCostReadLength;

    @Column(name = "execution_cost_runtime")
    private Long executionCostRuntime;

    @Column(name = "execution_cost_write_count")
    private Long executionCostWriteCount;

    @Column(name = "execution_cost_write_length")
    private Long executionCostWriteLength;

    @Column(name = "raw_result", columnDefinition = "TEXT")
    private String rawResult;

    @Column(name = "raw_tx", columnDefinition = "TEXT")
    private String rawTx;

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private ContractCall contractCall;

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private ContractDeployment contractDeployment;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionEvent> events = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Soft delete support
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Business key based equals using txId.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StacksTransaction)) return false;
        StacksTransaction that = (StacksTransaction) o;
        return txId != null && txId.equals(that.txId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Convenience method to add an event to this transaction.
     */
    public void addEvent(TransactionEvent event) {
        events.add(event);
        event.setTransaction(this);
    }

    /**
     * Mark this transaction as deleted (for blockchain reorganization handling).
     */
    public void markAsDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }

    /**
     * Check if this transaction is sponsored by another address.
     */
    public boolean isSponsored() {
        return sponsorAddress != null && !sponsorAddress.isEmpty();
    }

    /**
     * Get fee in STX (formatted for display).
     * Converts microSTX to STX (1 STX = 1,000,000 microSTX).
     *
     * @return Fee in STX as BigDecimal, or null if fee not set
     */
    public BigDecimal getFeeInStx() {
        if (feeMicroStx == null) return null;
        return new BigDecimal(feeMicroStx).divide(BigDecimal.valueOf(1_000_000));
    }
}

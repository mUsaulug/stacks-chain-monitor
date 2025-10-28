package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a block in the Stacks blockchain.
 * Each block contains multiple transactions and maintains the blockchain's sequential integrity.
 */
@Entity
@Table(name = "stacks_block", indexes = {
    @Index(name = "idx_block_height", columnList = "block_height", unique = true),
    @Index(name = "idx_block_hash", columnList = "block_hash", unique = true),
    @Index(name = "idx_block_timestamp", columnList = "timestamp")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StacksBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_height", nullable = false, unique = true)
    private Long blockHeight;

    @Column(name = "block_hash", nullable = false, unique = true, length = 66)
    private String blockHash;

    @Column(name = "index_block_hash", nullable = false, length = 66)
    private String indexBlockHash;

    @Column(name = "parent_block_hash", length = 66)
    private String parentBlockHash;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount = 0;

    @Column(name = "burn_block_height")
    private Long burnBlockHeight;

    @Column(name = "burn_block_hash", length = 66)
    private String burnBlockHash;

    @Column(name = "burn_block_timestamp")
    private Instant burnBlockTimestamp;

    @Column(name = "miner_address", length = 50)
    private String minerAddress;

    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StacksTransaction> transactions = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Soft delete support for blockchain reorganization
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Business key based equals using blockHash.
     * This ensures entity equality is based on the unique blockchain identifier,
     * not the database-generated ID.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StacksBlock)) return false;
        StacksBlock that = (StacksBlock) o;
        return blockHash != null && blockHash.equals(that.blockHash);
    }

    /**
     * Consistent hashCode implementation using entity class.
     * This ensures hashCode remains constant even before entity persistence.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Convenience method to add a transaction to this block.
     */
    public void addTransaction(StacksTransaction transaction) {
        transactions.add(transaction);
        transaction.setBlock(this);
        this.transactionCount = transactions.size();
    }

    /**
     * Mark this block as deleted (for blockchain reorganization handling).
     */
    public void markAsDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}

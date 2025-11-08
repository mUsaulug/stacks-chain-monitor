package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Represents a smart contract function call transaction.
 * Contains the contract identifier, function name, and arguments.
 */
@Entity
@Table(name = "contract_call", indexes = {
    @Index(name = "idx_contract_call_identifier", columnList = "contract_identifier"),
    @Index(name = "idx_contract_call_function", columnList = "function_name"),
    @Index(name = "idx_contract_call_composite", columnList = "contract_identifier, function_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractCall {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "contract_call_seq_gen")
    @SequenceGenerator(name = "contract_call_seq_gen", sequenceName = "contract_call_seq", allocationSize = 50)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private StacksTransaction transaction;

    @Column(name = "contract_identifier", nullable = false, length = 150)
    private String contractIdentifier;

    @Column(name = "function_name", nullable = false, length = 100)
    private String functionName;

    /**
     * Function arguments stored as JSON.
     * This allows flexible querying and analysis of contract call parameters.
     * Format: {"arg1": {"type": "uint", "value": "1000"}, "arg2": {...}}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "function_args", columnDefinition = "jsonb")
    private Map<String, Object> functionArgs;

    /**
     * Raw hex-encoded function arguments as received from the blockchain.
     */
    @Column(name = "function_args_raw", columnDefinition = "TEXT")
    private String functionArgsRaw;

    /**
     * Business key based equals using transaction relationship.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContractCall)) return false;
        ContractCall that = (ContractCall) o;
        return transaction != null && transaction.equals(that.transaction);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Get the full contract call identifier (contract::function).
     */
    public String getFullIdentifier() {
        return contractIdentifier + "::" + functionName;
    }
}

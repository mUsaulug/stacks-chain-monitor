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
 * Represents a smart contract deployment transaction.
 * Contains the contract source code, ABI, and trait implementations.
 */
@Entity
@Table(name = "contract_deployment", indexes = {
    @Index(name = "idx_contract_deployment_identifier", columnList = "contract_identifier", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private StacksTransaction transaction;

    @Column(name = "contract_identifier", nullable = false, unique = true, length = 150)
    private String contractIdentifier;

    @Column(name = "contract_name", nullable = false, length = 100)
    private String contractName;

    /**
     * Full Clarity source code of the deployed contract.
     */
    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    /**
     * Contract ABI (Application Binary Interface) stored as JSON.
     * Contains function signatures, data variables, and trait implementations.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "abi", columnDefinition = "jsonb")
    private Map<String, Object> abi;

    /**
     * Trait implementations detected in the contract.
     * Common traits: SIP-010 (fungible token), SIP-009 (NFT)
     */
    @Column(name = "trait_implementations", length = 500)
    private String traitImplementations;

    /**
     * Business key based equals using contractIdentifier.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContractDeployment)) return false;
        ContractDeployment that = (ContractDeployment) o;
        return contractIdentifier != null && contractIdentifier.equals(that.contractIdentifier);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Check if this contract implements a specific trait.
     */
    public boolean implementsTrait(String traitName) {
        return traitImplementations != null && traitImplementations.contains(traitName);
    }

    /**
     * Check if this is a SIP-010 fungible token contract.
     */
    public boolean isSIP010Token() {
        return implementsTrait("sip-010");
    }

    /**
     * Check if this is a SIP-009 NFT contract.
     */
    public boolean isSIP009NFT() {
        return implementsTrait("sip-009");
    }
}

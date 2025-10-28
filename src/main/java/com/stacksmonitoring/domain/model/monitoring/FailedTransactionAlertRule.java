package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Alert rule triggered when a transaction fails.
 * Can optionally filter by contract identifier and function name.
 */
@Entity
@DiscriminatorValue("FAILED_TRANSACTION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FailedTransactionAlertRule extends AlertRule {

    @Column(name = "contract_identifier", length = 150)
    private String contractIdentifier;

    @Column(name = "function_name", length = 100)
    private String functionName;

    @Override
    public boolean matches(Object context) {
        if (!(context instanceof StacksTransaction)) {
            return false;
        }

        StacksTransaction tx = (StacksTransaction) context;

        // Only match failed transactions
        if (tx.getSuccess()) {
            return false;
        }

        // If contract identifier is specified, check contract call
        if (contractIdentifier != null && tx.getContractCall() != null) {
            if (!contractIdentifier.equals(tx.getContractCall().getContractIdentifier())) {
                return false;
            }

            // Check function name if specified
            if (functionName != null && !functionName.equals(tx.getContractCall().getFunctionName())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getTriggerDescription(Object context) {
        if (!(context instanceof StacksTransaction)) {
            return "Invalid context";
        }

        StacksTransaction tx = (StacksTransaction) context;
        String contractInfo = "";
        if (tx.getContractCall() != null) {
            contractInfo = String.format(" (%s::%s)",
                tx.getContractCall().getContractIdentifier(),
                tx.getContractCall().getFunctionName());
        }
        return String.format("Transaction failed: %s%s", tx.getTxId(), contractInfo);
    }
}

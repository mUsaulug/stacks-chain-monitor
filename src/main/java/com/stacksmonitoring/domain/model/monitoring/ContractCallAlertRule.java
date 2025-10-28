package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Alert rule triggered when a specific contract function is called.
 * Can optionally filter by amount threshold.
 */
@Entity
@DiscriminatorValue("CONTRACT_CALL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractCallAlertRule extends AlertRule {

    @Column(name = "contract_identifier", length = 150)
    private String contractIdentifier;

    @Column(name = "function_name", length = 100)
    private String functionName;

    /**
     * Optional amount threshold.
     * If set, only trigger when an amount parameter exceeds this value.
     */
    @Column(name = "amount_threshold", precision = 30)
    private BigDecimal amountThreshold;

    @Override
    public boolean matches(Object context) {
        if (!(context instanceof ContractCall)) {
            return false;
        }

        ContractCall call = (ContractCall) context;

        // Check contract identifier
        if (contractIdentifier != null && !contractIdentifier.equals(call.getContractIdentifier())) {
            return false;
        }

        // Check function name
        if (functionName != null && !functionName.equals(call.getFunctionName())) {
            return false;
        }

        // TODO: Assumption - Amount threshold checking requires parsing function args
        // For MVP, we'll skip amount threshold validation
        // Production: Parse function args and check for amount fields

        return true;
    }

    @Override
    public String getTriggerDescription(Object context) {
        if (!(context instanceof ContractCall)) {
            return "Invalid context";
        }

        ContractCall call = (ContractCall) context;
        return String.format("Contract call detected: %s::%s",
            call.getContractIdentifier(), call.getFunctionName());
    }
}

package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.SmartContractEvent;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Alert rule triggered when a contract emits a print event with specific criteria.
 * Can filter by contract identifier, event key, and data conditions.
 */
@Entity
@DiscriminatorValue("PRINT_EVENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrintEventAlertRule extends AlertRule {

    @Column(name = "contract_identifier", length = 150)
    private String contractIdentifier;

    @Column(name = "event_key", length = 100)
    private String eventKey;

    /**
     * Optional data conditions stored as JSON.
     * Format: {"field1": "expectedValue", "field2": {"operator": "gt", "value": 100}}
     *
     * TODO: Assumption - For MVP, we'll do simple string matching on event data.
     * Production: Implement full JSONPath-based condition evaluation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_conditions", columnDefinition = "jsonb")
    private Map<String, Object> dataConditions;

    @Override
    public boolean matches(Object context) {
        if (!(context instanceof SmartContractEvent)) {
            return false;
        }

        SmartContractEvent event = (SmartContractEvent) context;

        // Check contract identifier
        if (contractIdentifier != null && !contractIdentifier.equals(event.getContractIdentifier())) {
            return false;
        }

        // Check event key (topic)
        if (eventKey != null && !eventKey.equals(event.getTopic())) {
            return false;
        }

        // TODO: Assumption - Skip complex data condition matching for MVP
        // Production: Implement JSONPath-based condition evaluation

        return true;
    }

    @Override
    public String getTriggerDescription(Object context) {
        if (!(context instanceof SmartContractEvent)) {
            return "Invalid context";
        }

        SmartContractEvent event = (SmartContractEvent) context;
        return String.format("Print event detected: %s (topic: %s)",
            event.getContractIdentifier(), event.getTopic());
    }
}

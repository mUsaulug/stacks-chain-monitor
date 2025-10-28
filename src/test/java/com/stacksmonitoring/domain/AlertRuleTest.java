package com.stacksmonitoring.domain;

import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AlertRule entities.
 */
class AlertRuleTest {

    private ContractCallAlertRule alertRule;

    @BeforeEach
    void setUp() {
        alertRule = new ContractCallAlertRule();
        alertRule.setRuleName("sBTC Transfer Alert");
        alertRule.setContractIdentifier("SP2XD7417HGPRTREMKF748VNEQPDRR0RMANB7X1NK.sbtc-token");
        alertRule.setFunctionName("transfer");
        alertRule.setSeverity(AlertSeverity.WARNING);
        alertRule.setCooldownMinutes(60);
        alertRule.setIsActive(true);
    }

    @Test
    void shouldNotBeInCooldownInitially() {
        // Then
        assertThat(alertRule.isInCooldown()).isFalse();
    }

    @Test
    void shouldBeInCooldownAfterTriggering() {
        // When
        alertRule.markAsTriggered();

        // Then
        assertThat(alertRule.isInCooldown()).isTrue();
        assertThat(alertRule.getLastTriggeredAt()).isNotNull();
    }

    @Test
    void shouldNotBeInCooldownAfterCooldownPeriod() {
        // Given - triggered 2 hours ago (cooldown is 60 minutes)
        alertRule.setLastTriggeredAt(Instant.now().minusSeconds(7200));

        // Then
        assertThat(alertRule.isInCooldown()).isFalse();
    }

    @Test
    void shouldMatchContractCall() {
        // Given
        ContractCall call = new ContractCall();
        call.setContractIdentifier("SP2XD7417HGPRTREMKF748VNEQPDRR0RMANB7X1NK.sbtc-token");
        call.setFunctionName("transfer");

        // When
        boolean matches = alertRule.matches(call);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void shouldNotMatchDifferentContract() {
        // Given
        ContractCall call = new ContractCall();
        call.setContractIdentifier("SP123.different-contract");
        call.setFunctionName("transfer");

        // When
        boolean matches = alertRule.matches(call);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void shouldNotMatchDifferentFunction() {
        // Given
        ContractCall call = new ContractCall();
        call.setContractIdentifier("SP2XD7417HGPRTREMKF748VNEQPDRR0RMANB7X1NK.sbtc-token");
        call.setFunctionName("mint");

        // When
        boolean matches = alertRule.matches(call);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void shouldGenerateTriggerDescription() {
        // Given
        ContractCall call = new ContractCall();
        call.setContractIdentifier("SP2XD7417HGPRTREMKF748VNEQPDRR0RMANB7X1NK.sbtc-token");
        call.setFunctionName("transfer");

        // When
        String description = alertRule.getTriggerDescription(call);

        // Then
        assertThat(description).contains("Contract call detected");
        assertThat(description).contains("sbtc-token");
        assertThat(description).contains("transfer");
    }
}

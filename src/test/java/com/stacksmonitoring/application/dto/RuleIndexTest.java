package com.stacksmonitoring.application.dto;

import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.model.monitoring.ContractCallAlertRule;
import com.stacksmonitoring.domain.model.monitoring.FailedTransactionAlertRule;
import com.stacksmonitoring.domain.model.monitoring.TokenTransferAlertRule;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.EventType;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RuleIndex (P1-3: O(1) alert matching).
 * Tests multi-level indexing for fast rule lookup.
 */
class RuleIndexTest {

    @Test
    void testFrom_WithMultipleRules_ShouldBuildCompleteIndex() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "SP123.contract-a", "swap"),
            createContractCallRule(2L, "SP456.contract-b", "transfer"),
            createTokenTransferRule(3L, "STX"),
            createFailedTransactionRule(4L)
        );

        // When
        RuleIndex index = RuleIndex.from(rules);

        // Then
        assertThat(index.byType()).hasSize(3); // CONTRACT_CALL, TOKEN_TRANSFER, FAILED_TRANSACTION
        assertThat(index.byContractIdentifier()).hasSize(2); // SP123.contract-a, SP456.contract-b
        assertThat(index.byAssetIdentifier()).hasSize(1); // STX
        assertThat(index.byContractAndFunction()).hasSize(2);
        assertThat(index.createdAt()).isNotNull();
    }

    @Test
    void testGetCandidatesForContractCall_ExactMatch_ShouldReturnRule() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "SP2C2.arkadiko-swap-v2-1", "swap-x-for-y"),
            createContractCallRule(2L, "SP123.other-contract", "transfer")
        );
        RuleIndex index = RuleIndex.from(rules);

        // When - exact match
        List<RuleSnapshot> candidates = index.getCandidatesForContractCall(
            "SP2C2.arkadiko-swap-v2-1",
            "swap-x-for-y"
        );

        // Then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).contractIdentifier()).isEqualTo("SP2C2.arkadiko-swap-v2-1");
        assertThat(candidates.get(0).functionName()).isEqualTo("swap-x-for-y");
    }

    @Test
    void testGetCandidatesForContractCall_WildcardFunction_ShouldReturnRule() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "SP2C2.arkadiko-swap-v2-1", "*") // Any function
        );
        RuleIndex index = RuleIndex.from(rules);

        // When - specific contract, any function
        List<RuleSnapshot> candidates = index.getCandidatesForContractCall(
            "SP2C2.arkadiko-swap-v2-1",
            "swap-x-for-y"
        );

        // Then - wildcard rule matches
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).functionName()).isEqualTo("*");
    }

    @Test
    void testGetCandidatesForContractCall_WildcardContract_ShouldReturnRule() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "*", "transfer") // Any contract
        );
        RuleIndex index = RuleIndex.from(rules);

        // When - any contract, specific function
        List<RuleSnapshot> candidates = index.getCandidatesForContractCall(
            "SP123.my-contract",
            "transfer"
        );

        // Then - wildcard rule matches
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).contractIdentifier()).isEqualTo("*");
    }

    @Test
    void testGetCandidatesForContractCall_FullWildcard_ShouldReturnRule() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "*", "*") // Any contract, any function
        );
        RuleIndex index = RuleIndex.from(rules);

        // When
        List<RuleSnapshot> candidates = index.getCandidatesForContractCall(
            "SP999.some-contract",
            "some-function"
        );

        // Then
        assertThat(candidates).hasSize(1);
    }

    @Test
    void testGetCandidatesForContractCall_MultipleMatches_ShouldReturnAll() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "SP123.contract", "swap"),      // Exact match
            createContractCallRule(2L, "SP123.contract", "*"),         // Wildcard function
            createContractCallRule(3L, "*", "swap"),                   // Wildcard contract
            createContractCallRule(4L, "*", "*"),                      // Full wildcard
            createContractCallRule(5L, "SP999.other", "transfer")      // No match
        );
        RuleIndex index = RuleIndex.from(rules);

        // When
        List<RuleSnapshot> candidates = index.getCandidatesForContractCall(
            "SP123.contract",
            "swap"
        );

        // Then - should return 4 matching rules (exact + 3 wildcards)
        assertThat(candidates).hasSize(4);
    }

    @Test
    void testGetCandidatesForTokenTransfer_ExactAsset_ShouldReturnRule() {
        // Given
        List<AlertRule> rules = List.of(
            createTokenTransferRule(1L, "STX"),
            createTokenTransferRule(2L, "SP123.token::usda")
        );
        RuleIndex index = RuleIndex.from(rules);

        // When
        List<RuleSnapshot> candidates = index.getCandidatesForTokenTransfer("STX");

        // Then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).assetIdentifier()).isEqualTo("STX");
    }

    @Test
    void testGetCandidatesForTokenTransfer_WildcardAsset_ShouldReturnRule() {
        // Given
        List<AlertRule> rules = List.of(
            createTokenTransferRule(1L, "*") // Any asset
        );
        RuleIndex index = RuleIndex.from(rules);

        // When
        List<RuleSnapshot> candidates = index.getCandidatesForTokenTransfer("SP123.token::custom");

        // Then
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).assetIdentifier()).isEqualTo("*");
    }

    @Test
    void testGetCandidatesForTokenTransfer_ExactPlusWildcard_ShouldReturnBoth() {
        // Given
        List<AlertRule> rules = List.of(
            createTokenTransferRule(1L, "STX"),
            createTokenTransferRule(2L, "*")
        );
        RuleIndex index = RuleIndex.from(rules);

        // When
        List<RuleSnapshot> candidates = index.getCandidatesForTokenTransfer("STX");

        // Then - both exact and wildcard match
        assertThat(candidates).hasSize(2);
    }

    @Test
    void testGetByType_ShouldReturnAllRulesOfType() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "SP123.a", "f1"),
            createContractCallRule(2L, "SP456.b", "f2"),
            createTokenTransferRule(3L, "STX"),
            createFailedTransactionRule(4L)
        );
        RuleIndex index = RuleIndex.from(rules);

        // When
        List<RuleSnapshot> contractCallRules = index.getByType(AlertRuleType.CONTRACT_CALL);
        List<RuleSnapshot> tokenTransferRules = index.getByType(AlertRuleType.TOKEN_TRANSFER);
        List<RuleSnapshot> failedTxRules = index.getByType(AlertRuleType.FAILED_TRANSACTION);

        // Then
        assertThat(contractCallRules).hasSize(2);
        assertThat(tokenTransferRules).hasSize(1);
        assertThat(failedTxRules).hasSize(1);
    }

    @Test
    void testGetStats_ShouldProvideAccurateMetrics() {
        // Given
        List<AlertRule> rules = List.of(
            createContractCallRule(1L, "SP123.contract-a", "swap"),
            createContractCallRule(2L, "SP123.contract-a", "transfer"),
            createContractCallRule(3L, "SP456.contract-b", "mint"),
            createTokenTransferRule(4L, "STX"),
            createTokenTransferRule(5L, "SP789.token::usda")
        );
        RuleIndex index = RuleIndex.from(rules);

        // When
        RuleIndex.IndexStats stats = index.getStats();

        // Then
        assertThat(stats.totalRules()).isEqualTo(5);
        assertThat(stats.typeCategories()).isEqualTo(2); // CONTRACT_CALL, TOKEN_TRANSFER
        assertThat(stats.contractsIndexed()).isEqualTo(2); // SP123.contract-a, SP456.contract-b
        assertThat(stats.assetsIndexed()).isEqualTo(2); // STX, SP789.token::usda
        assertThat(stats.contractFunctionCombos()).isEqualTo(2); // SP123.contract-a, SP456.contract-b
    }

    @Test
    void testIsStale_FreshIndex_ShouldReturnFalse() {
        // Given
        List<AlertRule> rules = List.of(createContractCallRule(1L, "SP123.contract", "swap"));
        RuleIndex index = RuleIndex.from(rules);

        // When
        boolean stale = index.isStale(600); // 10 minutes

        // Then
        assertThat(stale).isFalse();
    }

    @Test
    void testPerformance_O1Lookup_ShouldBeInstant() {
        // Given - 1000 rules to simulate production load
        List<AlertRule> rules = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rules.add(createContractCallRule((long) i, "SP" + i + ".contract", "function" + i));
        }
        RuleIndex index = RuleIndex.from(rules);

        // When - time the lookup
        long startNanos = System.nanoTime();
        List<RuleSnapshot> candidates = index.getCandidatesForContractCall("SP500.contract", "function500");
        long durationNanos = System.nanoTime() - startNanos;

        // Then - should find the rule in O(1) time (< 1ms even for 1000 rules)
        assertThat(candidates).hasSize(1);
        assertThat(durationNanos).isLessThan(1_000_000); // < 1ms
    }

    // Helper methods

    private ContractCallAlertRule createContractCallRule(Long id, String contract, String function) {
        ContractCallAlertRule rule = new ContractCallAlertRule();
        rule.setId(id);
        rule.setRuleName("Test Rule " + id);
        rule.setRuleType(AlertRuleType.CONTRACT_CALL);
        rule.setSeverity(AlertSeverity.MEDIUM);
        rule.setContractIdentifier(contract);
        rule.setFunctionName(function);
        rule.setCooldownMinutes(30);
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));
        return rule;
    }

    private TokenTransferAlertRule createTokenTransferRule(Long id, String asset) {
        TokenTransferAlertRule rule = new TokenTransferAlertRule();
        rule.setId(id);
        rule.setRuleName("Token Transfer Rule " + id);
        rule.setRuleType(AlertRuleType.TOKEN_TRANSFER);
        rule.setSeverity(AlertSeverity.LOW);
        rule.setAssetIdentifier(asset);
        rule.setEventType(EventType.FT_TRANSFER);
        rule.setCooldownMinutes(15);
        rule.setNotificationChannels(List.of(NotificationChannel.WEBHOOK));
        return rule;
    }

    private FailedTransactionAlertRule createFailedTransactionRule(Long id) {
        FailedTransactionAlertRule rule = new FailedTransactionAlertRule();
        rule.setId(id);
        rule.setRuleName("Failed TX Rule " + id);
        rule.setRuleType(AlertRuleType.FAILED_TRANSACTION);
        rule.setSeverity(AlertSeverity.HIGH);
        rule.setCooldownMinutes(60);
        rule.setNotificationChannels(List.of(NotificationChannel.EMAIL));
        return rule;
    }
}

package com.stacksmonitoring.application.dto;

import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-level index for O(1) alert rule lookups.
 *
 * Problem: Current O(k) implementation loops through all rules of a type
 * - 1000 CONTRACT_CALL rules = 1000 matches() checks per event
 * - No indexing by contract address, asset ID, or function name
 *
 * Solution: Multi-level hash map index
 * - Level 1: By AlertRuleType → O(1)
 * - Level 2: By contract/asset identifier → O(1)
 * - Level 3: By function name (for contract calls) → O(1)
 * - Wildcard support: "*" matches all
 *
 * Example Lookup:
 * CONTRACT_CALL for "SP2C2...swap-v2.swap-x-for-y"
 * → index.byType(CONTRACT_CALL)
 * → index.byContract("SP2C2...swap-v2")
 * → index.byFunction("swap-x-for-y")
 * → [matching rules] in O(1) time
 *
 * Reference: CLAUDE.md P1-3 (Index-based Alert Matching)
 */
public record RuleIndex(
    Map<AlertRuleType, List<RuleSnapshot>> byType,
    Map<String, List<RuleSnapshot>> byContractIdentifier,
    Map<String, List<RuleSnapshot>> byAssetIdentifier,
    Map<String, Map<String, List<RuleSnapshot>>> byContractAndFunction,
    Instant createdAt
) implements Serializable {

    /**
     * Build complete index from list of active rules.
     * Called when cache is refreshed.
     */
    public static RuleIndex from(List<AlertRule> activeRules) {
        List<RuleSnapshot> snapshots = activeRules.stream()
            .map(RuleSnapshot::from)
            .toList();

        return new RuleIndex(
            buildTypeIndex(snapshots),
            buildContractIndex(snapshots),
            buildAssetIndex(snapshots),
            buildContractFunctionIndex(snapshots),
            Instant.now()
        );
    }

    /**
     * Get candidates for CONTRACT_CALL evaluation.
     * Returns rules matching contract + function (or wildcards).
     */
    public List<RuleSnapshot> getCandidatesForContractCall(
        String contractIdentifier,
        String functionName
    ) {
        List<RuleSnapshot> candidates = new ArrayList<>();

        // 1. Exact match: specific contract + specific function
        candidates.addAll(getByContractAndFunction(contractIdentifier, functionName));

        // 2. Wildcard function: specific contract + any function
        candidates.addAll(getByContractAndFunction(contractIdentifier, "*"));

        // 3. Wildcard contract: any contract + specific function
        candidates.addAll(getByContractAndFunction("*", functionName));

        // 4. Full wildcard: any contract + any function
        candidates.addAll(getByContractAndFunction("*", "*"));

        return candidates;
    }

    /**
     * Get candidates for TOKEN_TRANSFER evaluation.
     * Returns rules matching asset identifier (or wildcard).
     */
    public List<RuleSnapshot> getCandidatesForTokenTransfer(String assetIdentifier) {
        List<RuleSnapshot> candidates = new ArrayList<>();

        // 1. Exact match: specific asset
        candidates.addAll(byAssetIdentifier.getOrDefault(assetIdentifier, List.of()));

        // 2. Wildcard: any asset
        candidates.addAll(byAssetIdentifier.getOrDefault("*", List.of()));

        return candidates;
    }

    /**
     * Get all rules of a specific type.
     * Fallback for rule types without specialized indexing.
     */
    public List<RuleSnapshot> getByType(AlertRuleType type) {
        return byType.getOrDefault(type, List.of());
    }

    /**
     * Check if index is stale (older than threshold).
     */
    public boolean isStale(long maxAgeSeconds) {
        return Instant.now().isAfter(createdAt.plusSeconds(maxAgeSeconds));
    }

    /**
     * Get statistics about index size.
     */
    public IndexStats getStats() {
        return new IndexStats(
            byType.values().stream().mapToInt(List::size).sum(),
            byType.size(),
            byContractIdentifier.size(),
            byAssetIdentifier.size(),
            byContractAndFunction.size()
        );
    }

    // Private index builders

    private static Map<AlertRuleType, List<RuleSnapshot>> buildTypeIndex(
        List<RuleSnapshot> snapshots
    ) {
        return snapshots.stream()
            .collect(Collectors.groupingBy(RuleSnapshot::type));
    }

    private static Map<String, List<RuleSnapshot>> buildContractIndex(
        List<RuleSnapshot> snapshots
    ) {
        return snapshots.stream()
            .filter(s -> s.contractIdentifier() != null)
            .collect(Collectors.groupingBy(RuleSnapshot::contractIdentifier));
    }

    private static Map<String, List<RuleSnapshot>> buildAssetIndex(
        List<RuleSnapshot> snapshots
    ) {
        return snapshots.stream()
            .filter(s -> s.assetIdentifier() != null)
            .collect(Collectors.groupingBy(RuleSnapshot::assetIdentifier));
    }

    private static Map<String, Map<String, List<RuleSnapshot>>> buildContractFunctionIndex(
        List<RuleSnapshot> snapshots
    ) {
        Map<String, Map<String, List<RuleSnapshot>>> index = new HashMap<>();

        for (RuleSnapshot snapshot : snapshots) {
            if (snapshot.contractIdentifier() != null) {
                String contract = snapshot.contractIdentifier();
                String function = snapshot.functionName() != null ? snapshot.functionName() : "*";

                index
                    .computeIfAbsent(contract, k -> new HashMap<>())
                    .computeIfAbsent(function, k -> new ArrayList<>())
                    .add(snapshot);
            }
        }

        return index;
    }

    private List<RuleSnapshot> getByContractAndFunction(String contract, String function) {
        return byContractAndFunction
            .getOrDefault(contract, Map.of())
            .getOrDefault(function, List.of());
    }

    /**
     * Statistics about index size and coverage.
     */
    public record IndexStats(
        int totalRules,
        int typeCategories,
        int contractsIndexed,
        int assetsIndexed,
        int contractFunctionCombos
    ) {}
}

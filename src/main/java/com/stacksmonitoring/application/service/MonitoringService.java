package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for system monitoring and statistics.
 * Provides health checks, metrics, and system status information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MonitoringService {

    private final StacksBlockRepository blockRepository;
    private final StacksTransactionRepository transactionRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertNotificationRepository alertNotificationRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    /**
     * Get comprehensive system statistics.
     */
    public SystemStatistics getSystemStatistics() {
        log.debug("Generating system statistics");

        SystemStatistics stats = new SystemStatistics();
        stats.setTimestamp(Instant.now());

        // Blockchain data stats
        stats.setTotalBlocks(blockRepository.count());
        stats.setTotalTransactions(transactionRepository.count());

        // Get latest block height
        blockRepository.findMaxBlockHeight().ifPresent(stats::setLatestBlockHeight);

        // Alert stats
        stats.setTotalAlertRules(alertRuleRepository.count());
        stats.setActiveAlertRules(alertRuleRepository.findAllActive().size());
        stats.setTotalNotifications(alertNotificationRepository.count());

        // User stats
        stats.setTotalUsers(userRepository.count());

        return stats;
    }

    /**
     * Get blockchain data statistics.
     */
    public BlockchainStatistics getBlockchainStatistics() {
        log.debug("Generating blockchain statistics");

        BlockchainStatistics stats = new BlockchainStatistics();
        stats.setTimestamp(Instant.now());

        stats.setTotalBlocks(blockRepository.count());
        stats.setTotalTransactions(transactionRepository.count());

        blockRepository.findMaxBlockHeight().ifPresent(stats::setLatestBlockHeight);

        // Transaction breakdown
        Map<String, Long> transactionBreakdown = new HashMap<>();
        transactionBreakdown.put("total", transactionRepository.count());

        stats.setTransactionBreakdown(transactionBreakdown);

        return stats;
    }

    /**
     * Get alert system statistics.
     */
    public AlertStatistics getAlertStatistics() {
        log.debug("Generating alert statistics");

        AlertStatistics stats = new AlertStatistics();
        stats.setTimestamp(Instant.now());

        stats.setTotalRules(alertRuleRepository.count());
        stats.setActiveRules(alertRuleRepository.findAllActive().size());
        stats.setTotalNotifications(alertNotificationRepository.count());

        return stats;
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStatistics() {
        log.debug("Generating cache statistics");

        Map<String, Object> cacheStats = new HashMap<>();

        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("name", cacheName);
                    // Cache-specific stats would go here
                    cacheStats.put(cacheName, stats);
                }
            });
        }

        return cacheStats;
    }

    /**
     * Check system health.
     */
    public HealthStatus checkHealth() {
        log.debug("Performing health check");

        HealthStatus health = new HealthStatus();
        health.setTimestamp(Instant.now());
        health.setStatus("UP");

        Map<String, String> components = new HashMap<>();

        // Check database
        try {
            blockRepository.count();
            components.put("database", "UP");
        } catch (Exception e) {
            components.put("database", "DOWN");
            health.setStatus("DOWN");
            log.error("Database health check failed", e);
        }

        // Check cache
        try {
            if (cacheManager != null) {
                cacheManager.getCacheNames();
                components.put("cache", "UP");
            }
        } catch (Exception e) {
            components.put("cache", "DOWN");
            log.error("Cache health check failed", e);
        }

        health.setComponents(components);

        return health;
    }

    /**
     * System statistics DTO.
     */
    @Data
    public static class SystemStatistics {
        private Instant timestamp;
        private long totalBlocks;
        private long totalTransactions;
        private Long latestBlockHeight;
        private long totalAlertRules;
        private long activeAlertRules;
        private long totalNotifications;
        private long totalUsers;
    }

    /**
     * Blockchain statistics DTO.
     */
    @Data
    public static class BlockchainStatistics {
        private Instant timestamp;
        private long totalBlocks;
        private long totalTransactions;
        private Long latestBlockHeight;
        private Map<String, Long> transactionBreakdown;
    }

    /**
     * Alert statistics DTO.
     */
    @Data
    public static class AlertStatistics {
        private Instant timestamp;
        private long totalRules;
        private long activeRules;
        private long totalNotifications;
    }

    /**
     * Health status DTO.
     */
    @Data
    public static class HealthStatus {
        private Instant timestamp;
        private String status;
        private Map<String, String> components;
    }
}

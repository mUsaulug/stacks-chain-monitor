package com.stacksmonitoring.api.controller;

import com.stacksmonitoring.application.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for system monitoring and health checks.
 * Provides system statistics, health status, and metrics.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final MonitoringService monitoringService;

    /**
     * Get comprehensive system statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<MonitoringService.SystemStatistics> getSystemStatistics() {
        log.debug("Fetching system statistics");

        MonitoringService.SystemStatistics stats = monitoringService.getSystemStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * Get blockchain-specific statistics.
     */
    @GetMapping("/stats/blockchain")
    public ResponseEntity<MonitoringService.BlockchainStatistics> getBlockchainStatistics() {
        log.debug("Fetching blockchain statistics");

        MonitoringService.BlockchainStatistics stats = monitoringService.getBlockchainStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * Get alert system statistics.
     */
    @GetMapping("/stats/alerts")
    public ResponseEntity<MonitoringService.AlertStatistics> getAlertStatistics() {
        log.debug("Fetching alert statistics");

        MonitoringService.AlertStatistics stats = monitoringService.getAlertStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * Get cache statistics.
     */
    @GetMapping("/stats/cache")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        log.debug("Fetching cache statistics");

        Map<String, Object> stats = monitoringService.getCacheStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * System health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<MonitoringService.HealthStatus> checkHealth() {
        log.debug("Performing health check");

        MonitoringService.HealthStatus health = monitoringService.checkHealth();

        if ("UP".equals(health.getStatus())) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(503).body(health);
        }
    }
}

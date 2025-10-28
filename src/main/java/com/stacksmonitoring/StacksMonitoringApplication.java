package com.stacksmonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for Stacks Blockchain Smart Contract Monitoring System.
 *
 * This application provides:
 * - Real-time blockchain transaction processing via Chainhook webhooks
 * - Flexible JSON-based alert rule engine
 * - Multi-channel notifications (Email + Webhook)
 * - Enterprise-level security (JWT + HMAC validation)
 * - High-performance alert matching with cache optimization
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableJpaAuditing
public class StacksMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(StacksMonitoringApplication.class, args);
    }
}

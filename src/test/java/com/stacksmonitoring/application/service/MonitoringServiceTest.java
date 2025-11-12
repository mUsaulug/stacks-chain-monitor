package com.stacksmonitoring.application.service;

import com.stacksmonitoring.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MonitoringService.
 */
@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock
    private StacksBlockRepository blockRepository;

    @Mock
    private StacksTransactionRepository transactionRepository;

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertNotificationRepository alertNotificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheManager cacheManager;

    private MonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        monitoringService = new MonitoringService(
                blockRepository,
                transactionRepository,
                alertRuleRepository,
                alertNotificationRepository,
                userRepository,
                cacheManager
        );
    }

    @Test
    void testGetSystemStatistics_ShouldReturnCompleteStats() {
        // Given
        when(blockRepository.count()).thenReturn(1000L);
        when(transactionRepository.count()).thenReturn(5000L);
        when(blockRepository.findMaxBlockHeight()).thenReturn(Optional.of(1500L));
        when(alertRuleRepository.count()).thenReturn(25L);
        when(alertRuleRepository.findAllActive()).thenReturn(Collections.emptyList());
        when(alertNotificationRepository.count()).thenReturn(150L);
        when(userRepository.count()).thenReturn(10L);

        // When
        MonitoringService.SystemStatistics stats = monitoringService.getSystemStatistics();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalBlocks()).isEqualTo(1000L);
        assertThat(stats.getTotalTransactions()).isEqualTo(5000L);
        assertThat(stats.getLatestBlockHeight()).isEqualTo(1500L);
        assertThat(stats.getTotalAlertRules()).isEqualTo(25L);
        assertThat(stats.getTotalNotifications()).isEqualTo(150L);
        assertThat(stats.getTotalUsers()).isEqualTo(10L);
        assertThat(stats.getTimestamp()).isNotNull();

        verify(blockRepository, times(1)).count();
        verify(transactionRepository, times(1)).count();
        verify(alertRuleRepository, times(1)).count();
        verify(alertNotificationRepository, times(1)).count();
        verify(userRepository, times(1)).count();
    }

    @Test
    void testGetBlockchainStatistics_ShouldReturnBlockchainStats() {
        // Given
        when(blockRepository.count()).thenReturn(1000L);
        when(transactionRepository.count()).thenReturn(5000L);
        when(blockRepository.findMaxBlockHeight()).thenReturn(Optional.of(1500L));

        // When
        MonitoringService.BlockchainStatistics stats = monitoringService.getBlockchainStatistics();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalBlocks()).isEqualTo(1000L);
        assertThat(stats.getTotalTransactions()).isEqualTo(5000L);
        assertThat(stats.getLatestBlockHeight()).isEqualTo(1500L);
        assertThat(stats.getTimestamp()).isNotNull();
        assertThat(stats.getTransactionBreakdown()).isNotNull();
    }

    @Test
    void testGetAlertStatistics_ShouldReturnAlertStats() {
        // Given
        when(alertRuleRepository.count()).thenReturn(25L);
        when(alertRuleRepository.findAllActive()).thenReturn(Collections.emptyList());
        when(alertNotificationRepository.count()).thenReturn(150L);

        // When
        MonitoringService.AlertStatistics stats = monitoringService.getAlertStatistics();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalRules()).isEqualTo(25L);
        assertThat(stats.getActiveRules()).isEqualTo(0L);
        assertThat(stats.getTotalNotifications()).isEqualTo(150L);
        assertThat(stats.getTimestamp()).isNotNull();
    }

    @Test
    void testGetCacheStatistics_WithCache_ShouldReturnCacheStats() {
        // Given
        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCacheNames()).thenReturn(List.of("alertRules"));
        when(cacheManager.getCache("alertRules")).thenReturn(mockCache);

        // When
        var stats = monitoringService.getCacheStatistics();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats).containsKey("alertRules");
    }

    @Test
    void testCheckHealth_WhenAllComponentsUp_ShouldReturnUp() {
        // Given
        when(blockRepository.count()).thenReturn(1000L);
        when(cacheManager.getCacheNames()).thenReturn(List.of("alertRules"));

        // When
        MonitoringService.HealthStatus health = monitoringService.checkHealth();

        // Then
        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getComponents()).containsEntry("database", "UP");
        assertThat(health.getComponents()).containsEntry("cache", "UP");
        assertThat(health.getTimestamp()).isNotNull();
    }

    @Test
    void testCheckHealth_WhenDatabaseDown_ShouldReturnDown() {
        // Given
        when(blockRepository.count()).thenThrow(new RuntimeException("Database connection failed"));

        // When
        MonitoringService.HealthStatus health = monitoringService.checkHealth();

        // Then
        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo("DOWN");
        assertThat(health.getComponents()).containsEntry("database", "DOWN");
    }
}

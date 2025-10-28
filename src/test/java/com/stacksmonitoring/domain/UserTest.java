package com.stacksmonitoring.domain;

import com.stacksmonitoring.domain.model.monitoring.MonitoredContract;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.valueobject.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for User entity.
 */
class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$12$hashed_password");
        user.setFullName("Test User");
        user.setRole(UserRole.USER);
        user.setActive(true);
    }

    @Test
    void shouldAddMonitoredContract() {
        // Given
        MonitoredContract contract = new MonitoredContract();
        contract.setContractIdentifier("SP123.test-contract");
        contract.setContractName("Test Contract");

        // When
        user.addMonitoredContract(contract);

        // Then
        assertThat(user.getMonitoredContracts()).hasSize(1);
        assertThat(contract.getUser()).isEqualTo(user);
    }

    @Test
    void shouldIdentifyAdminRole() {
        // Given
        user.setRole(UserRole.ADMIN);

        // Then
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void shouldIdentifyNonAdminRole() {
        // Given
        user.setRole(UserRole.USER);

        // Then
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void shouldUseEmailForEquals() {
        // Given
        User user1 = new User();
        user1.setEmail("test@example.com");

        User user2 = new User();
        user2.setEmail("test@example.com");

        User user3 = new User();
        user3.setEmail("other@example.com");

        // Then
        assertThat(user1).isEqualTo(user2);
        assertThat(user1).isNotEqualTo(user3);
    }
}

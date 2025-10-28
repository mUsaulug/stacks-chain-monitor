package com.stacksmonitoring.infrastructure;

import com.stacksmonitoring.infrastructure.config.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JWT Token Service.
 */
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService();

        // Set test values using reflection
        ReflectionTestUtils.setField(jwtTokenService, "secretKey", "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm");
        ReflectionTestUtils.setField(jwtTokenService, "expirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtTokenService, "issuer", "stacks-chain-monitor-test");

        userDetails = User.builder()
                .username("test@example.com")
                .password("password")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    @Test
    void shouldGenerateToken() {
        // When
        String token = jwtTokenService.generateToken(userDetails, "USER");

        // Then
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void shouldExtractUsername() {
        // Given
        String token = jwtTokenService.generateToken(userDetails, "USER");

        // When
        String username = jwtTokenService.extractUsername(token);

        // Then
        assertThat(username).isEqualTo("test@example.com");
    }

    @Test
    void shouldExtractRole() {
        // Given
        String token = jwtTokenService.generateToken(userDetails, "USER");

        // When
        String role = jwtTokenService.extractRole(token);

        // Then
        assertThat(role).isEqualTo("USER");
    }

    @Test
    void shouldValidateToken() {
        // Given
        String token = jwtTokenService.generateToken(userDetails, "USER");

        // When
        Boolean isValid = jwtTokenService.validateToken(token, userDetails);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectTokenWithWrongUser() {
        // Given
        String token = jwtTokenService.generateToken(userDetails, "USER");
        UserDetails differentUser = User.builder()
                .username("different@example.com")
                .password("password")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        // When
        Boolean isValid = jwtTokenService.validateToken(token, differentUser);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        // Given - token with 1ms expiration
        ReflectionTestUtils.setField(jwtTokenService, "expirationMs", 1L);
        String token = jwtTokenService.generateToken(userDetails, "USER");

        // Wait for token to expire
        Thread.sleep(10);

        // When
        Boolean isValid = jwtTokenService.validateToken(token, userDetails);

        // Then
        assertThat(isValid).isFalse();
    }
}

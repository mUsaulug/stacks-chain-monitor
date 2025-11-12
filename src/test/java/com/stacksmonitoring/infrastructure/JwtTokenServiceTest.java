package com.stacksmonitoring.infrastructure;

import com.stacksmonitoring.infrastructure.config.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JWT Token Service (RS256).
 */
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenService = new JwtTokenService();

        // Generate test RSA key pair (2048-bit for faster test execution)
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Set test values using reflection
        ReflectionTestUtils.setField(jwtTokenService, "privateKey", keyPair.getPrivate());
        ReflectionTestUtils.setField(jwtTokenService, "publicKey", keyPair.getPublic());
        ReflectionTestUtils.setField(jwtTokenService, "expirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtTokenService, "issuer", "stacks-chain-monitor-test");
        ReflectionTestUtils.setField(jwtTokenService, "currentKeyId", "test-key-id");

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

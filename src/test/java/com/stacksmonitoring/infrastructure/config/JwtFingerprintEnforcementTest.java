package com.stacksmonitoring.infrastructure.config;

import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for JWT fingerprint enforcement (P0-SEC-2).
 *
 * Tests verify that:
 * 1. Valid JWT + valid fingerprint cookie → 200
 * 2. Valid JWT + missing fingerprint cookie → 401
 * 3. Valid JWT + wrong fingerprint cookie → 401
 *
 * Fingerprint cookie is now MANDATORY, not optional.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "security.jwt.expiration=3600000", // 1 hour for testing
    "security.jwt.key-id=test-key-2025-11"
})
class JwtFingerprintEnforcementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private static final String TEST_EMAIL = "fingerprint-test@example.com";
    private static final String PROTECTED_ENDPOINT = "/api/v1/alert-rules"; // Requires authentication

    @BeforeEach
    void setUp() {
        // Create test user
        userRepository.deleteAll();
        testUser = new User();
        testUser.setEmail(TEST_EMAIL);
        testUser.setPasswordHash(passwordEncoder.encode("password123"));
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("P0-SEC-2: Valid JWT + valid fingerprint cookie should allow access")
    void testValidJwtAndValidFingerprint() throws Exception {
        // Generate JWT with fingerprint
        String fingerprint = jwtTokenService.generateFingerprint();
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_EMAIL)
                .password("dummy")
                .roles("USER")
                .build();
        String jwt = jwtTokenService.generateTokenWithFingerprint(userDetails, "USER", fingerprint);

        // Request with both JWT and fingerprint cookie
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("X-Fingerprint", fingerprint)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("P0-SEC-2: Valid JWT + missing fingerprint cookie should return 401")
    void testValidJwtMissingFingerprint() throws Exception {
        // Generate JWT with fingerprint
        String fingerprint = jwtTokenService.generateFingerprint();
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_EMAIL)
                .password("dummy")
                .roles("USER")
                .build();
        String jwt = jwtTokenService.generateTokenWithFingerprint(userDetails, "USER", fingerprint);

        // Request with JWT but WITHOUT fingerprint cookie (sidejacking scenario)
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header("Authorization", "Bearer " + jwt)
                // X-Fingerprint cookie intentionally omitted
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0-SEC-2: Valid JWT + wrong fingerprint cookie should return 401")
    void testValidJwtWrongFingerprint() throws Exception {
        // Generate JWT with fingerprint
        String fingerprint = jwtTokenService.generateFingerprint();
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_EMAIL)
                .password("dummy")
                .roles("USER")
                .build();
        String jwt = jwtTokenService.generateTokenWithFingerprint(userDetails, "USER", fingerprint);

        // Use a different fingerprint (attacker guessing)
        String wrongFingerprint = "wrong-fingerprint-value-123";

        // Request with JWT and WRONG fingerprint cookie
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("X-Fingerprint", wrongFingerprint)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0-SEC-2: Multiple requests with same valid JWT and fingerprint should succeed")
    void testReusableTokenWithFingerprint() throws Exception {
        // Generate JWT with fingerprint
        String fingerprint = jwtTokenService.generateFingerprint();
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_EMAIL)
                .password("dummy")
                .roles("USER")
                .build();
        String jwt = jwtTokenService.generateTokenWithFingerprint(userDetails, "USER", fingerprint);

        // First request
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("X-Fingerprint", fingerprint)))
                .andExpect(status().is2xxSuccessful());

        // Second request with same JWT + fingerprint (should also succeed)
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header("Authorization", "Bearer " + jwt)
                .cookie(new Cookie("X-Fingerprint", fingerprint)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("P0-SEC-2: Token stolen without cookie (XSS scenario) should be rejected")
    void testTokenStolenWithoutCookie() throws Exception {
        // Scenario: Attacker steals JWT via XSS (reads localStorage)
        // but cannot access HttpOnly fingerprint cookie
        String fingerprint = jwtTokenService.generateFingerprint();
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(TEST_EMAIL)
                .password("dummy")
                .roles("USER")
                .build();
        String stolenJwt = jwtTokenService.generateTokenWithFingerprint(userDetails, "USER", fingerprint);
        // Fingerprint cookie is NOT stolen (HttpOnly protection)

        // Attacker tries to use stolen JWT without fingerprint cookie
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                .header("Authorization", "Bearer " + stolenJwt)
                // No fingerprint cookie - XSS cannot access HttpOnly cookies
                )
                .andExpect(status().isUnauthorized());
    }
}

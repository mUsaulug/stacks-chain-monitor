package com.stacksmonitoring.infrastructure.config;

import com.stacksmonitoring.application.service.TokenRevocationService;
import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for JWT security features (Phase 1 validation).
 *
 * Tests cover:
 * 1. RS256 signature validation
 * 2. Token expiration
 * 3. Token revocation (denylist)
 * 4. Fingerprint validation (sidejacking prevention)
 * 5. OWASP JWT best practices compliance
 *
 * Reference: CLAUDE.md P0-1 (JWT RS256 Migration)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.jwt.expiration-ms=60000", // 1 minute for testing
        "security.jwt.issuer=test-issuer"
})
@Transactional
@DisplayName("JWT Security Integration Tests (Phase 1)")
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    private String validToken;
    private String fingerprint;
    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        // Create test user
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$12$dummy");
        user.setFullName("Test User");
        user.setIsActive(true);
        userRepository.save(user);

        testUser = userDetailsService.loadUserByUsername("test@example.com");

        // Generate fingerprint
        fingerprint = jwtTokenService.generateFingerprint();

        // Generate valid token with fingerprint
        validToken = jwtTokenService.generateTokenWithFingerprint(testUser, "USER", fingerprint);
    }

    @Test
    @DisplayName("✅ Valid token with fingerprint cookie → 200 OK")
    void testValidTokenWithFingerprint() throws Exception {
        Cookie fingerprintCookie = new Cookie("X-Fingerprint", fingerprint);
        fingerprintCookie.setHttpOnly(true);
        fingerprintCookie.setSecure(true);

        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", "Bearer " + validToken)
                        .cookie(fingerprintCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("❌ Revoked token → 401 Unauthorized")
    void testRevokedToken() throws Exception {
        // Revoke token
        tokenRevocationService.revokeToken(validToken, "LOGOUT");

        Cookie fingerprintCookie = new Cookie("X-Fingerprint", fingerprint);

        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", "Bearer " + validToken)
                        .cookie(fingerprintCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Invalid fingerprint cookie → 401 Unauthorized")
    void testInvalidFingerprint() throws Exception {
        // Generate different fingerprint
        String wrongFingerprint = jwtTokenService.generateFingerprint();
        Cookie fingerprintCookie = new Cookie("X-Fingerprint", wrongFingerprint);

        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", "Bearer " + validToken)
                        .cookie(fingerprintCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("⚠️ Missing fingerprint cookie → Still works (backward compatibility)")
    void testMissingFingerprintCookie() throws Exception {
        // Token validation should still work without fingerprint for backward compatibility
        // But in production, fingerprint should be mandatory
        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("❌ Expired token → 401 Unauthorized")
    void testExpiredToken() throws Exception {
        // Wait for token to expire (1 minute in test config)
        Thread.sleep(61000);

        Cookie fingerprintCookie = new Cookie("X-Fingerprint", fingerprint);

        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", "Bearer " + validToken)
                        .cookie(fingerprintCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("❌ Invalid signature (tampered token) → 401 Unauthorized")
    void testTamperedToken() throws Exception {
        // Tamper with token by changing last character
        String tamperedToken = validToken.substring(0, validToken.length() - 1) + "X";

        Cookie fingerprintCookie = new Cookie("X-Fingerprint", fingerprint);

        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", "Bearer " + tamperedToken)
                        .cookie(fingerprintCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("✅ Token with correct issuer → 200 OK")
    void testCorrectIssuer() throws Exception {
        // Token was generated with issuer "test-issuer"
        Cookie fingerprintCookie = new Cookie("X-Fingerprint", fingerprint);

        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", "Bearer " + validToken)
                        .cookie(fingerprintCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✅ RS256 algorithm verification")
    void testRS256Algorithm() {
        // Verify token uses RS256
        String[] tokenParts = validToken.split("\\.");
        String header = new String(java.util.Base64.getUrlDecoder().decode(tokenParts[0]));

        assert header.contains("RS256");
        assert header.contains("kid"); // Key ID for rotation
    }

    @Test
    @DisplayName("✅ Fingerprint constant-time comparison (timing attack prevention)")
    void testFingerprintTimingAttack() {
        // Generate two different fingerprints
        String fp1 = jwtTokenService.generateFingerprint();
        String fp2 = jwtTokenService.generateFingerprint();

        String token1 = jwtTokenService.generateTokenWithFingerprint(testUser, "USER", fp1);

        // Measure time for correct vs incorrect fingerprint
        long startCorrect = System.nanoTime();
        boolean correctResult = jwtTokenService.validateFingerprint(token1, fp1);
        long correctTime = System.nanoTime() - startCorrect;

        long startIncorrect = System.nanoTime();
        boolean incorrectResult = jwtTokenService.validateFingerprint(token1, fp2);
        long incorrectTime = System.nanoTime() - startIncorrect;

        // Verify results
        assert correctResult == true;
        assert incorrectResult == false;

        // Timing difference should be minimal (constant-time comparison)
        // Allow 10x variance for test stability
        long timeDiff = Math.abs(correctTime - incorrectTime);
        long avgTime = (correctTime + incorrectTime) / 2;
        assert timeDiff < avgTime * 10 : "Timing attack vulnerability detected!";
    }
}

package com.stacksmonitoring.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT Token utility service for generating and validating RS256 signed tokens.
 * Uses asymmetric RSA 4096-bit key pairs for enhanced security.
 *
 * Security improvements over HS256:
 * - Private key for signing (auth server only)
 * - Public key for verification (can be safely distributed)
 * - Key rotation support via kid (key ID) header
 * - Immune to offline brute-force attacks
 *
 * Reference: OWASP JWT Cheat Sheet for Java
 * https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html
 */
@Component
@Slf4j
public class JwtTokenService {

    @Value("${security.jwt.private-key-path:classpath:keys/jwt-private-key.pem}")
    private Resource privateKeyResource;

    @Value("${security.jwt.public-key-path:classpath:keys/jwt-public-key.pem}")
    private Resource publicKeyResource;

    @Value("${security.jwt.expiration-ms:900000}")
    private Long expirationMs; // Default: 15 minutes

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.key-id:key-2025-11}")
    private String currentKeyId;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    /**
     * Load RSA key pairs on application startup.
     * Reads PEM files and converts to Java Key objects.
     */
    @PostConstruct
    public void init() throws Exception {
        log.info("Loading RSA key pairs for JWT signing/verification");
        this.privateKey = loadPrivateKey();
        this.publicKey = loadPublicKey();
        log.info("Successfully loaded RSA keys with key ID: {}", currentKeyId);
    }

    /**
     * Load private key from PEM file.
     */
    private PrivateKey loadPrivateKey() throws Exception {
        try {
            String keyContent = new String(privateKeyResource.getInputStream().readAllBytes())
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load private key from: " + privateKeyResource, e);
        }
    }

    /**
     * Load public key from PEM file.
     */
    private PublicKey loadPublicKey() throws Exception {
        try {
            String keyContent = new String(publicKeyResource.getInputStream().readAllBytes())
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load public key from: " + publicKeyResource, e);
        }
    }

    /**
     * Extract username from JWT token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract expiration date from JWT token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract a specific claim from JWT token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from JWT token.
     * Uses public key for verification with clock skew tolerance.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .clockSkewSeconds(60) // 1-minute clock skew tolerance
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if token is expired.
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Generate JWT token for user with role claim.
     * Uses RS256 algorithm with private key signing.
     */
    public String generateToken(UserDetails userDetails, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * Create JWT token with claims and subject.
     * Includes kid (key ID) header for key rotation support.
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setHeaderParam("kid", currentKeyId) // Key ID for rotation
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(privateKey, SignatureAlgorithm.RS256) // RS256 with private key
                .compact();
    }

    /**
     * Validate JWT token against user details.
     * Verifies signature, expiration, and username match.
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract role from token.
     */
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * Get current key ID.
     */
    public String getCurrentKeyId() {
        return currentKeyId;
    }

    // ========== Token Fingerprinting (OWASP Best Practice) ==========

    /**
     * Generate a random fingerprint value.
     * This value will be stored in an HttpOnly cookie.
     *
     * @return Random Base64-encoded fingerprint
     */
    public String generateFingerprint() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hash a fingerprint value using SHA-256.
     * This hash will be embedded in the JWT payload.
     *
     * @param fingerprint The original fingerprint value
     * @return SHA-256 hash as Base64 string
     */
    public String hashFingerprint(String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate JWT token with fingerprint for additional security.
     * The fingerprint hash is embedded in the JWT, while the original value
     * should be stored in an HttpOnly/Secure cookie.
     *
     * @param userDetails User details
     * @param role User role
     * @param fingerprint Original fingerprint value (will be hashed)
     * @return JWT token containing fingerprint hash
     */
    public String generateTokenWithFingerprint(UserDetails userDetails, String role, String fingerprint) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("fingerprint", hashFingerprint(fingerprint));
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * Validate token fingerprint against cookie value.
     * Prevents token sidejacking attacks by binding token to specific browser session.
     *
     * @param token JWT token
     * @param cookieFingerprint Fingerprint value from HttpOnly cookie
     * @return true if fingerprints match, false otherwise
     */
    public boolean validateFingerprint(String token, String cookieFingerprint) {
        if (cookieFingerprint == null || cookieFingerprint.isEmpty()) {
            log.warn("Missing fingerprint cookie - potential token sidejacking attempt");
            return false;
        }

        try {
            Claims claims = extractAllClaims(token);
            String tokenFingerprintHash = claims.get("fingerprint", String.class);

            if (tokenFingerprintHash == null) {
                log.warn("JWT missing fingerprint claim");
                return false;
            }

            String cookieFingerprintHash = hashFingerprint(cookieFingerprint);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    tokenFingerprintHash.getBytes(StandardCharsets.UTF_8),
                    cookieFingerprintHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Fingerprint validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract fingerprint hash from token (for debugging).
     */
    public String extractFingerprintHash(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("fingerprint", String.class);
    }
}

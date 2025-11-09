# Security Policy

> **Stacks Chain Monitor - JWT RS256 Security Implementation**
> Last Updated: 2025-11-09
> Status: ✅ Production-Ready (OWASP Compliant)

---

## Table of Contents

1. [JWT Authentication Architecture](#jwt-authentication-architecture)
2. [RS256 vs HS256 Comparison](#rs256-vs-hs256-comparison)
3. [Key Management](#key-management)
4. [Token Fingerprinting](#token-fingerprinting)
5. [Key Rotation Strategy](#key-rotation-strategy)
6. [Security Best Practices](#security-best-practices)
7. [Vulnerability Reporting](#vulnerability-reporting)

---

## JWT Authentication Architecture

### Overview

This application uses **RS256 (RSA 4096-bit)** asymmetric cryptography for JWT authentication:

- **Private Key**: Signs tokens (authentication server only)
- **Public Key**: Verifies tokens (can be distributed to all services)
- **Token Expiration**: 15 minutes (access token), 7 days (refresh token)
- **Fingerprinting**: SHA-256 hash binding to prevent token sidejacking
- **Revocation**: Database denylist for instant token invalidation

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Login Request                                             │
│    POST /api/v1/auth/login                                   │
│    { "email": "user@example.com", "password": "..." }       │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Authentication Service                                    │
│    - Validate credentials (BCrypt with cost 12)             │
│    - Generate fingerprint (32 random bytes)                 │
│    - Create JWT with RS256 signature                        │
│    - Embed fingerprint SHA-256 hash in JWT payload          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Response                                                  │
│    HTTP 200 OK                                               │
│    Set-Cookie: X-Fingerprint=<fingerprint>; HttpOnly; Secure│
│    { "token": "eyJhbGc...", "expiresAt": "..." }            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Subsequent Requests                                       │
│    GET /api/v1/blocks                                        │
│    Authorization: Bearer eyJhbGc...                          │
│    Cookie: X-Fingerprint=<fingerprint>                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. JwtAuthenticationFilter                                  │
│    - Extract token from Authorization header                │
│    - Verify RS256 signature with public key                 │
│    - Check issuer claim                                      │
│    - Validate fingerprint against cookie                     │
│    - Check revocation denylist                               │
│    - Populate SecurityContext                                │
└─────────────────────────────────────────────────────────────┘
```

---

## RS256 vs HS256 Comparison

### Why RS256 is Superior

| Feature | HS256 (Symmetric) | RS256 (Asymmetric) | Winner |
|---------|-------------------|-------------------|--------|
| **Key Type** | Single secret key | Private + Public key pair | ✅ RS256 |
| **Signing** | Same key for sign + verify | Private key signs | ✅ RS256 |
| **Verification** | Same key (must be secret) | Public key verifies (can be shared) | ✅ RS256 |
| **Key Compromise** | Attacker can forge any token | Only affects signing, not verification | ✅ RS256 |
| **Microservices** | All services need secret key | Only auth service needs private key | ✅ RS256 |
| **Key Rotation** | All services update simultaneously | Gradual rollout (dual public keys) | ✅ RS256 |
| **Offline Attacks** | Vulnerable to brute-force | Immune (4096-bit RSA) | ✅ RS256 |
| **OWASP Recommendation** | Not recommended | ✅ Recommended | ✅ RS256 |

### Security Implications

**HS256 Risk Scenario:**
```
1. Developer commits secret key to git (accidental leak)
2. Attacker finds key in public repository
3. Attacker forges token with admin role:
   {
     "sub": "attacker@evil.com",
     "role": "ADMIN",  ← Forged
     "iat": 1699999999,
     "exp": 9999999999
   }
4. Application validates token (correct signature!)
5. ✗ Complete authentication bypass
```

**RS256 Protection:**
```
1. Private key leaked from auth server
2. Attacker can sign NEW tokens
3. BUT: Verification services use public key (unchanged)
4. Rotate private key + update kid header
5. Old tokens still valid until expiration (15 min)
6. ✓ Limited blast radius, no verification bypass
```

---

## Key Management

### Generating Keys (Production)

**Use the provided script:**
```bash
./scripts/generate-rsa-keys.sh /path/to/secure/directory

# Example output:
# ✓ Private key generated: /path/to/secure/directory/jwt-private-key.pem (permissions: 0600)
# ✓ Public key extracted: /path/to/secure/directory/jwt-public-key.pem (permissions: 0644)
# ✓ Key pair verification: PASSED
# ✓ Private key size: (4096 bit)
```

**Manual generation (if needed):**
```bash
# Generate private key (4096-bit RSA)
openssl genpkey -algorithm RSA \
    -pkeyopt rsa_keygen_bits:4096 \
    -out jwt-private-key.pem

# Extract public key
openssl rsa -pubout \
    -in jwt-private-key.pem \
    -out jwt-public-key.pem

# Set restrictive permissions
chmod 600 jwt-private-key.pem  # Owner read/write only
chmod 644 jwt-public-key.pem   # Owner read/write, others read

# Verify key pair
openssl rsa -noout -modulus -in jwt-private-key.pem | openssl md5
openssl rsa -pubin -noout -modulus -in jwt-public-key.pem | openssl md5
# (SHA-256 hashes should match)
```

### Key Storage Best Practices

**❌ NEVER DO THIS:**
```yaml
# application.yml
security:
  jwt:
    private-key: "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg..."  # NEVER!
```

**✅ ALWAYS DO THIS:**

#### Option 1: Environment Variables
```yaml
# application.yml
security:
  jwt:
    private-key-path: ${JWT_PRIVATE_KEY_PATH}
    public-key-path: ${JWT_PUBLIC_KEY_PATH}
```

```bash
# .env (NOT committed to git)
export JWT_PRIVATE_KEY_PATH=/etc/secrets/jwt-private-key.pem
export JWT_PUBLIC_KEY_PATH=/etc/secrets/jwt-public-key.pem
```

#### Option 2: Secret Management Service

**AWS Secrets Manager:**
```bash
# Store private key
aws secretsmanager create-secret \
    --name stacks-monitor/jwt-private-key \
    --secret-binary fileb://jwt-private-key.pem

# Retrieve in application startup
aws secretsmanager get-secret-value \
    --secret-id stacks-monitor/jwt-private-key \
    --query SecretBinary \
    --output text | base64 -d > /tmp/jwt-private-key.pem
```

**HashiCorp Vault:**
```bash
# Store in Vault
vault kv put secret/stacks-monitor/jwt \
    private_key=@jwt-private-key.pem

# Retrieve with Spring Cloud Vault
# (automatic injection into application context)
```

**Docker Secrets (Swarm/Kubernetes):**
```bash
# Create secret
docker secret create jwt_private_key jwt-private-key.pem

# Mount in container
docker service create \
    --secret source=jwt_private_key,target=/run/secrets/jwt-private-key.pem \
    stacks-monitor:latest
```

### .gitignore Configuration

**CRITICAL:** Ensure these patterns are in `.gitignore`:
```gitignore
# JWT Keys (NEVER commit to git)
*.pem
*.key
*.der
*.b64
src/main/resources/keys/
/keys/

# Environment files
.env
.env.*
!.env.example
```

---

## Token Fingerprinting

### What is Token Fingerprinting?

Token fingerprinting binds a JWT to a specific browser session using:
1. **Random fingerprint** (32 bytes) stored in HttpOnly/Secure cookie
2. **SHA-256 hash** of fingerprint embedded in JWT payload
3. **Validation** on every request: hash(cookie) == JWT claim

### Attack Prevention

**Without Fingerprinting:**
```
1. User authenticates, receives JWT
2. Attacker steals JWT (XSS, man-in-the-middle)
3. Attacker uses JWT from different browser/location
4. ✗ Authentication succeeds (token is valid)
```

**With Fingerprinting:**
```
1. User authenticates, receives JWT + fingerprint cookie
2. Attacker steals JWT (XSS, man-in-the-middle)
3. Attacker uses JWT WITHOUT fingerprint cookie
4. ✓ Authentication fails (fingerprint mismatch)
```

### Implementation

**Login endpoint:**
```java
// Generate fingerprint
String fingerprint = jwtTokenService.generateFingerprint();

// Create JWT with fingerprint hash
String token = jwtTokenService.generateTokenWithFingerprint(
    userDetails, role, fingerprint
);

// Set HttpOnly/Secure cookie
Cookie cookie = new Cookie("X-Fingerprint", fingerprint);
cookie.setHttpOnly(true);  // Prevent JavaScript access
cookie.setSecure(true);    // HTTPS only
cookie.setPath("/");
cookie.setMaxAge(900);     // 15 minutes (match token expiration)

response.addCookie(cookie);
```

**Validation (JwtAuthenticationFilter):**
```java
String token = extractTokenFromHeader(request);
String cookieFingerprint = extractFingerprintFromCookie(request);

if (!jwtTokenService.validateFingerprint(token, cookieFingerprint)) {
    throw new AuthenticationException("Token fingerprint mismatch");
}
```

### Constant-Time Comparison

**Prevents timing attacks:**
```java
public boolean validateFingerprint(String token, String cookieFingerprint) {
    String tokenFingerprintHash = extractFingerprintHash(token);
    String cookieFingerprintHash = hashFingerprint(cookieFingerprint);

    // MessageDigest.isEqual uses constant-time comparison
    return MessageDigest.isEqual(
        tokenFingerprintHash.getBytes(StandardCharsets.UTF_8),
        cookieFingerprintHash.getBytes(StandardCharsets.UTF_8)
    );
}
```

---

## Key Rotation Strategy

### When to Rotate Keys

- **Scheduled:** Every 90 days (recommended)
- **Incident:** Immediately if private key compromised
- **Compliance:** Per organizational security policy

### Zero-Downtime Rotation

**Step 1: Generate new key pair**
```bash
./scripts/generate-rsa-keys.sh ./keys-new

# Output:
# jwt-private-key-new.pem (new private key)
# jwt-public-key-new.pem (new public key)
```

**Step 2: Update application configuration (dual support)**
```yaml
# application.yml
security:
  jwt:
    # NEW keys (for signing new tokens)
    private-key-path: file:./keys-new/jwt-private-key.pem
    public-key-path: file:./keys-new/jwt-public-key.pem
    key-id: key-2025-12  # NEW key ID

    # OLD keys (for verifying existing tokens)
    old-public-keys:
      - path: file:./keys-old/jwt-public-key.pem
        key-id: key-2025-11
```

**Step 3: Deploy to all services**
```bash
# Verification services accept BOTH old and new public keys
# Auth service signs with NEW private key only
```

**Step 4: Wait for old tokens to expire (15 minutes)**
```bash
# Monitor logs for key ID usage
grep "kid" /var/log/stacks-monitor.log | jq '.kid' | sort | uniq -c
#  1203 "key-2025-11"  ← Old tokens (decreasing)
# 18452 "key-2025-12"  ← New tokens (increasing)
```

**Step 5: Remove old public key after 24 hours**
```yaml
# application.yml
security:
  jwt:
    public-key-path: file:./keys-new/jwt-public-key.pem
    key-id: key-2025-12
    # old-public-keys: []  ← Removed
```

### Rotation Verification

```bash
# Check current key ID in issued tokens
curl -X POST http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"email":"user@example.com","password":"password"}' \
    | jq -r '.token' \
    | cut -d. -f1 \
    | base64 -d \
    | jq '.kid'

# Expected: "key-2025-12" (new key ID)
```

---

## Security Best Practices

### ✅ DO

1. **Use RS256 with 4096-bit keys**
   ```yaml
   # application.yml
   security:
     jwt:
       algorithm: RS256
   ```

2. **Short token expiration (15 minutes)**
   ```yaml
   security:
     jwt:
       expiration-ms: 900000  # 15 minutes
   ```

3. **Refresh token pattern**
   ```yaml
   security:
     jwt:
       refresh-token-expiration-ms: 604800000  # 7 days
   ```

4. **HttpOnly/Secure cookies**
   ```java
   cookie.setHttpOnly(true);
   cookie.setSecure(true);
   ```

5. **Revocation denylist**
   ```java
   tokenRevocationService.revokeToken(token, "LOGOUT");
   ```

6. **Issuer validation**
   ```java
   Jwts.parserBuilder()
       .requireIssuer("stacks-chain-monitor")
   ```

7. **Clock skew tolerance (1 minute)**
   ```java
   .setAllowedClockSkewSeconds(60)
   ```

### ❌ DO NOT

1. **NEVER use HS256 in production**
   ```java
   // ❌ INSECURE
   .signWith(secretKey, SignatureAlgorithm.HS256)
   ```

2. **NEVER commit private keys to git**
   ```gitignore
   # ✅ Always in .gitignore
   *.pem
   src/main/resources/keys/
   ```

3. **NEVER hardcode secrets**
   ```yaml
   # ❌ NEVER
   security:
     jwt:
       secret: "my-super-secret-key"
   ```

4. **NEVER use long token expiration**
   ```yaml
   # ❌ INSECURE
   expiration-ms: 2592000000  # 30 days
   ```

5. **NEVER skip signature verification**
   ```java
   // ❌ DANGEROUS
   Jwts.parserBuilder().build().parseClaimsJwt(token)
   ```

---

## Vulnerability Reporting

### Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.0.x   | ✅ Yes (RS256)    |
| 0.x.x   | ❌ No (HS256 - deprecated) |

### Reporting a Vulnerability

**DO NOT** open public GitHub issues for security vulnerabilities.

**Instead, please:**
1. Email: security@stacks-monitor.com
2. Include: Detailed description, steps to reproduce, impact assessment
3. Response time: 48 hours for acknowledgment, 7 days for patch

### Security Contacts

- **Security Lead:** security@stacks-monitor.com
- **PGP Key:** [Public key for encrypted communication]

---

## References

### OWASP

- [OWASP JWT Cheat Sheet for Java](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [OWASP API Security Top 10](https://owasp.org/API-Security/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

### RFCs

- [RFC 7519: JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 7518: JSON Web Algorithms (JWA)](https://datatracker.ietf.org/doc/html/rfc7518)
- [RFC 7517: JSON Web Key (JWK)](https://datatracker.ietf.org/doc/html/rfc7517)

### Spring Security

- [Spring Security 6.x JWT Guide](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)

---

**Document Status:** ✅ Current (2025-11-09)
**Next Review:** 2025-12-09
**Maintainer:** Security Team

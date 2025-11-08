# CLAUDE.md - Stacks Chain Monitor Refactoring Roadmap

> **Critical Production Readiness Guide**
> This document serves as the definitive roadmap for migrating the Stacks Chain Monitor from MVP to production-ready status.
> All issues are categorized by priority (P0/P1/P2) with concrete implementation patterns.

---

## Table of Contents

1. [Current Architecture](#current-architecture)
2. [Critical Issues (P0)](#critical-issues-p0---production-blockers)
3. [Important Issues (P1)](#important-issues-p1---performance--integrity)
4. [Code Quality Issues (P2)](#code-quality-issues-p2---maintainability)
5. [Migration Priorities](#migration-priorities)
6. [Tech Stack](#tech-stack)
7. [Code Style & Patterns](#code-style--patterns)
8. [Key References](#key-references)

---

## Current Architecture

### Clean Architecture (4 Layers)

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  - 8 REST Controllers (32 endpoints)                        │
│  - JWT Authentication Filter                                 │
│  - HMAC Signature Validation Filter                         │
│  - Rate Limiting Filter (Bucket4j)                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Application Layer                          │
│  - AlertMatchingService (O(k) scan - needs optimization)    │
│  - NotificationDispatcher (dispatch before commit - RISK)   │
│  - ProcessChainhookPayloadUseCase                           │
│  - Query Services (Block, Transaction)                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                             │
│  - 15 Entities (SINGLE_TABLE + JOINED inheritance)          │
│  - AlertRule (base) + 5 subtypes                            │
│  - TransactionEvent (base) + 11 subtypes                    │
│  - Repository Interfaces                                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                         │
│  - PostgreSQL 14+ (JSONB support)                           │
│  - JPA/Hibernate                                             │
│  - Caffeine Cache (in-memory - multi-instance FAIL)         │
│  - ChainhookPayloadParser                                   │
└─────────────────────────────────────────────────────────────┘
```

### Current Data Flow (Chainhook Webhook → Notifications)

```
Chainhook Webhook
      ↓
ChainhookHmacFilter (HMAC-SHA256 validation)
      ↓
WebhookController (@Async returns 200 OK)
      ↓
ProcessChainhookPayloadUseCase
      ↓
   @Transactional
      ├── handleRollbacks (soft delete blocks/tx)
      ├── handleApplies (parse & persist blocks/tx)
      ├── AlertMatchingService.evaluateTransaction
      │        ↓
      │   O(k) loop through all rules of type ⚠️
      │        ↓
      │   isInCooldown() [race condition] ⚠️
      │        ↓
      │   createNotifications (individual saves) ⚠️
      │        ↓
      │   markAsTriggered() [mutable cached entity] ⚠️
      │
      └── NotificationDispatcher.dispatchBatch
              ↓
          [PROBLEM: dispatched BEFORE commit] ⚠️
              ↓
          Email/Webhook delivery
```

### Inheritance Strategies

1. **AlertRule (SINGLE_TABLE)**
   - Discriminator: `rule_type`
   - Subtypes: CONTRACT_CALL, TOKEN_TRANSFER, FAILED_TRANSACTION, PRINT_EVENT, ADDRESS_ACTIVITY
   - Trade-off: Fast queries, but nullable columns for subtype-specific fields

2. **TransactionEvent (JOINED)**
   - 11 event types, each with own table
   - Trade-off: Full normalization, but slower polymorphic queries

---

## Critical Issues (P0) - Production Blockers

### 🔴 P0-1: JWT Symmetric Key Vulnerability (HS256 → RS256)

**File:** `JwtTokenService.java:95`

**Problem:**
```java
// CURRENT: HS256 with symmetric key
.signWith(getSigningKey(), SignatureAlgorithm.HS256)

// TODO comment at line 109:
// "Using HS256 for MVP. Production should use RS256 with key pairs."
```

**Risk:**
- Single secret key for both signing and verification
- Key compromise = attacker can forge any token with any identity
- Key rotation nightmare in microservices
- Vulnerable to offline brute-force attacks (OWASP)

**Solution:**
Migrate to **RS256 (RSA 4096-bit)** with:
- Private key for signing (auth server only)
- Public key for verification (all services)
- Token fingerprinting (SHA-256 hash in cookie + JWT payload)
- Revocation denylist (SHA-256 token digest in DB)
- 15-minute access token + 7-day refresh token

**Implementation Priority:** IMMEDIATE (before production)

---

### 🔴 P0-2: In-Memory Rate Limiting (Multi-Instance Fail)

**File:** `RateLimitFilter.java:33`

**Problem:**
```java
private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
```

**Risk:**
- Each instance maintains separate buckets
- 3 instances with 100 req/min limit = actually 300 req/min
- Users can bypass limits by hitting different backends
- Memory leak: no eviction, cache grows unbounded

**Solution:**
Redis-backed distributed rate limiting with `Bucket4j LettuceBasedProxyManager`

**Implementation Priority:** IMMEDIATE (security vulnerability)

---

### 🔴 P0-3: Security Filter Ordering (Per-User Rate Limiting Broken)

**File:** `SecurityConfiguration.java:60-62`

**Problem:**
```java
.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(chainhookHmacFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

**Risk:**
- RateLimit runs BEFORE JWT authentication
- `SecurityContextHolder` not yet populated
- `getUserIdentifier()` always falls back to IP
- Per-user rate limiting completely disabled

**Solution:**
Correct order: `HMAC → JWT → RateLimit`
```java
.addFilterBefore(chainhookHmacFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
```

**Implementation Priority:** IMMEDIATE

---

### 🔴 P0-4: HMAC Replay Attack Vulnerability

**File:** `ChainhookHmacFilter.java:80`

**Problem:**
```java
String expectedSignature = calculateHmacSignature(requestBody);
// No timestamp validation, no nonce tracking
```

**Risk:**
- Attacker can capture valid webhook and replay indefinitely
- No freshness validation
- Constant-time comparison is good, but insufficient

**Solution:**
Add timestamp + nonce validation:
```java
1. Require X-Signature-Timestamp header
2. Reject requests older than 5 minutes
3. Include timestamp in HMAC: hmac(secret, timestamp + "." + body)
4. Track used nonces in Redis (10-min TTL)
```

**Implementation Priority:** IMMEDIATE

---

### 🔴 P0-5: Actuator Endpoints Publicly Accessible

**File:** `SecurityConfiguration.java:44-50`

**Problem:**
```java
.requestMatchers(
    "/api/v1/auth/**",
    "/api/v1/webhook/**",
    "/actuator/**",  // ⚠️ All actuator endpoints public
    // ...
).permitAll()
```

**Risk:**
- Thread dumps, heap dumps, metrics exposed
- Information leakage for attackers
- Production data exposure

**Solution:**
```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

**Implementation Priority:** HIGH

---

### 🔴 P0-6: Notifications Dispatched Before Transaction Commit

**File:** `ProcessChainhookPayloadUseCase.java:185`

**Problem:**
```java
@Transactional
private int handleApplies(...) {
    // ... persist blocks/tx ...
    alertMatchingService.evaluateTransaction(transaction); // saves to DB
    allNotifications.addAll(notifications);

    // PROBLEM: Dispatch BEFORE commit completes
    notificationDispatcher.dispatchBatch(allNotifications);

    return count; // transaction commits AFTER dispatch
}
```

**Risk:**
- Email/webhook sent before DB commit
- Commit failure = phantom notifications to users
- Duplicate notifications on retry

**Solution:**
Use `@TransactionalEventListener(phase = AFTER_COMMIT)`:
```java
// In ProcessChainhookPayloadUseCase
applicationEventPublisher.publishEvent(new NotificationsReadyEvent(allNotifications));

// In NotificationDispatcher
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleNotificationsReady(NotificationsReadyEvent event) {
    dispatchBatch(event.getNotifications());
}
```

**Implementation Priority:** IMMEDIATE

---

## Important Issues (P1) - Performance & Integrity

### 🟡 P1-1: Caching Mutable JPA Entities (Thread Safety + Staleness)

**File:** `AlertMatchingService.java:251`

**Problem:**
```java
@Cacheable(value = "alertRules", key = "#ruleType")
public List<AlertRule> getActiveRulesByType(AlertRuleType ruleType) {
    return alertRuleRepository.findActiveByRuleType(ruleType);
}

// Later:
rule.markAsTriggered(); // Mutates cached entity
alertRuleRepository.save(rule);
```

**Risk:**
- Cached entity references are mutable
- `lastTriggeredAt` changes = stale cache
- Multi-threaded access = race conditions
- Redis serialization fails on lazy proxies
- Detached entity updates lost

**Solution:**
Cache **immutable DTOs**, not entities:
```java
public record RuleSnapshot(
    Long id, AlertRuleType type, String contractId,
    Duration cooldown, Set<NotificationChannel> channels,
    Predicate<Object> matcher
) {}

@Cacheable("ruleIndex")
public RuleIndex buildIndex() {
    var active = alertRuleRepository.findAllActive();
    return RuleIndex.from(active); // Convert to immutable snapshots
}
```

**Implementation Priority:** HIGH

---

### 🟡 P1-2: IDENTITY ID Generation (Batch Operations Disabled)

**File:** `AlertRule.java:44`, `StacksTransaction.java`, etc.

**Problem:**
```java
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

**Risk:**
- Hibernate must get ID immediately after each INSERT
- Prevents JDBC batching even with `hibernate.jdbc.batch_size`
- 10,000 inserts: 185s with IDENTITY vs 9s with SEQUENCE (95% slower)

**Solution:**
Migrate to SEQUENCE with allocation:
```java
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alert_rule_seq")
@SequenceGenerator(name = "alert_rule_seq", sequenceName = "alert_rule_seq", allocationSize = 50)
private Long id;

// application.properties
spring.jpa.properties.hibernate.jdbc.batch_size=30
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

**Implementation Priority:** HIGH (significant performance impact)

---

### 🟡 P1-3: Alert Matching O(k) Full Scan (Not O(1))

**File:** `AlertMatchingService.java:81`

**Problem:**
```java
// Get all CONTRACT_CALL rules
List<AlertRule> rules = getActiveRulesByType(AlertRuleType.CONTRACT_CALL);

// Then loop through ALL rules of this type
for (AlertRule rule : rules) {
    if (shouldTrigger(rule, contractCall)) { ... }
}
```

**Risk:**
- "O(1) lookup" claim (line 30) is false
- Actually O(k) where k = number of rules per type
- 1000 CONTRACT_CALL rules = 1000 `matches()` checks per event
- No indexing by contract address, asset, function name

**Solution:**
Multi-level index with immutable snapshots:
```java
public record RuleIndex(
    Map<String, List<RuleSnapshot>> byContractAddress,  // "SP2C2...swap-v2" → rules
    Map<String, List<RuleSnapshot>> byAssetId,          // "STX" → rules
    Map<AlertRuleType, List<RuleSnapshot>> byType
) {}

private List<RuleSnapshot> candidatesFor(ContractCall call) {
    var idx = ruleIndex();
    return Stream.concat(
        idx.byContractAddress().getOrDefault(call.getContractIdentifier(), List.of()).stream(),
        idx.byContractAddress().getOrDefault("*", List.of()).stream()
    ).toList();
}
```

**Implementation Priority:** HIGH (scalability blocker)

---

### 🟡 P1-4: Race Condition in Cooldown Logic

**File:** `AlertMatchingService.java:157-162`, `AlertRule.java:124-138`

**Problem:**
```java
// Thread 1 and Thread 2 both execute:
if (rule.isInCooldown()) {  // Both see false
    return false;
}
// Both proceed to trigger...

// Later:
rule.markAsTriggered();  // Both update lastTriggeredAt
alertRuleRepository.save(rule);  // Second save wins, but both notifications sent
```

**Risk:**
- Two threads can both pass cooldown check
- Duplicate notifications sent
- `@Version` optimistic locking throws `OptimisticLockException` on second save
- Exception not handled = entire transaction rolls back

**Solution:**
Database-level conditional UPDATE:
```java
@Modifying
@Query("""
    UPDATE AlertRule r
       SET r.lastTriggeredAt = :now
     WHERE r.id = :id
       AND (r.lastTriggeredAt IS NULL
            OR r.lastTriggeredAt <= :windowStart)
""")
int markTriggeredIfOutOfCooldown(Long id, Instant now, Instant windowStart);

// Returns 1 if updated (trigger allowed), 0 if cooldown (skip)
```

**Implementation Priority:** HIGH

---

### 🟡 P1-5: No Idempotency (Duplicate Notifications)

**File:** `AlertMatchingService.java:199`, `ProcessChainhookPayloadUseCase.java:122`

**Problem:**
```java
// Webhook can arrive multiple times (network retry, reorg, etc.)
if (existingBlock.isPresent()) {
    log.debug("Block {} already exists, skipping", blockHash);
    continue; // But what about partial transactions?
}

// No unique constraint on notifications:
alertNotificationRepository.save(notification);
```

**Risk:**
- Same webhook processed multiple times
- Duplicate `AlertNotification` records
- Users receive multiple emails/webhooks for same event
- Block exists check insufficient (what if transactions partially failed?)

**Solution:**
Unique constraint + idempotent INSERT:
```java
@Table(uniqueConstraints = @UniqueConstraint(
    name = "uk_notification_rule_tx_event_channel",
    columnNames = {"alert_rule_id", "transaction_id", "event_id", "channel"}
))
public class AlertNotification { ... }

// In service:
try {
    alertNotificationRepository.saveAll(toSave);
} catch (DataIntegrityViolationException e) {
    // Duplicate detected, safe to ignore
    log.debug("Duplicate notification ignored");
}
```

**Implementation Priority:** HIGH

---

### 🟡 P1-6: Soft Delete Incomplete (Events Not Marked)

**File:** `ProcessChainhookPayloadUseCase.java:94-96`

**Problem:**
```java
block.getTransactions().forEach(tx -> {
    tx.markAsDeleted();
    // Comment says: "Events will be cascade deleted via JPA orphanRemoval"
    // BUT: orphanRemoval only physically deletes, not soft delete
});
```

**Risk:**
- Rollback marks block + transactions as deleted
- But `TransactionEvent` entities NOT marked deleted
- Query services return "deleted block" data if events queried directly
- Reorg restore doesn't restore events

**Solution:**
Propagate soft delete to events:
```java
block.getTransactions().forEach(tx -> {
    tx.markAsDeleted();
    tx.getEvents().forEach(event -> event.markAsDeleted());
});

// Add @Where clause to all entities:
@Where(clause = "deleted = false")
public class TransactionEvent { ... }
```

**Implementation Priority:** MEDIUM

---

## Code Quality Issues (P2) - Maintainability

### 🟢 P2-1: Manual DTO Mapping (Maintainability Debt)

**File:** `ChainhookPayloadParser.java` (multiple mapping methods)

**Problem:**
- Manual field-by-field mapping in parser
- Every new field = update 3+ methods
- Type safety at runtime only
- Verbose, error-prone

**Solution:**
Use **MapStruct 1.5.5.Final**:
```java
@Mapper(componentModel = "spring")
public interface BlockMapper {
    BlockDto toDto(StacksBlock entity);
    StacksBlock toEntity(BlockEventDto dto);
}

// Compile-time code generation, 13x faster than reflection
```

**Implementation Priority:** MEDIUM

---

### 🟢 P2-2: Parser Nonce Always Zero

**File:** `ChainhookPayloadParser.java` (transaction parsing)

**Problem:**
```java
transaction.setNonce(0L); // Hardcoded to 0
```

**Risk:**
- Lost transaction sequencing data
- Address activity analysis broken
- Idempotency based on (sender, nonce) impossible

**Solution:**
```java
if (metadata.getNonce() != null) {
    transaction.setNonce(metadata.getNonce().longValue());
}
```

**Implementation Priority:** MEDIUM

---

### 🟢 P2-3: Missing BlockMetadataDto Fields

**File:** `BlockMetadataDto.java`

**Problem:**
- Missing `burn_block_height`, `burn_block_hash`
- Missing `parent_burn_block_time`, `parent_burn_block_hash`
- Official Hiro docs confirm these fields exist in Chainhook webhooks

**Solution:**
Add missing fields per official Chainhook schema:
```java
public class BlockMetadataDto {
    private Long burnBlockHeight;
    private String burnBlockHash;
    private String stacksBlockHash;
    private Long burnBlockTime;
    private String consensusHash;
}
```

**Implementation Priority:** MEDIUM

---

### 🟢 P2-4: Event Type Stringly-Typed

**File:** `ChainhookPayloadParser.java` (event type switch statements)

**Problem:**
```java
switch (eventType.toUpperCase()) {
    case "FT_TRANSFER" -> ...
    case "NFT_MINT" -> ...
    // Typos caught only at runtime
}
```

**Solution:**
Enum with wire-to-domain mapping:
```java
public enum WireEventType {
    FT_TRANSFER, NFT_MINT, PRINT_EVENT;

    public static EventType toDomain(String wire) {
        return switch (wire.toUpperCase()) {
            case "FT_TRANSFER" -> EventType.FT_TRANSFER;
            // ... centralized mapping
            default -> EventType.UNKNOWN;
        };
    }
}
```

**Implementation Priority:** LOW

---

## Migration Priorities

### Phase 1: Critical Security (Week 1-2)

**Goal:** Eliminate production-blocking vulnerabilities

| Priority | Task | Estimated Time | Depends On |
|----------|------|----------------|------------|
| P0-1 | JWT HS256 → RS256 migration | 2-3 days | - |
| P0-2 | Redis-backed rate limiting | 1-2 days | - |
| P0-3 | Fix filter ordering | 1 hour | - |
| P0-4 | HMAC replay protection | 1 day | - |
| P0-5 | Lock down actuator endpoints | 30 min | - |
| P0-6 | AFTER_COMMIT notification dispatch | 1 day | - |

**Deliverable:** Production-ready security posture

---

### Phase 2: Performance & Data Integrity (Week 3-4)

**Goal:** Scale to high transaction volumes

| Priority | Task | Estimated Time | Depends On |
|----------|------|----------------|------------|
| P1-1 | Immutable DTO caching | 2 days | - |
| P1-2 | IDENTITY → SEQUENCE migration | 1 day + DB migration | - |
| P1-3 | Index-based alert matching | 3 days | P1-1 |
| P1-4 | DB-level cooldown with conditional UPDATE | 1 day | - |
| P1-5 | Idempotency constraints | 1 day | - |
| P1-6 | Complete soft delete propagation | 1 day | - |

**Deliverable:** Sub-second alert matching, zero duplicates

---

### Phase 3: Code Quality (Week 5-6)

**Goal:** Reduce technical debt, improve maintainability

| Priority | Task | Estimated Time | Depends On |
|----------|------|----------------|------------|
| P2-1 | MapStruct integration | 2 days | - |
| P2-2 | Fix parser nonce handling | 1 hour | - |
| P2-3 | Add missing Chainhook fields | 1 day | - |
| P2-4 | Enum-based type mapping | 1 day | - |

**Deliverable:** Clean, type-safe codebase

---

## Tech Stack

### Current Stack

| Component | Technology | Version | Notes |
|-----------|-----------|---------|-------|
| **Backend** | Spring Boot | 3.2.5 | Latest stable |
| **Language** | Java | 17 | LTS |
| **Database** | PostgreSQL | 14+ | JSONB support required |
| **ORM** | Hibernate/JPA | 6.x | Via Spring Boot |
| **Security** | Spring Security | 6.x | JWT + BCrypt |
| **JWT Library** | JJWT | 0.12.5 | Supports RS256 |
| **Cache** | Caffeine | ⚠️ | In-memory only |
| **Rate Limiting** | Bucket4j | 8.x | Needs Redis adapter |
| **Testing** | JUnit 5 + Mockito | - | TestContainers for integration |

### Recommended Upgrades

| Component | From | To | Reason |
|-----------|------|----|----|
| **Cache** | Caffeine (in-memory) | Redis 7.x + Caffeine L1 | Distributed cache for multi-instance |
| **Rate Limiting** | In-memory Map | Bucket4j + Redis | Distributed rate limiting |
| **Mapping** | Manual | MapStruct 1.5.5 | 13x faster, compile-time safety |
| **Observability** | Logs only | Micrometer + Prometheus | Metrics for alert matching, cooldown races |

---

## Code Style & Patterns

### Correct Patterns (Keep These)

✅ **Clean Architecture separation**
- Domain entities in `/domain/model`
- Use cases in `/application/usecase`
- Infrastructure in `/infrastructure`

✅ **Stateless JWT with BCrypt(12)**
- Proper strength password hashing
- CSRF disabled for stateless API (correct per Spring Security docs)

✅ **HMAC constant-time comparison**
```java
MessageDigest.isEqual(provided.getBytes(), expected.getBytes())
```

✅ **Optimistic locking with @Version**
```java
@Version
private Long version = 0L;
```

✅ **Soft delete pattern**
```java
private Boolean deleted = false;
private Instant deletedAt;
```

### Anti-Patterns to Fix

❌ **Caching mutable entities**
```java
// BAD:
@Cacheable("alertRules")
public List<AlertRule> getRules() { ... }

// GOOD:
@Cacheable("ruleSnapshots")
public List<RuleSnapshot> getRuleSnapshots() { ... }
```

❌ **Side effects before transaction commit**
```java
// BAD:
@Transactional
public void process() {
    save(entity);
    sendEmail(); // Can send even if commit fails
}

// GOOD:
@TransactionalEventListener(phase = AFTER_COMMIT)
public void handleCommit(EntitySavedEvent event) {
    sendEmail();
}
```

❌ **TOCTOU (Time-of-Check-Time-of-Use) race conditions**
```java
// BAD:
if (!rule.isInCooldown()) {  // Check
    rule.markAsTriggered();   // Use (race window)
}

// GOOD:
int updated = repo.markIfNotInCooldown(id, now); // Atomic
if (updated > 0) { ... }
```

---

## Key References

### Official Documentation

- **Spring Security 6.x:** https://docs.spring.io/spring-security/reference/
- **Spring Framework Transaction Management:** https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- **Hibernate Performance Tuning:** https://vladmihalcea.com/tutorials/hibernate/
- **JPA Best Practices:** https://thorben-janssen.com/tips-to-boost-your-hibernate-performance/

### Security Best Practices

- **OWASP JWT Cheat Sheet for Java:** https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html
- **OWASP API Security Top 10:** https://owasp.org/API-Security/
- **Bucket4j Distributed Rate Limiting:** https://bucket4j.com/

### Blockchain-Specific

- **Hiro Chainhook Documentation:** https://docs.hiro.so/chainhook
- **Stacks Blockchain API:** https://github.com/hirosystems/stacks-blockchain-api
- **Stacks Transaction Format:** https://docs.stacks.co/

### Performance & Testing

- **MapStruct Performance Benchmarks:** https://www.baeldung.com/java-performance-mapping-frameworks
- **TestContainers PostgreSQL:** https://www.testcontainers.org/modules/databases/postgres/
- **Spring Boot Testing Best Practices:** https://rieckpil.de/guide-to-testing-spring-boot-applications-with-mockmvc/

---

## Implementation Checklist

### Before Starting Any Phase

- [ ] Create feature branch: `claude/phase-N-description-{sessionId}`
- [ ] Read relevant sections of this document
- [ ] Review OWASP/Spring Security docs for security tasks
- [ ] Set up TestContainers for integration tests

### After Completing Each Task

- [ ] Write unit tests (70% of test suite)
- [ ] Write integration tests with TestContainers (20%)
- [ ] Update this document if patterns change
- [ ] Commit with clear message: `fix(security): migrate JWT to RS256`
- [ ] Run full test suite
- [ ] Check for regressions in related features

### Phase Completion

- [ ] All P0/P1/P2 tasks for phase completed
- [ ] Test coverage >80% for modified code
- [ ] No new security vulnerabilities introduced
- [ ] Performance benchmarks show improvement (if applicable)
- [ ] Create PR with summary of changes
- [ ] Update `PHASE_N_COMPLETION.md` with lessons learned

---

## Quick Reference: File Locations

### Critical Files for P0 Tasks

| Task | Files to Modify |
|------|----------------|
| JWT Migration | `JwtTokenService.java`, `SecurityConfiguration.java`, `application.properties` |
| Rate Limiting | `RateLimitFilter.java` (new Redis-backed impl) |
| Filter Order | `SecurityConfiguration.java:60-62` |
| HMAC Replay | `ChainhookHmacFilter.java` |
| Actuator Lock | `SecurityConfiguration.java:44-50` |
| Notification Dispatch | `ProcessChainhookPayloadUseCase.java`, `NotificationDispatcher.java` |

### Critical Files for P1 Tasks

| Task | Files to Modify |
|------|----------------|
| Cache DTOs | `AlertMatchingService.java`, new `RuleSnapshot.java` |
| SEQUENCE Migration | All entities with `@GeneratedValue`, Flyway migrations |
| Alert Index | `AlertMatchingService.java`, new `RuleIndex.java` |
| Cooldown Fix | `AlertRuleRepository.java`, `AlertMatchingService.java` |
| Idempotency | `AlertNotification.java`, migration for unique constraint |
| Soft Delete | `ProcessChainhookPayloadUseCase.java`, `TransactionEvent.java` |

---

## Success Metrics

### Phase 1 (Security)

- [ ] Zero P0 vulnerabilities in security audit
- [ ] Rate limiting works across 3+ instances
- [ ] JWT tokens use RS256 with 4096-bit keys
- [ ] No webhooks accepted without valid timestamp

### Phase 2 (Performance)

- [ ] Alert matching <100ms for 1000 rules
- [ ] Batch insert 10,000 transactions in <10s
- [ ] Zero duplicate notifications under load test
- [ ] Zero race conditions in cooldown logic

### Phase 3 (Code Quality)

- [ ] 80%+ test coverage
- [ ] Zero manual DTO mapping code
- [ ] All parser fields mapped correctly
- [ ] Zero runtime type errors in event handling

---

**Last Updated:** {current_date}
**Version:** 1.0.0
**Maintained By:** Claude Code Agent
**Status:** 🟡 In Progress (Phase 0: Setup Complete)

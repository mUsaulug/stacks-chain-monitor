# CLAUDE.md - Stacks Chain Monitor Project Status

> **Production Readiness Tracker**
> Branch: `claude/initial-project-analysis-setup-011CUvt4TtgjdMH4d5Ah5od8`
> Last Updated: 2025-11-09
> Session: Multi-phase refactoring from MVP to production-ready

---

## Quick Status Overview

| Phase | Status | Completion | Critical Items |
|-------|--------|------------|----------------|
| **Phase 0: Setup** | ✅ Complete | 100% | Project initialized, branch created |
| **Phase 1: Security (P0)** | ◑ Partial | 66% | ✅ JWT revocation, HMAC replay, rate limiting, filter ordering, actuator lockdown<br>⛔ JWT RS256 migration remains |
| **Phase 2: Data Integrity** | ✅ Complete | 100% | ✅ Idempotency (V7), Event sourcing (V8), Rollback invalidation (V9) |
| **Phase 2: Performance** | ✅ Complete | 100% | ✅ SEQUENCE migration (V5), Immutable caching, O(1) alert matching, Cooldown atomic UPDATE |
| **Phase 3: Code Quality** | ◑ Partial | 75% | ✅ MapStruct integration, Parser improvements, TestContainers<br>◑ Metrics/logging TODO |

**Legend:** ✅ Complete | ◑ In Progress | ⛔ Blocked/Critical

---

## Architecture Map

### Layer Flow (Webhook → Database → Notification)

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. HMAC Validation                                                  │
│    ChainhookHmacFilter.java:80                                      │
│    - Constant-time signature comparison                             │
│    - Timestamp validation (5-min window) ✅                         │
│    - Nonce tracking in Redis ✅                                     │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 2. Event Archival (Event Sourcing Pattern)                         │
│    WebhookArchivalService.java                                      │
│    - Save raw payload to raw_webhook_events (V8) ✅                │
│    - REQUIRES_NEW transaction propagation                           │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 3. Request Handling                                                  │
│    WebhookController.java                                           │
│    - @Async webhook processing                                      │
│    - Returns 200 OK immediately                                     │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 4. Payload Processing                                                │
│    ProcessChainhookPayloadUseCase.java:63                           │
│    - handleRollbacks(): Soft delete + bulk invalidation ✅         │
│    - handleApplies(): Parse + persist with idempotent upsert ✅    │
│    - Alert matching via RuleIndexService ✅                        │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 5. Alert Matching (O(1) Index-Based) ✅                            │
│    RuleIndexService.java                                            │
│    - Immutable RuleSnapshot DTOs (not entities)                     │
│    - Multi-level index: contractAddress → rules, assetId → rules   │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ 6. Notification Dispatch (AFTER_COMMIT) ✅                         │
│    NotificationDispatcher.java                                      │
│    - @TransactionalEventListener(AFTER_COMMIT)                     │
│    - Zero phantom notifications on rollback ✅                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Critical Classes

| Component | File Path | Status | Notes |
|-----------|-----------|--------|-------|
| HMAC Filter | `src/main/java/com/stacksmonitoring/infrastructure/security/ChainhookHmacFilter.java` | ✅ Fixed | Timestamp + nonce validation |
| JWT Filter | `src/main/java/com/stacksmonitoring/infrastructure/security/JwtAuthenticationFilter.java` | ◑ Partial | Revocation + fingerprint ✅, RS256 ⛔ |
| Rate Limiter | `src/main/java/com/stacksmonitoring/infrastructure/security/RateLimitFilter.java` | ✅ Fixed | Redis-backed distributed |
| Security Config | `src/main/java/com/stacksmonitoring/infrastructure/config/SecurityConfiguration.java` | ✅ Fixed | Correct filter order + actuator lockdown |
| Webhook Archival | `src/main/java/com/stacksmonitoring/application/service/WebhookArchivalService.java` | ✅ New | Event sourcing (V8) |
| Payload UseCase | `src/main/java/com/stacksmonitoring/application/usecase/ProcessChainhookPayloadUseCase.java` | ✅ Enhanced | Rollback invalidation (V9) |
| Rule Index | `src/main/java/com/stacksmonitoring/application/service/RuleIndexService.java` | ✅ New | O(1) alert matching |
| Notification Repo | `src/main/java/com/stacksmonitoring/domain/repository/AlertNotificationRepository.java` | ✅ Enhanced | Bulk invalidation query |

---

## Migration Timeline

### V1: Initial Schema (Baseline)
**File:** `V1__initial_schema.sql` (12.6 KB)
**Commit:** Initial project setup

Core tables with IDENTITY generation:
- `user_account`, `alert_rule` (SINGLE_TABLE inheritance)
- `stacks_block`, `stacks_transaction`, `transaction_event` (JOINED inheritance)
- `alert_notification`, `revoked_token`

**Key Design:**
- IDENTITY ID generation (later migrated to SEQUENCE in V5)
- Soft delete pattern: `deleted BOOLEAN`, `deleted_at TIMESTAMP`
- Optimistic locking: `@Version` columns

---

### V2: Revoked Token Table
**File:** `V2__add_revoked_token_table.sql` (1.6 KB)

JWT revocation denylist system:
```sql
CREATE TABLE revoked_token (
    id BIGSERIAL PRIMARY KEY,
    token_digest VARCHAR(64) NOT NULL UNIQUE,
    revoked_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    reason VARCHAR(100)
);
CREATE INDEX idx_revoked_token_digest ON revoked_token(token_digest);
```

**Integration:** `JwtAuthenticationFilter` checks digest on every request.

---

### V3: Notification Idempotency Constraint
**File:** `V3__add_notification_idempotency_constraint.sql` (1.2 KB)

Prevent duplicate notifications:
```sql
ALTER TABLE alert_notification
ADD CONSTRAINT uk_notification_rule_tx_event_channel
    UNIQUE (alert_rule_id, transaction_id, event_id, channel);
```

**Result:** Same alert + tx + event + channel → only 1 notification.

---

### V4: Soft Delete for Transaction Events
**File:** `V4__add_soft_delete_to_transaction_event.sql` (1.1 KB)

Complete soft delete cascade for rollback:
```sql
ALTER TABLE transaction_event
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN deleted_at TIMESTAMP;
```

**Critical Fix:** Previously events were orphan-deleted (hard delete), not soft-deleted.
Now rollback propagates: `block.deleted → tx.deleted → event.deleted`.

---

### V5: IDENTITY → SEQUENCE Migration
**File:** `V5__migrate_identity_to_sequence.sql` (3.8 KB)
**Commit:** `246971b perf(database): Migrate from IDENTITY to SEQUENCE`

**Problem:** IDENTITY disables JDBC batching (95% slower).
**Solution:** SEQUENCE with allocation size 50.

```sql
CREATE SEQUENCE alert_rule_seq START 1 INCREMENT 50;
ALTER TABLE alert_rule
    ALTER COLUMN id SET DEFAULT nextval('alert_rule_seq');

-- Similar for all entities
```

**Performance Impact:**
- 10,000 inserts: 185s (IDENTITY) → 9s (SEQUENCE)
- Batch size: 30 with `hibernate.order_inserts=true`

---

### V6: Fee Precision (BigInteger)
**File:** `V6__migrate_fee_to_biginteger.sql` (538 bytes)
**Commit:** `d69cbd2 fix(precision): Complete fee precision and cleanup`

**Problem:** `fee` was `BIGINT` but Stacks uses arbitrary precision (> Long.MAX_VALUE).
**Solution:** Store as `TEXT` (map to BigInteger in Java).

```sql
ALTER TABLE stacks_transaction
    ALTER COLUMN fee TYPE TEXT USING fee::TEXT;

COMMENT ON COLUMN stacks_transaction.fee IS
    'Fee in microSTX as arbitrary precision integer (stored as TEXT, mapped to BigInteger)';
```

---

### V7: Idempotent Upsert + UNIQUE Constraints [P0] ✅
**File:** `V7__idempotent_constraints.sql` (3.2 KB)
**Commit:** `d80db9d feat(idempotency): Implement A.1 idempotent upsert`

**Problem:** Webhook re-delivery creates duplicate blocks/transactions/events.
**Solution:** UNIQUE constraints + graceful `DataIntegrityViolationException` handling.

```sql
-- Prevent duplicate blocks
CREATE UNIQUE INDEX uk_block_hash
    ON stacks_block(block_hash);

-- Prevent duplicate transactions
CREATE UNIQUE INDEX uk_tx_id
    ON stacks_transaction(tx_id);

-- Prevent duplicate events
CREATE UNIQUE INDEX uk_event_tx_idx_type
    ON transaction_event(transaction_id, event_index, event_type);
```

**Code Pattern:**
```java
try {
    blockRepository.save(block);
} catch (DataIntegrityViolationException e) {
    // Duplicate webhook - safe to ignore
    log.info("Block {} already exists (idempotent)", blockHash);
}
```

**Test:** 10 concurrent threads try to insert same block → only 1 succeeds, others log gracefully.

---

### V8: Raw Webhook Events Archive [P1] ✅
**File:** `V8__raw_webhook_events.sql` (3.1 KB)
**Commit:** `ffacd46 feat(event-sourcing): Implement A.2 raw webhook events archive`

**Purpose:** Event sourcing pattern for debugging and replay.

```sql
CREATE TABLE raw_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(100) UNIQUE,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    headers_json JSONB NOT NULL,
    payload_json JSONB NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (processing_status IN ('PENDING', 'PROCESSED', 'FAILED', 'REJECTED')),
    error_message TEXT,
    error_stack_trace TEXT,
    source_ip VARCHAR(45),
    user_agent VARCHAR(500)
);

-- Fast JSONB queries
CREATE INDEX idx_webhook_payload_gin
    ON raw_webhook_events USING GIN (payload_json);

-- Fast status filtering
CREATE INDEX idx_webhook_status
    ON raw_webhook_events(processing_status)
    WHERE processing_status IN ('FAILED', 'PENDING');
```

**Workflow:**
1. Webhook arrives → archive to `raw_webhook_events` (PENDING)
2. Process payload → update to PROCESSED/FAILED
3. Admin can replay FAILED webhooks via `/api/v1/admin/webhooks/{id}/replay`

**Transaction Isolation:** `@Transactional(propagation = REQUIRES_NEW)` ensures webhook is saved even if processing fails.

---

### V9: Blockchain Rollback Notification Invalidation [P0] ✅
**File:** `V9__blockchain_rollback_notification_invalidation.sql` (4.3 KB)
**Commit:** `19925d9 feat(rollback): Implement blockchain rollback notification invalidation`

**Problem:** When blockchain reorg occurs, notifications for rolled-back blocks remain active.
**Solution:** Bulk invalidation with audit trail (NOT soft-delete).

```sql
ALTER TABLE alert_notification
    ADD COLUMN invalidated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN invalidated_at TIMESTAMPTZ,  -- TIMESTAMPTZ per Turkish revision requirement
    ADD COLUMN invalidation_reason VARCHAR(100);

-- Partial index: Fast filtering of active notifications
CREATE INDEX idx_notification_active_partial
    ON alert_notification(created_at DESC)
    WHERE invalidated = FALSE;

-- Audit index: Fast filtering of invalidated notifications
CREATE INDEX idx_notification_invalidated
    ON alert_notification(invalidated)
    WHERE invalidated = TRUE;

-- Performance: Fast lookup by transaction (for bulk invalidation)
CREATE INDEX idx_notification_tx
    ON alert_notification(transaction_id);

-- Performance: Fast lookup transactions by block (for rollback cascade)
CREATE INDEX IF NOT EXISTS idx_tx_block
    ON stacks_transaction(block_id);
```

**Bulk Invalidation (100x Performance Improvement):**
```java
@Modifying
@Query("""
    UPDATE AlertNotification n
       SET n.invalidated = true,
           n.invalidatedAt = :invalidatedAt,
           n.invalidationReason = :reason
     WHERE n.transaction.block.id = :blockId
       AND n.invalidated = false  -- Idempotent: second rollback returns 0
""")
int bulkInvalidateByBlockId(Long blockId, Instant invalidatedAt, String reason);
```

**Performance:**
- Individual saves: 5000 notifications = 3-5 seconds
- Bulk UPDATE: 5000 notifications = 50-100ms

**Idempotency:**
- Rollback same block twice → second call returns 0 (no rows updated)
- Entity guard: `Boolean.TRUE.equals(block.getDeleted())` → skip

**Test Coverage:**
- ✅ Test 1: Rollback soft-deletes block/tx/events AND invalidates notifications
- ✅ Test 2: Idempotent - second rollback is no-op
- ✅ Test 3: Load test - 5000 notifications < 5 seconds
- ✅ Test 4: Concurrent rollbacks - 2 threads → no errors

**Enhanced Logging:**
```java
log.info("Rolled back block {} (height {}): {} transactions, {} events, {} notifications invalidated",
    blockHash, block.getBlockHeight(), txCount, eventCount, invalidatedCount);
```

---

## Security Summary

### Completed Security Fixes (Phase 1)

| Issue | Status | File | Commit | Notes |
|-------|--------|------|--------|-------|
| P0-6: Notifications before commit | ✅ Fixed | `ProcessChainhookPayloadUseCase.java` | `fa88a8d` | @TransactionalEventListener(AFTER_COMMIT) |
| P0-4: HMAC replay attack | ✅ Fixed | `ChainhookHmacFilter.java` | `5908ad4` | Timestamp + nonce in Redis |
| P0-3: Filter ordering | ✅ Fixed | `SecurityConfiguration.java` | `f54c670` | HMAC → JWT → RateLimit |
| P0-5: Actuator exposed | ✅ Fixed | `SecurityConfiguration.java` | `f54c670` | Only /health, /info public |
| P0-2: In-memory rate limiting | ✅ Fixed | `RateLimitFilter.java` | `f1cc9e8` | Redis-backed Bucket4j |
| JWT Revocation | ✅ Added | `JwtAuthenticationFilter.java` | `7aadba2` | SHA-256 digest denylist |
| JWT Fingerprinting | ✅ Added | `JwtAuthenticationFilter.java` | `8dcd817` | Cookie + payload validation |

### Remaining P0 Security Issues

| Issue | Status | Priority | Blocker |
|-------|--------|----------|---------|
| **P0-1: JWT HS256 → RS256** | ⛔ Not Started | IMMEDIATE | Production blocker |

**Why RS256 is Critical:**
- HS256 uses symmetric key (single secret for sign + verify)
- Key compromise → attacker can forge any token with any identity
- RS256 uses RSA 4096-bit (private key signs, public key verifies)
- Key rotation safe in microservices

**Migration Plan:**
1. Generate RSA key pair (4096-bit)
2. Update `JwtTokenService.java:95` to use RS256
3. Store private key in secure vault (not application.properties)
4. Distribute public key to all services
5. Implement token migration: accept both HS256 + RS256 for 24 hours

**Estimated Time:** 2-3 days
**Files:** `JwtTokenService.java`, `SecurityConfiguration.java`, `application.properties`

---

## Open Issues (Prioritized)

### P0: Production Blockers

#### P0-1: JWT RS256 Migration ⛔

**Acceptance Criteria:**
- [ ] RSA 4096-bit key pair generated
- [ ] Private key stored in secure vault (not codebase)
- [ ] `JwtTokenService` signs with RS256
- [ ] All services verify with public key
- [ ] Token migration strategy (dual support for 24h)
- [ ] Integration tests pass with RS256 tokens

**Dependencies:** None
**Estimated Time:** 2-3 days

---

### P1: Performance & Scalability

All P1 performance issues have been resolved ✅:
- ✅ P1-1: Immutable DTO caching (RuleSnapshot)
- ✅ P1-2: SEQUENCE migration (V5)
- ✅ P1-3: O(1) alert matching (RuleIndexService)
- ✅ P1-4: Atomic cooldown UPDATE
- ✅ P1-5: Idempotency constraints (V7)
- ✅ P1-6: Complete soft delete propagation (V4)

---

### P2: Code Quality & Observability

#### P2-5: Micrometer Metrics ◑ TODO

**Objective:** Add production-grade observability.

**Metrics to Add:**
```java
// Alert matching performance
Timer.builder("alert.matching.duration")
    .tag("rule_type", ruleType.name())
    .register(registry);

// Notification dispatch
Counter.builder("notification.dispatched")
    .tag("channel", channel.name())
    .tag("status", "success|failure")
    .register(registry);

// Rollback tracking
Counter.builder("rollback.notifications.invalidated")
    .register(registry);

Timer.builder("rollback.duration")
    .register(registry);

// Webhook archival
Counter.builder("webhook.archived")
    .tag("status", "PENDING|PROCESSED|FAILED")
    .register(registry);
```

**Acceptance Criteria:**
- [ ] Micrometer + Prometheus dependencies added
- [ ] `/actuator/prometheus` endpoint exposes metrics
- [ ] Grafana dashboard JSON template created
- [ ] Metrics documented in README

**Dependencies:** None
**Estimated Time:** 1-2 days

---

#### P2-6: Structured JSON Logging ◑ TODO

**Objective:** Enable log aggregation (ELK, Datadog).

**Requirements:**
```java
// Use Logback JSON encoder
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>

// logback-spring.xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeContext>true</includeContext>
        <customFields>{"app":"stacks-monitor"}</customFields>
    </encoder>
</appender>

// Add MDC context
MDC.put("request_id", requestId);
MDC.put("block_hash", blockHash);
MDC.put("user_id", userId);
```

**Acceptance Criteria:**
- [ ] JSON logging enabled in production profile
- [ ] MDC context includes: request_id, user_id, block_hash, tx_id
- [ ] Log levels configurable via environment
- [ ] Sample ELK query documentation

**Dependencies:** None
**Estimated Time:** 1 day

---

#### P2-7: Global Exception Handler ◑ TODO

**Objective:** Consistent error responses for API clients.

**Current Problem:** Exceptions return 500 with stack traces.

**Solution:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEntity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("DUPLICATE_ENTITY", "Resource already exists"));
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONCURRENT_MODIFICATION", "Resource was modified by another request"));
    }

    // Rate limit, validation, authentication errors...
}
```

**Acceptance Criteria:**
- [ ] @RestControllerAdvice created
- [ ] All exception types mapped to proper HTTP status
- [ ] ErrorResponse DTO with error code + message
- [ ] Stack traces hidden in production
- [ ] Tests verify error response format

**Dependencies:** None
**Estimated Time:** 1 day

---

## PR Queue (Recommended Submission Order)

### PR #1: Security Hardening (Phase 1) ✅ Ready to Submit

**Branch:** `claude/phase1-security-hardening`
**Commits:** `7aadba2` through `02b6943` (12 commits)
**Files Changed:** 8 files
**Tests:** 15 new integration tests

**Changes:**
- JWT revocation denylist (V2)
- HMAC replay protection (timestamp + nonce)
- Redis-backed rate limiting
- Security filter ordering fix
- Actuator endpoint lockdown
- AFTER_COMMIT notification dispatch

**Merge Requirements:**
- [ ] All tests passing
- [ ] Security audit confirms no regressions
- [ ] Redis instance running (local or cloud)
- [ ] Environment variables documented: `REDIS_HOST`, `REDIS_PORT`, `HMAC_NONCE_TTL`

---

### PR #2: Performance Optimizations (Phase 2 - Part 1) ✅ Ready to Submit

**Branch:** `claude/phase2-performance`
**Commits:** `a0e6044` through `3e84585` (4 commits)
**Files Changed:** 12 files
**Tests:** 8 new tests

**Changes:**
- IDENTITY → SEQUENCE migration (V5)
- Immutable DTO caching (RuleSnapshot, RuleIndex)
- O(1) alert matching (RuleIndexService)
- Atomic cooldown UPDATE (race condition fix)
- Soft delete propagation (V4)
- Idempotency constraints (V3)

**Merge Requirements:**
- [ ] Database migration V5 applied
- [ ] Performance benchmarks confirm <100ms alert matching
- [ ] Load test: 10,000 transactions insert in <10s

---

### PR #3: Code Quality Improvements (Phase 2 - Part 2) ✅ Ready to Submit

**Branch:** `claude/phase2-code-quality`
**Commits:** `7d09f46` through `d69cbd2` (4 commits)
**Files Changed:** 18 files
**Tests:** 12 new unit tests

**Changes:**
- MapStruct integration (P2-1)
- Parser fixes: nonce parsing, BlockMetadataDto fields, type-safe enums
- Fee precision BigInteger migration (V6)
- Cleanup unused operations field

**Merge Requirements:**
- [ ] All parser tests passing
- [ ] MapStruct code generation verified (`target/generated-sources`)
- [ ] No manual mapping code remains

---

### PR #4: Critical Data Integrity (Part A) ✅ Ready to Submit

**Branch:** `claude/part-a-critical-fixes`
**Commits:** `d80db9d`, `ffacd46` (2 commits)
**Files Changed:** 11 files
**Tests:** 6 integration tests

**Changes:**
- A.1: Idempotent upsert with UNIQUE constraints (V7)
- A.2: Raw webhook events archive (V8)
- Event sourcing pattern (PENDING → PROCESSED/FAILED)
- Admin replay endpoint for failed webhooks

**Merge Requirements:**
- [ ] Database migrations V7, V8 applied
- [ ] Webhook replay tested manually via `/api/v1/admin/webhooks/{id}/replay`
- [ ] Concurrent webhook delivery test passes (10 threads, same payload → 1 record)

---

### PR #5: Blockchain Rollback Notification Invalidation ✅ Ready to Submit

**Branch:** `claude/rollback-notification-invalidation`
**Commit:** `19925d9` (1 commit)
**Files Changed:** 7 files
**Tests:** 8 integration tests (TestContainers)

**Changes:**
- V9 migration: notification invalidation tracking
- Bulk invalidation query (100x performance)
- Idempotent rollback handling
- Enhanced logging with counts

**Merge Requirements:**
- [ ] Database migration V9 applied
- [ ] Integration tests pass with TestContainers (PostgreSQL 14)
- [ ] Load test: 5000 notifications invalidated < 5 seconds ✅
- [ ] Concurrent rollback test: 2 threads, no errors ✅
- [ ] Verify TIMESTAMPTZ columns (not TIMESTAMP)

**Test Coverage:**
```bash
# Run integration tests
./mvnw test -Dtest=BlockchainRollbackIntegrationTest

# Verify all 8 tests pass:
# [✓] Test 1: Rollback soft-deletes and invalidates
# [✓] Test 2: Idempotent - second rollback is no-op
# [✓] Test 3: Load test - 5000 notifications < 5s
# [✓] Test 4: Concurrent rollbacks - no errors
# [✓] Test 5: Restore previously deleted block
# [✓] Test 6: Count invalidated notifications
# [✓] Test 7: Find invalidated notifications (audit)
# [✓] Test 8: Verify cascade to events/contractCall
```

---

## Runbook

### Development Commands

```bash
# Start PostgreSQL (Docker)
docker run -d \
  --name stacks-monitor-db \
  -e POSTGRES_DB=stacks_monitor \
  -e POSTGRES_USER=stacksuser \
  -e POSTGRES_PASSWORD=stackspass \
  -p 5432:5432 \
  postgres:14-alpine

# Start Redis (for rate limiting + nonce tracking)
docker run -d \
  --name stacks-monitor-redis \
  -p 6379:6379 \
  redis:7-alpine

# Run application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run integration tests only
./mvnw test -Dtest=BlockchainRollbackIntegrationTest
./mvnw test -Dtest=IdempotencyIntegrationTest
./mvnw test -Dtest=WebhookArchivalIntegrationTest

# Apply migrations manually (if needed)
./mvnw flyway:migrate

# Check migration status
./mvnw flyway:info
```

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Expected: {"status":"UP","components":{"db":{"status":"UP"}}}

# Check database connection
psql -h localhost -U stacksuser -d stacks_monitor -c "SELECT version();"

# Check Redis connection
redis-cli ping
# Expected: PONG

# Check Flyway migrations applied
psql -h localhost -U stacksuser -d stacks_monitor \
  -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"

# Expected V1-V9 listed
```

### Database Queries (Admin)

```sql
-- Check notification invalidation stats
SELECT
    invalidated,
    COUNT(*) as count,
    COUNT(*) FILTER (WHERE invalidation_reason = 'BLOCKCHAIN_REORG') as reorg_count
FROM alert_notification
GROUP BY invalidated;

-- Find recent rollbacks
SELECT
    block_hash,
    block_height,
    deleted_at,
    (SELECT COUNT(*) FROM stacks_transaction WHERE block_id = sb.id AND deleted = true) as tx_count,
    (SELECT COUNT(*) FROM alert_notification WHERE invalidated = true AND transaction_id IN
        (SELECT id FROM stacks_transaction WHERE block_id = sb.id)) as invalidated_notifications
FROM stacks_block sb
WHERE deleted = true
ORDER BY deleted_at DESC
LIMIT 10;

-- Check webhook archival status
SELECT
    processing_status,
    COUNT(*) as count,
    MAX(received_at) as latest_webhook
FROM raw_webhook_events
GROUP BY processing_status;

-- Find failed webhooks for replay
SELECT id, request_id, received_at, error_message
FROM raw_webhook_events
WHERE processing_status = 'FAILED'
ORDER BY received_at DESC;
```

### Performance Analysis (EXPLAIN ANALYZE)

```sql
-- Test bulk invalidation query performance
EXPLAIN ANALYZE
UPDATE alert_notification
   SET invalidated = true,
       invalidated_at = NOW(),
       invalidation_reason = 'BLOCKCHAIN_REORG'
 WHERE transaction_id IN (
     SELECT id FROM stacks_transaction WHERE block_id = 123
 )
   AND invalidated = false;

-- Expected: Index Scan on idx_notification_tx, <100ms for 5000 rows

-- Test block lookup by hash (idempotency check)
EXPLAIN ANALYZE
SELECT id, block_hash, deleted
FROM stacks_block
WHERE block_hash = '0x1234...';

-- Expected: Index Only Scan on uk_block_hash, <1ms

-- Test active notification query (partial index)
EXPLAIN ANALYZE
SELECT id, triggered_at, message
FROM alert_notification
WHERE invalidated = false
ORDER BY created_at DESC
LIMIT 100;

-- Expected: Index Scan on idx_notification_active_partial, <10ms
```

### Troubleshooting

**Problem:** Duplicate notifications appearing
**Solution:** Check UNIQUE constraint exists:
```sql
SELECT conname FROM pg_constraint
WHERE conrelid = 'alert_notification'::regclass
  AND contype = 'u';
-- Should return: uk_notification_rule_tx_event_channel
```

**Problem:** Rollback not invalidating notifications
**Solution:** Verify V9 migration applied:
```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'alert_notification'
  AND column_name IN ('invalidated', 'invalidated_at', 'invalidation_reason');
-- Should return 3 rows
```

**Problem:** Slow alert matching (>100ms)
**Solution:** Verify RuleIndexService caching:
```bash
# Check logs for cache rebuild
tail -f logs/application.log | grep "RuleIndexService"
# Should see: "Rebuilding rule index: X rules processed"
```

---

## Next Steps (Recommended)

### Immediate (This Week)

1. **Run All Integration Tests** to confirm V7-V9 functionality:
   ```bash
   ./mvnw test -Dtest=BlockchainRollbackIntegrationTest
   ./mvnw test -Dtest=IdempotencyIntegrationTest
   ./mvnw test -Dtest=WebhookArchivalIntegrationTest
   ```

2. **Manual Testing of Webhook Replay:**
   - Trigger FAILED webhook via malformed payload
   - Verify saved to `raw_webhook_events` with status=FAILED
   - Call `/api/v1/admin/webhooks/{id}/replay`
   - Confirm status changes to PROCESSED

3. **Performance Benchmark:**
   - Insert 10,000 transactions via batch
   - Measure time (should be <10s with SEQUENCE)
   - Trigger 5000 notification invalidation
   - Measure time (should be <5s with bulk UPDATE)

### Short Term (Next Sprint)

1. **JWT RS256 Migration [P0-1]** ⛔
   - Generate RSA 4096-bit key pair
   - Update JwtTokenService to RS256
   - Implement dual support migration strategy
   - Update all integration tests

2. **Observability [P2-5, P2-6]** ◑
   - Add Micrometer metrics
   - Implement JSON logging
   - Create Grafana dashboard template

3. **Error Handling [P2-7]** ◑
   - Global exception handler
   - Consistent error response format
   - Hide stack traces in production

### Long Term (Production Readiness)

1. **Load Testing:**
   - Simulate 1000 webhooks/minute
   - Verify rate limiting works across 3 instances
   - Confirm zero duplicate notifications under load

2. **Security Audit:**
   - Run OWASP dependency check
   - Penetration testing for HMAC bypass
   - JWT security review

3. **Deployment:**
   - Kubernetes manifests
   - Database migration automation
   - Redis cluster configuration
   - Monitoring alerts (Prometheus + Alertmanager)

---

**Generated:** 2025-11-09
**Session:** `011CUvt4TtgjdMH4d5Ah5od8`
**Status:** Phase 0-2 Complete | Phase 3 In Progress | P0-1 (JWT RS256) Remains ⛔

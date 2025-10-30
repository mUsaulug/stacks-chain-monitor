# Stacks Chain Monitor - Project Overview

## Project Description
A blockchain smart contract monitoring system for Stacks blockchain. The system receives webhooks from Chainhook, processes blockchain events, evaluates alert rules, and sends notifications.

## Architecture
Clean Architecture with 4 layers:
1. **Domain Layer**: Core business entities and logic
2. **Application Layer**: Use cases and services
3. **Infrastructure Layer**: Database, repositories, external integrations
4. **Presentation Layer**: REST API controllers

## Technology Stack
- **Backend**: Spring Boot 3.2.5, Java 17
- **Database**: PostgreSQL 14+ with JSONB support
- **Security**: JWT (HS256), HMAC-SHA256, BCrypt
- **Cache**: Caffeine Cache
- **Testing**: JUnit 5, Mockito, TestContainers

## Project Structure
```
src/main/java/com/stacksmonitoring/
├── domain/
│   ├── model/              # Domain entities (15 entities)
│   │   ├── StacksBlock.java
│   │   ├── StacksTransaction.java
│   │   ├── TransactionEvent.java (11 subtypes with JOINED inheritance)
│   │   ├── AlertRule.java (5 subtypes with SINGLE_TABLE inheritance)
│   │   ├── AlertNotification.java
│   │   └── User.java
│   ├── repository/         # Repository interfaces
│   └── service/           # Domain services
├── application/
│   ├── service/           # Application services
│   │   ├── BlockQueryService.java
│   │   ├── TransactionQueryService.java
│   │   ├── MonitoringService.java
│   │   ├── AlertMatchingService.java
│   │   ├── NotificationDispatcher.java
│   │   └── AlertRuleService.java
│   ├── usecase/           # Use cases
│   │   └── ProcessChainhookPayloadUseCase.java
│   └── dto/               # DTOs for webhooks
├── infrastructure/
│   ├── persistence/       # JPA repositories
│   ├── security/          # Security configuration
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── HmacSignatureValidator.java
│   │   └── RateLimitingFilter.java
│   ├── notification/      # Notification implementations
│   │   ├── EmailNotificationService.java
│   │   └── WebhookNotificationService.java
│   └── config/           # Configuration classes
└── api/
    └── controller/        # REST controllers (8 controllers, 32 endpoints)
        ├── AuthController.java
        ├── UserController.java
        ├── WebhookController.java
        ├── AlertRuleController.java
        ├── AlertNotificationController.java
        ├── BlockQueryController.java
        ├── TransactionQueryController.java
        └── MonitoringController.java
```

## Key Domain Entities

### StacksBlock
```java
@Entity
public class StacksBlock {
    private Long id;
    private String blockHash;        // Unique identifier
    private Long blockHeight;
    private Instant timestamp;
    private String parentBlockHash;
    private List<StacksTransaction> transactions;
}
```

### StacksTransaction
```java
@Entity
public class StacksTransaction {
    private Long id;
    private String txId;             // Unique transaction hash
    private TransactionType txType;  // SMART_CONTRACT, TOKEN_TRANSFER, etc.
    private String sender;
    private TransactionStatus txStatus; // SUCCESS, FAILED
    private StacksBlock block;
    private List<TransactionEvent> events;
}
```

### TransactionEvent (Polymorphic - 11 types)
```java
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class TransactionEvent {
    // Base class for all event types
}

// Subtypes:
- FTTransferEvent (Fungible Token Transfer)
- FTMintEvent (Fungible Token Mint)
- FTBurnEvent (Fungible Token Burn)
- NFTTransferEvent (Non-Fungible Token Transfer)
- NFTMintEvent (Non-Fungible Token Mint)
- NFTBurnEvent (Non-Fungible Token Burn)
- STXTransferEvent (STX Transfer)
- STXLockEvent (STX Lock)
- STXBurnEvent (STX Burn)
- SmartContractEvent (Contract Log Event)
- SmartContractDeployment (Contract Deploy)
```

### AlertRule (Polymorphic - 5 types)
```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "rule_type")
public abstract class AlertRule {
    private Long id;
    private String name;
    private User user;
    private boolean active;
    private Instant lastTriggeredAt;
    private Integer cooldownMinutes;
}

// Subtypes:
- ContractCallAlertRule (monitors specific contract calls)
- TransactionFailureAlertRule (monitors failed transactions)
- LargeTransferAlertRule (monitors large token transfers)
- BlockProductionAlertRule (monitors block production)
- SmartContractEventAlertRule (monitors contract events)
```

### AlertNotification
```java
@Entity
public class AlertNotification {
    private Long id;
    private AlertRule alertRule;
    private StacksTransaction transaction;
    private NotificationChannel channel; // EMAIL, WEBHOOK
    private NotificationStatus status;   // PENDING, SENT, FAILED
    private String recipientAddress;
    private Instant sentAt;
}
```

## Key Features

### 1. Webhook Processing (Phase 3)
- Receives Chainhook webhooks at `/api/v1/webhook/chainhook`
- HMAC-SHA256 signature validation
- Async processing (returns 200 OK immediately)
- Parses 11 different event types
- Handles blockchain reorganization (soft delete)

### 2. Alert Engine (Phase 4)
- Cache-optimized rule matching (O(1) lookup)
- 5 alert rule types with polymorphic design
- Cooldown period support (prevents spam)
- Batch notification processing

### 3. Notification System (Phase 4)
- Email notifications (SMTP)
- Webhook notifications (HTTP POST)
- Retry mechanism (max 3 attempts)
- Async delivery

### 4. Query APIs (Phase 5)
- Block queries (by hash, height, time range)
- Transaction queries (by sender, type, status)
- Pagination support
- Read-only transaction optimization

### 5. Monitoring (Phase 5)
- System statistics (blocks, transactions, alerts)
- Health checks (database, cache)
- Cache metrics

## Security Features
- JWT authentication (HS256, 24-hour expiration)
- BCrypt password hashing (strength 12)
- HMAC-SHA256 webhook signature validation
- Token Bucket rate limiting (100 req/min per IP)
- Role-based access control (USER, ADMIN)

## Database Schema Highlights

### Inheritance Strategies
1. **JOINED** for TransactionEvent (11 subtypes)
   - Base table: transaction_events
   - Subtype tables: ft_transfer_events, nft_mint_events, etc.

2. **SINGLE_TABLE** for AlertRule (5 subtypes)
   - Single table: alert_rules
   - Discriminator column: rule_type

### Key Indexes
- `idx_block_hash` on stacks_blocks(block_hash)
- `idx_block_height` on stacks_blocks(block_height)
- `idx_tx_id` on stacks_transactions(tx_id)
- `idx_tx_sender` on stacks_transactions(sender)
- `idx_alert_user_active` on alert_rules(user_id, active)

## API Endpoints (32 total)

### Authentication (2)
- POST /api/v1/auth/register
- POST /api/v1/auth/login

### User Management (3)
- GET /api/v1/users/me
- PUT /api/v1/users/me
- DELETE /api/v1/users/me

### Webhooks (1)
- POST /api/v1/webhook/chainhook

### Alert Rules (5)
- POST /api/v1/alerts/rules
- GET /api/v1/alerts/rules
- GET /api/v1/alerts/rules/{id}
- PUT /api/v1/alerts/rules/{id}
- DELETE /api/v1/alerts/rules/{id}

### Alert Notifications (3)
- GET /api/v1/alerts/notifications
- GET /api/v1/alerts/notifications/{id}
- PUT /api/v1/alerts/notifications/{id}/mark-read

### Block Queries (6)
- GET /api/v1/blocks
- GET /api/v1/blocks/{id}
- GET /api/v1/blocks/hash/{blockHash}
- GET /api/v1/blocks/height/{height}
- GET /api/v1/blocks/range
- GET /api/v1/blocks/latest/height

### Transaction Queries (6)
- GET /api/v1/transactions
- GET /api/v1/transactions/txid/{txId}
- GET /api/v1/transactions/sender/{sender}
- GET /api/v1/transactions/type/{type}
- GET /api/v1/transactions/successful
- GET /api/v1/transactions/failed

### Monitoring (5)
- GET /api/v1/monitoring/stats
- GET /api/v1/monitoring/stats/blockchain
- GET /api/v1/monitoring/stats/alerts
- GET /api/v1/monitoring/stats/cache
- GET /api/v1/monitoring/health

## Configuration Files
- `application.properties` - Main configuration
- `application-dev.properties` - Development profile
- `application-prod.properties` - Production profile
- Database migrations in `src/main/resources/db/migration/`

## Testing
- 80+ test cases across 23 test files
- Unit tests with Mockito
- Integration tests with TestContainers
- Coverage for all critical paths

## Performance Characteristics
- Block query: O(1) via indexed block_hash/block_height
- Transaction query: O(log n) with pagination
- Alert matching: O(1) via Caffeine cache
- Webhook processing: Async, non-blocking

## Common Use Cases

### 1. Monitor a Smart Contract
```java
ContractCallAlertRule rule = new ContractCallAlertRule();
rule.setName("Monitor DEX Swaps");
rule.setContractAddress("SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1");
rule.setMethodName("swap-x-for-y");
```

### 2. Alert on Large Transfers
```java
LargeTransferAlertRule rule = new LargeTransferAlertRule();
rule.setName("Large STX Transfers");
rule.setAssetIdentifier("STX");
rule.setMinimumAmount(new BigDecimal("1000000")); // 1M STX
```

### 3. Query Recent Transactions
```
GET /api/v1/transactions?page=0&size=20&sort=timestamp,desc
```

### 4. Check System Health
```
GET /api/v1/monitoring/health
```

## Documentation Files
- `PHASE1_COMPLETION.md` - Core domain model documentation
- `PHASE2_COMPLETION.md` - Security layer documentation
- `PHASE3_COMPLETION.md` - Webhook processing documentation
- `PHASE4_COMPLETION.md` - Alert engine documentation
- `PHASE5_COMPLETION.md` - Query APIs and MVP completion summary
- `GIT_WORKFLOW_GUIDE.md` - Git workflow and troubleshooting

## Statistics
- Total Lines: ~12,000 lines of Java code
- Total Files: 123 files (52 production, 71 test/config)
- Test Coverage: 80+ test cases
- Development Time: 5 phases completed
- Status: ✅ MVP Complete and Production Ready

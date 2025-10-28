# Stacks Blockchain Smart Contract Monitoring System - MVP

Enterprise-grade monitoring system for Stacks Blockchain smart contracts with real-time transaction processing, flexible alert rules, and multi-channel notifications.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Domain Model](#domain-model)
- [Configuration](#configuration)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)
- [API Documentation](#api-documentation)
- [Monitoring](#monitoring)
- [Assumptions & Design Decisions](#assumptions--design-decisions)
- [Phase Roadmap](#phase-roadmap)

## Overview

This system monitors Stacks Blockchain smart contracts in real-time by processing Chainhook webhook events. It provides a flexible, JSON-based alert rule engine with 5 alert types, multi-channel notifications (Email + Webhook), and enterprise-level security.

### Key Capabilities

- **Real-time Processing**: Process blockchain transactions via Chainhook webhooks
- **Flexible Alerts**: 5 alert rule types with customizable conditions
- **High Performance**: Cache-optimized alert matching with O(1) lookup
- **Secure**: JWT authentication + HMAC webhook validation
- **Scalable**: Production-ready architecture with Redis support
- **Observable**: Prometheus metrics + structured logging

## Features

### Core Features (Phase 1 - ✅ Complete)

- ✅ 9 core domain entities with JPA/Hibernate
- ✅ PostgreSQL database with optimized indexes
- ✅ Flyway database migrations
- ✅ Spring Data JPA repositories
- ✅ Docker Compose setup
- ✅ Comprehensive configuration (dev, test, prod)
- ✅ Unit and integration tests

### Planned Features

- **Phase 2**: JWT authentication, HMAC validation, rate limiting
- **Phase 3**: Webhook processing, transaction persistence
- **Phase 4**: Alert engine, notification system
- **Phase 5**: Query APIs, monitoring, observability

## Architecture

### Clean Architecture Layers

```
┌──────────────────────────────────────┐
│   PRESENTATION LAYER                 │
│   - Controllers, DTOs                │
└──────────────────────────────────────┘
              ↓
┌──────────────────────────────────────┐
│   APPLICATION LAYER                  │
│   - Use Cases, Services              │
└──────────────────────────────────────┘
              ↓
┌──────────────────────────────────────┐
│   DOMAIN LAYER                       │
│   - Entities, Repositories (9 core) │
└──────────────────────────────────────┘
              ↓
┌──────────────────────────────────────┐
│   INFRASTRUCTURE LAYER               │
│   - JPA, Email, Webhook, Config      │
└──────────────────────────────────────┘
```

### Data Flow

```
Chainhook Webhook → HMAC Validation → Transaction Parser
                                             ↓
                                    Transaction Persistence
                                             ↓
                                       Event Extraction
                                             ↓
                              Cache-Indexed Alert Matching
                                             ↓
                                    Notification System
                                    (Email + Webhook)
```

## Technology Stack

- **Java**: 17 (LTS)
- **Framework**: Spring Boot 3.2.5
- **Database**: PostgreSQL 14+ (JSONB support)
- **Persistence**: Spring Data JPA + Hibernate
- **Migration**: Flyway
- **Security**: Spring Security + JWT (RS256) + HMAC-SHA256
- **Cache**: Spring Cache (MVP: ConcurrentHashMap, Prod: Redis)
- **Monitoring**: Micrometer + Prometheus + Actuator
- **Build**: Maven 3.9+
- **Container**: Docker + Docker Compose
- **Testing**: JUnit 5 + Mockito + TestContainers

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.9+
- Docker and Docker Compose
- Git

### Quick Start

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/stacks-chain-monitor.git
   cd stacks-chain-monitor
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Start PostgreSQL with Docker Compose**
   ```bash
   docker-compose up -d postgres
   ```

4. **Build the application**
   ```bash
   mvn clean install
   ```

5. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

6. **Access the application**
   - API: http://localhost:8080
   - Actuator: http://localhost:8080/actuator
   - Health: http://localhost:8080/actuator/health

### Database Setup

The database schema is automatically created by Flyway on application startup.

**Manual database creation** (if needed):
```bash
docker exec -it stacks-monitor-postgres psql -U postgres
CREATE DATABASE stacks_monitor;
\q
```

## Project Structure

```
com.stacksmonitoring/
├── api/
│   ├── controller/          # REST controllers
│   ├── dto/
│   │   ├── request/         # Request DTOs
│   │   ├── response/        # Response DTOs
│   │   └── webhook/         # Chainhook webhook DTOs
│   └── exception/           # Exception handlers
├── application/
│   ├── usecase/             # Business use cases
│   └── service/             # Application services
├── domain/
│   ├── model/
│   │   ├── blockchain/      # Blockchain entities
│   │   ├── monitoring/      # Alert & monitoring entities
│   │   └── user/            # User entities
│   ├── repository/          # Repository interfaces
│   ├── service/             # Domain services
│   └── valueobject/         # Enums and value objects
└── infrastructure/
    ├── persistence/         # JPA implementations
    ├── notification/        # Email & webhook senders
    ├── parser/              # Chainhook payload parser
    └── config/              # Spring configuration
```

## Domain Model

### 9 Core Entities

1. **StacksBlock** - Blockchain block metadata
2. **StacksTransaction** - Transaction data
3. **ContractCall** - Smart contract function calls
4. **ContractDeployment** - Contract deployments
5. **TransactionEvent** - Base event class (11 subtypes)
   - FTTransferEvent, FTMintEvent, FTBurnEvent
   - NFTTransferEvent, NFTMintEvent, NFTBurnEvent
   - STXTransferEvent, STXMintEvent, STXBurnEvent, STXLockEvent
   - SmartContractEvent
6. **User** - System users
7. **MonitoredContract** - User-watched contracts
8. **AlertRule** - Base alert class (5 subtypes)
   - ContractCallAlertRule
   - TokenTransferAlertRule
   - FailedTransactionAlertRule
   - PrintEventAlertRule
   - AddressActivityAlertRule
9. **AlertNotification** - Notification delivery tracking

### Entity Relationships

```
User 1──* MonitoredContract 1──* AlertRule 1──* AlertNotification
                                      ↓
StacksBlock 1──* StacksTransaction ──→ ContractCall
                       ↓                     ↓
                       └──────→ TransactionEvent (polymorphic)
```

### Inheritance Strategies

- **TransactionEvent**: JOINED (each subtype has separate table)
- **AlertRule**: SINGLE_TABLE (all subtypes in one table with discriminator)

## Configuration

### Environment Variables

Required environment variables (see `.env.example`):

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/stacks_monitor
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Security
CHAINHOOK_HMAC_SECRET=your-hmac-secret
JWT_SECRET_KEY=your-256-bit-jwt-key

# Email (SMTP)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password

# Redis (Production)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis_password
```

### Profiles

- **dev** (default): Development with console logging
- **test**: Testing with TestContainers
- **prod**: Production with Redis cache and JSON logging

Activate profile:
```bash
java -jar app.jar --spring.profiles.active=prod
```

## Development

### Running Tests

```bash
# All tests
mvn test

# Unit tests only
mvn test -Dtest=*Test

# Integration tests only
mvn test -Dtest=*IntegrationTest

# With coverage
mvn test jacoco:report
```

### Code Quality

```bash
# Format code
mvn spotless:apply

# Check style
mvn checkstyle:check

# Static analysis
mvn pmd:check
```

### Database Migrations

Create a new migration:
```bash
# Create file: src/main/resources/db/migration/V2__description.sql
```

Migration naming: `V{version}__{description}.sql`

## Testing

### Test Structure

```
src/test/java/
├── domain/              # Entity unit tests
├── application/         # Service unit tests
├── api/                 # Controller tests
└── integration/         # Integration tests with TestContainers
```

### TestContainers

Integration tests use PostgreSQL TestContainers:

```java
@Testcontainers
@DataJpaTest
class RepositoryIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:14-alpine");

    // tests...
}
```

## Deployment

### Docker Build

```bash
# Build image
docker build -t stacks-chain-monitor:latest .

# Run container
docker run -d \
  -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/db \
  -e JWT_SECRET_KEY=your-key \
  stacks-chain-monitor:latest
```

### Docker Compose (Full Stack)

```bash
# Start all services
docker-compose --profile app up -d

# Start with monitoring
docker-compose --profile app --profile monitoring up -d

# View logs
docker-compose logs -f app
```

### Production Checklist

- [ ] Set strong JWT_SECRET_KEY (256-bit)
- [ ] Set strong CHAINHOOK_HMAC_SECRET
- [ ] Configure Redis for caching
- [ ] Set up SMTP for email notifications
- [ ] Configure Prometheus + Grafana
- [ ] Enable HTTPS/TLS
- [ ] Set up log aggregation
- [ ] Configure backups
- [ ] Review rate limits

## API Documentation

### Endpoints (Planned)

#### Authentication
- `POST /api/v1/auth/register` - Register user
- `POST /api/v1/auth/login` - Login (returns JWT)

#### Webhooks
- `POST /api/v1/webhook/chainhook` - Chainhook webhook (HMAC protected)

#### Alerts
- `POST /api/v1/alerts` - Create alert rule
- `GET /api/v1/alerts` - List user's alerts
- `GET /api/v1/alerts/{id}` - Get specific alert
- `PUT /api/v1/alerts/{id}` - Update alert
- `DELETE /api/v1/alerts/{id}` - Delete alert

#### Monitoring
- `GET /api/v1/monitoring/transactions` - Query transactions
- `GET /api/v1/monitoring/transactions/{txId}` - Transaction details
- `GET /api/v1/monitoring/alert-notifications` - Notification history

### OpenAPI/Swagger

Access Swagger UI: http://localhost:8080/swagger-ui.html

## Monitoring

### Metrics

Prometheus metrics exposed at: `/actuator/prometheus`

**Custom metrics** (planned):
- `alert_rule_evaluation_time` - Alert matching performance
- `webhook_processing_time` - Webhook processing latency
- `notification_success_rate` - Notification delivery rate

### Health Checks

- `/actuator/health` - Application health
- `/actuator/info` - Application info

### Logging

Logs location: `logs/stacks-monitor.log`

**Production**: Structured JSON logging to `logs/stacks-monitor-json.log`

## Assumptions & Design Decisions

### Critical Assumptions

1. **PostgreSQL 14+**: Required for JSONB support
2. **Timestamps**: All timestamps stored in UTC using `Instant`
3. **HMAC Secret**: Shared secret for Chainhook webhook validation provided via env var
4. **JWT Key**: RS256 signing key provided via env var
5. **Max Payload Size**: Chainhook payloads assumed ≤ 5MB
6. **Soft Delete**: Blockchain reorganization handled via soft delete (deleted flag)
7. **Cache Strategy**: MVP uses ConcurrentHashMap, production upgrades to Redis (no code changes)
8. **Password Hashing**: BCrypt strength 12 (application layer)
9. **Rate Limiting**: 100 requests/minute per user (configurable)

### Performance Optimizations

1. **Alert Matching**: Cache-indexed lookup (eventType → contractId → rules) - O(1) instead of O(n)
2. **Database Indexes**: 15+ strategic indexes on high-query columns
3. **Batch Processing**: JDBC batch size 50 for bulk inserts
4. **Connection Pool**: HikariCP with 15 max connections, 5 min idle
5. **Async Notifications**: @Async processing with retry logic (3 attempts, exponential backoff)

### Partial Implementations (MVP Scope)

1. **Amount Threshold Checking**: ContractCallAlertRule - parsing function args deferred to production
2. **Data Conditions**: PrintEventAlertRule - JSONPath evaluation deferred to production
3. **Recipient Checking**: AddressActivityAlertRule - only sender checking in MVP

## Phase Roadmap

### ✅ Phase 1: Core Domain Model & Infrastructure (COMPLETE)
- Domain entities (9 entities)
- Repository interfaces
- Flyway migrations
- Configuration
- Docker setup
- Unit tests

### 🔄 Phase 2: Security Layer (Next)
- JWT authentication (RS256)
- HMAC webhook validation
- Rate limiting (Bucket4j)
- User registration/login endpoints

### Phase 3: Webhook Processing
- Chainhook payload parser
- Transaction persistence pipeline
- Batch processing
- Rollback/reorg handling

### Phase 4: Alert Engine & Notifications
- Alert CRUD endpoints
- Cache-based rule matching
- Email notification (Spring Mail)
- Webhook notification (RestTemplate)
- Retry logic + circuit breaker

### Phase 5: Query APIs & Monitoring
- Transaction query endpoints
- Notification history
- Prometheus metrics
- Grafana dashboards
- Production deployment guide

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

### Code Standards

- Follow Clean Architecture principles
- Write unit tests (target: >80% coverage)
- Use business key-based equals/hashCode for entities
- Use @Enumerated(EnumType.STRING) for all enums
- Use Instant for timestamps (UTC)
- Document assumptions with `// TODO: Assumption - ...`

## License

MIT License - see LICENSE file for details

## Support

For issues and questions:
- GitHub Issues: [Create an issue](https://github.com/yourusername/stacks-chain-monitor/issues)
- Email: support@stacks-monitor.com

## Acknowledgments

- Stacks Blockchain team for Chainhook webhooks
- Spring Boot community
- PostgreSQL team for JSONB support

---

**Built with ❤️ for the Stacks ecosystem**

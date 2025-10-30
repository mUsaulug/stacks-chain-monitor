# 🚀 Stacks Chain Monitor - Hızlı Öğrenim Rehberi

**Hedef**: Projeyi sıfırdan anlayarak kodlamak
**Strateji**: Aşağıdan yukarı (Bottom-Up), dependency sırasıyla
**Süre**: 15-20 gün

---

## 📚 7 Aşamalı Yol Haritası

### 🎯 AŞAMA 1: Value Objects (1 Gün)

**Başlangıç Noktası** - Hiçbir dependency yok!

#### Sırayla Oluştur:
```
1. TransactionType.java      → Blockchain transaction tipleri
2. EventType.java             → Event tipleri (FT, NFT, STX)
3. AlertRuleType.java         → Alert kuralı tipleri
4. AlertSeverity.java         → INFO, WARNING, CRITICAL
5. NotificationChannel.java   → EMAIL, WEBHOOK
6. NotificationStatus.java    → PENDING, SENT, FAILED
7. UserRole.java              → USER, ADMIN
```

**Konum**: `src/main/java/com/stacksmonitoring/domain/valueobject/`

**Öğrenilecekler**:
- Java Enum kullanımı
- `@Enumerated(EnumType.STRING)` annotation
- Enum'ları entity'lerde nasıl kullanırsın

**Test**:
```bash
mvn clean compile
```

---

### 🎯 AŞAMA 2: Blockchain Entities (3-4 Gün)

**Dependency Sırası**: Block → Transaction → Event'ler

#### 2.1 Core Entities (İLK ÖNCE BUNLAR)

```
1. StacksBlock.java           → Ana blok entity
   └─ Öğrenilecekler:
      - @Entity, @Table, @Index
      - OneToMany ilişki
      - Business Key Pattern (equals/hashCode)
      - Soft Delete Pattern
      - Cascade operations

2. StacksTransaction.java     → Ana transaction entity
   └─ Öğrenilecekler:
      - ManyToOne ilişki (Block'a)
      - OneToOne ilişkiler (ContractCall, ContractDeployment)
      - FetchType.LAZY
      - BigDecimal kullanımı (para için)

3. ContractCall.java          → Contract çağrısı
   └─ Öğrenilecekler:
      - OneToOne ilişki
      - JSONB kullanımı (@JdbcTypeCode)
      - Map<String, Object> field

4. ContractDeployment.java    → Contract deployment
   └─ Öğrenilecekler:
      - TEXT sütun tipi
      - Contract ABI saklama
```

#### 2.2 Event Hierarchy (POLYMORPHISM)

**Base Class** (ABSTRACT):
```
TransactionEvent.java         → Tüm event'lerin base'i
└─ Öğrenilecekler:
   - Abstract class
   - @Inheritance(strategy = JOINED)
   - @DiscriminatorColumn
   - Abstract method tanımı
```

**11 Alt Sınıf** (Hepsi aynı pattern):
```
1. FTTransferEvent.java       → Fungible token transfer
2. FTMintEvent.java           → Fungible token mint
3. FTBurnEvent.java           → Fungible token burn
4. NFTTransferEvent.java      → NFT transfer
5. NFTMintEvent.java          → NFT mint
6. NFTBurnEvent.java          → NFT burn
7. STXTransferEvent.java      → Native STX transfer
8. STXMintEvent.java          → STX mint
9. STXBurnEvent.java          → STX burn
10. STXLockEvent.java         → STX lock (staking)
11. SmartContractEvent.java   → Contract print event

Her biri:
- @DiscriminatorValue("EVENT_TYPE")
- extends TransactionEvent
- Kendi spesifik field'ları
- getEventDescription() implement eder
```

**Konum**: `src/main/java/com/stacksmonitoring/domain/model/blockchain/`

**Kritik Kavramlar**:
- **JOINED Inheritance**: Her sınıf için ayrı tablo
- **Polymorphism**: Runtime'da doğru tip seçilir
- **Cascade**: Block silinince Transaction'lar da silinir
- **Lazy Loading**: İlişkiler gerektiğinde yüklenir

---

### 🎯 AŞAMA 3: User & Monitoring Entities (2 Gün)

#### Sırayla Oluştur:

```
1. User.java                  → Kullanıcı entity
   └─ Öğrenilecekler:
      - BCrypt password hash
      - @CreatedDate, @LastModifiedDate
      - @EntityListeners(AuditingEntityListener.class)

2. MonitoredContract.java     → İzlenen contract'lar
   └─ Öğrenilecekler:
      - Composite unique constraint
      - User ile ManyToOne ilişki

3. AlertRule.java (ABSTRACT)  → Alert kuralı base class
   └─ Öğrenilecekler:
      - @Inheritance(strategy = SINGLE_TABLE)
      - @Version (optimistic locking)
      - Cooldown logic
      - Abstract matches() method

4-8. Alert Rule Alt Sınıfları:
   - ContractCallAlertRule.java      → Contract çağrı alert
   - TokenTransferAlertRule.java     → Token transfer alert
   - FailedTransactionAlertRule.java → Başarısız tx alert
   - PrintEventAlertRule.java        → Print event alert
   - AddressActivityAlertRule.java   → Adres aktivite alert

   Her biri:
   - @DiscriminatorValue("RULE_TYPE")
   - matches() method implement eder
   - getTriggerDescription() implement eder

9. AlertNotification.java     → Bildirim kaydı
   └─ Öğrenilecekler:
      - Retry logic (attemptCount)
      - shouldRetry() business logic
```

**Konum**:
- `src/main/java/com/stacksmonitoring/domain/model/user/`
- `src/main/java/com/stacksmonitoring/domain/model/monitoring/`

**Kritik Fark**:
- **TransactionEvent**: JOINED strategy (normalize)
- **AlertRule**: SINGLE_TABLE strategy (hızlı query)

**Neden farklı stratejiler?**
- Event'ler çok sayıda, JOIN maliyeti kabul edilebilir
- Alert rule'lar az, tek tablodan daha hızlı

---

### 🎯 AŞAMA 4: Repository Interfaces (1 Gün)

**Spring Data JPA** - Interface yazıyorsun, implementation otomatik!

#### Sırayla Oluştur:

```
1. StacksBlockRepository.java
   interface StacksBlockRepository extends JpaRepository<StacksBlock, Long> {
       Optional<StacksBlock> findByBlockHash(String blockHash);
       Optional<StacksBlock> findByBlockHeight(Long blockHeight);

       @Query("SELECT b FROM StacksBlock b WHERE b.deleted = false")
       List<StacksBlock> findActiveBlocks();
   }

2. StacksTransactionRepository.java
3. TransactionEventRepository.java
4. ContractCallRepository.java
5. ContractDeploymentRepository.java
6. UserRepository.java
7. MonitoredContractRepository.java
8. AlertRuleRepository.java
9. AlertNotificationRepository.java
```

**Konum**: `src/main/java/com/stacksmonitoring/domain/repository/`

**Öğrenilecekler**:
- `JpaRepository<Entity, IdType>` extend
- Method naming convention (findBy, existsBy, countBy)
- @Query ile custom JPQL
- @Param annotation
- Optional<T> dönüş tipi

**Magic Method Names**:
```java
// Spring otomatik implement eder!
Optional<User> findByEmail(String email);
List<AlertRule> findByUserIdAndIsActiveTrue(Long userId, Boolean isActive);
boolean existsByBlockHash(String blockHash);
long countBySuccess(Boolean success);
```

---

### 🎯 AŞAMA 5: Infrastructure Layer (3 Gün)

#### 5.1 Security Configuration (2 Gün)

**Sırayla Oluştur**:

```
1. JwtTokenService.java           → JWT token oluşturma/doğrulama
   └─ Öğrenilecekler:
      - JJWT library kullanımı
      - HS256 signature algorithm
      - Claims extraction
      - Token validation

2. CustomUserDetailsService.java  → User yükleme servisi
   └─ Öğrenilecekler:
      - UserDetailsService interface
      - UserDetails nesnesi dönme
      - Database'den user yükleme

3. JwtAuthenticationFilter.java   → Her request'te JWT kontrolü
   └─ Öğrenilecekler:
      - OncePerRequestFilter extend
      - Bearer token extraction
      - SecurityContextHolder kullanımı
      - Filter chain

4. ChainhookHmacFilter.java       → Webhook HMAC doğrulama
   └─ Öğrenilecekler:
      - HMAC-SHA256 signature
      - Request body reading
      - Header validation

5. RateLimitFilter.java           → Rate limiting
   └─ Öğrenilecekler:
      - Token Bucket algorithm (Bucket4j)
      - IP bazlı limiting
      - 429 Too Many Requests response

6. SecurityConfiguration.java     → Ana security config
   └─ Öğrenilecekler:
      - SecurityFilterChain bean
      - Filter sırası (önemli!)
      - Public vs authenticated endpoints
      - CSRF disable (JWT için)
      - Stateless session

7. NotificationConfig.java        → Email/webhook config
   └─ Öğrenilecekler:
      - JavaMailSender bean
      - RestTemplate bean
      - @ConfigurationProperties
```

**Filter Sırası** (ÇOK ÖNEMLİ):
```
1. RateLimitFilter           → İlk önce rate limit kontrol
2. ChainhookHmacFilter       → Webhook HMAC doğrula
3. JwtAuthenticationFilter   → JWT doğrula
4. UsernamePasswordAuthenticationFilter → Spring default
```

#### 5.2 Parser (1 Gün)

```
ChainhookPayloadParser.java      → Webhook DTO → Entity parser
└─ Öğrenilecekler:
   - 440 satır, en karmaşık sınıf!
   - DTO'dan Entity'e mapping
   - Switch expression (Java 17)
   - 11 farklı event tipi parse etme
   - Null safety
   - Error handling
```

**Konum**: `src/main/java/com/stacksmonitoring/infrastructure/`

---

### 🎯 AŞAMA 6: Application Services (4 Gün)

#### 6.1 Core Services

```
1. AuthenticationService.java            → Login/Register
   └─ Öğrenilecekler:
      - PasswordEncoder kullanımı
      - AuthenticationManager
      - JWT token generation

2. AlertMatchingService.java ⭐ EN KRİTİK
   └─ Öğrenilecekler:
      - @Cacheable (Caffeine cache)
      - O(1) alert matching
      - Cooldown kontrolü
      - Multi-type rule evaluation
      - @CacheEvict

3. AlertRuleService.java                 → Alert CRUD işlemleri
   └─ Öğrenilecekler:
      - Basic CRUD pattern
      - Cache invalidation
      - User ownership kontrolü

4. NotificationService.java (interface)  → Bildirim interface
5. EmailNotificationService.java         → Email gönderimi
   └─ Öğrenilecekler:
      - JavaMailSender kullanımı
      - MimeMessage oluşturma
      - HTML email

6. WebhookNotificationService.java       → Webhook gönderimi
   └─ Öğrenilecekler:
      - RestTemplate POST
      - JSON serialization
      - HTTP error handling

7. NotificationDispatcher.java           → Bildirim koordinatörü
   └─ Öğrenilecekler:
      - @Async kullanımı
      - CompletableFuture
      - Batch processing
      - Retry logic

8. ProcessChainhookPayloadUseCase.java ⭐ EN KRİTİK
   └─ Öğrenilecekler:
      - Apply ve Rollback handling
      - Soft delete logic
      - Batch persistence
      - Alert evaluation entegrasyonu
      - @Transactional

9. BlockQueryService.java                → Block sorguları
10. TransactionQueryService.java         → Transaction sorguları
11. MonitoringService.java               → Sistem monitoring
```

**Konum**: `src/main/java/com/stacksmonitoring/application/`

**Service Layer Pattern**:
```java
@Service
@RequiredArgsConstructor  // Lombok constructor injection
@Slf4j                    // Lombok logger
public class MyService {

    private final MyRepository repository;  // Constructor injection

    @Transactional  // Transaction yönetimi
    public void myMethod() {
        // Business logic
    }
}
```

---

### 🎯 AŞAMA 7: API Layer (3 Gün)

#### 7.1 DTO'lar (1 Gün)

**Webhook DTO'ları** (15 dosya):
```
ChainhookPayloadDto.java
BlockEventDto.java
TransactionDto.java
EventDto.java
... (11 daha)
```

**Request/Response DTO'ları**:
```
RegisterRequest.java
LoginRequest.java
AuthenticationResponse.java
CreateAlertRuleRequest.java
AlertRuleResponse.java
```

**Konum**: `src/main/java/com/stacksmonitoring/api/dto/`

#### 7.2 Controllers (2 Gün)

```
1. AuthenticationController.java         → POST /api/v1/auth/register, /login

2. WebhookController.java ⭐ KRİTİK
   └─ POST /api/v1/webhook/chainhook
   └─ @Async processing
   └─ Immediate 200 OK response

3. AlertRuleController.java              → Alert CRUD endpoints
   └─ GET, POST, PUT, DELETE /api/v1/alerts/rules

4. AlertNotificationController.java      → Notification endpoints
   └─ GET /api/v1/alerts/notifications

5. BlockQueryController.java             → Block query endpoints
   └─ GET /api/v1/blocks
   └─ GET /api/v1/blocks/hash/{hash}
   └─ GET /api/v1/blocks/height/{height}

6. TransactionQueryController.java       → Transaction endpoints
7. MonitoringController.java             → Monitoring endpoints

8. GlobalExceptionHandler.java           → Exception handling
   └─ @ControllerAdvice
   └─ @ExceptionHandler
```

**Controller Pattern**:
```java
@RestController
@RequestMapping("/api/v1/resource")
@RequiredArgsConstructor
@Validated
public class MyController {

    private final MyService service;

    @GetMapping("/{id}")
    public ResponseEntity<MyDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<MyDto> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }
}
```

**Konum**: `src/main/java/com/stacksmonitoring/api/`

---

## 🔗 Dependency İlişkileri Haritası

```
Value Objects (Enum'lar)
    ↓ (hiçbir dependency yok)
Domain Entities
    ↓ (entity'lere bağlı)
Repository Interfaces
    ↓ (repository'lere bağlı)
Infrastructure (Security, Parser)
    ↓ (infrastructure'a bağlı)
Application Services
    ↓ (service'lere bağlı)
API Controllers & DTOs
```

**Kritik Kural**: Yukarıdan aşağıya dependency olabilir, tersi ASLA!

---

## 📝 Her Aşama Sonunda Yapılacaklar

### ✅ Checklist

1. **Kod Yaz**
   ```bash
   # Dosyayı oluştur
   # Kodu yaz
   # Annotation'ları ekle
   ```

2. **Compile Et**
   ```bash
   mvn clean compile
   ```

3. **Anladığını Doğrula**
   - Bu sınıf ne iş yapıyor?
   - Hangi sınıflara bağımlı?
   - Hangi sınıflar buna bağımlı olacak?
   - Veritabanında nasıl saklanacak?

4. **Not Al**
   ```markdown
   # Gün X - [Aşama Adı]

   ## Oluşturduklarım:
   - Dosya1.java
   - Dosya2.java

   ## Öğrendiklerim:
   - Kavram 1
   - Kavram 2

   ## Sorularım:
   - ?
   ```

5. **Commit Et**
   ```bash
   git add .
   git commit -m "feat: Aşama X tamamlandı"
   ```

---

## 🎯 Öğrenim Teknikleri

### 1. Active Learning

Her sınıfı yazarken:
```
❓ Bu ne?          → Sınıfın amacı
❓ Neden var?      → Problem çözümü
❓ Nasıl çalışır?  → İç mekanik
❓ Ne zaman kullanılır? → Use case
❓ Alternatifleri? → Farklı yaklaşımlar
```

### 2. Debugging İle Öğren

```java
// Test yaz ve debug mode'da çalıştır
@Test
void testStacksBlockCreation() {
    StacksBlock block = new StacksBlock();
    block.setBlockHash("0xabc");  // <- Breakpoint koy
    block.setBlockHeight(100L);

    // equals() metoduna gir, nasıl çalıştığını gör
    StacksBlock block2 = new StacksBlock();
    block2.setBlockHash("0xabc");

    assertEquals(block, block2);  // <- Neden eşit?
}
```

### 3. Documentation First

Her sınıf için:
```markdown
# StacksBlock.java

## Ne yapar?
Blockchain'deki bir bloğu temsil eder.

## Neden önemli?
Blockchain'in temel yapı taşı. Transaction'lar block içinde.

## İlişkileri:
- Block → Transaction (OneToMany)
- Block → Block (parentBlockHash)

## Kritik noktalar:
- Business key: blockHash
- Soft delete için deleted field
- Cascade ile transaction'lar silinir
```

---

## 🧪 Test Stratejisi

### Unit Test Öncelikleri

**1. En Kritik: Business Logic**
```java
// AlertMatchingService
@Test
void shouldMatchContractCallRule() { ... }

@Test
void shouldRespectCooldownPeriod() { ... }
```

**2. Orta: Service Layer**
```java
// AlertRuleService
@Test
void shouldCreateAlertRule() { ... }
```

**3. Integration: Controller**
```java
@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerIntegrationTest { ... }
```

---

## 🚨 Sık Yapılan Hatalar

### 1. equals/hashCode Hatası
```java
// ❌ YANLIŞ
@Override
public boolean equals(Object o) {
    StacksBlock that = (StacksBlock) o;
    return id.equals(that.id);  // ID null olabilir!
}

// ✅ DOĞRU
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StacksBlock)) return false;
    StacksBlock that = (StacksBlock) o;
    return blockHash != null && blockHash.equals(that.blockHash);
}
```

### 2. Bidirectional Relationship Hatası
```java
// ❌ YANLIŞ
block.getTransactions().add(tx);  // Sadece bir taraf set edildi!

// ✅ DOĞRU
block.addTransaction(tx);  // İki taraf da sync edilir
```

### 3. N+1 Query Problem
```java
// ❌ YANLIŞ
List<StacksBlock> blocks = blockRepository.findAll();
for (StacksBlock block : blocks) {
    block.getTransactions().size();  // Her block için ayrı query!
}

// ✅ DOĞRU
@Query("SELECT b FROM StacksBlock b LEFT JOIN FETCH b.transactions")
List<StacksBlock> findAllWithTransactions();
```

### 4. Lazy Loading Exception
```java
// ❌ YANLIŞ
@Transactional
public StacksBlock getBlock(Long id) {
    return blockRepository.findById(id).get();
}
// Transaction dışında transactions.get(0) → LazyInitializationException!

// ✅ DOĞRU
@Transactional
public StacksBlock getBlockWithTransactions(Long id) {
    StacksBlock block = blockRepository.findById(id).get();
    block.getTransactions().size();  // Force load
    return block;
}
```

---

## 📚 Öğrenim Kaynakları

### JPA & Hibernate
- Hibernate Official Docs
- Baeldung JPA Tutorials

### Spring Security
- Spring Security Reference
- JWT.io

### Spring Boot
- Spring Boot Reference Documentation
- Spring Data JPA Documentation

### Design Patterns
- Clean Architecture (Robert C. Martin)
- Domain-Driven Design (Eric Evans)

---

## ⏱️ Günlük İlerleme Planı

### Hafta 1: Domain Layer
- **Gün 1**: Value Objects (7 enum)
- **Gün 2-3**: StacksBlock, StacksTransaction
- **Gün 4-5**: TransactionEvent hierarchy (11 sınıf)
- **Gün 6**: ContractCall, ContractDeployment
- **Gün 7**: Dinlenme + revision

### Hafta 2: Domain + Infrastructure
- **Gün 8-9**: User, Monitoring entities
- **Gün 10**: Repository interfaces
- **Gün 11-12**: Security configuration
- **Gün 13**: Parser
- **Gün 14**: Dinlenme + revision

### Hafta 3: Application + API
- **Gün 15-16**: Application services
- **Gün 17**: DTO'lar
- **Gün 18-19**: Controllers
- **Gün 20**: Testing + final review

---

## 🎓 Son Kontrol Listesi

Proje bittiğinde şunları bileceksin:

### JPA/Hibernate
- ✅ Entity, Table, Column annotations
- ✅ Relationship mapping (OneToOne, OneToMany, ManyToOne)
- ✅ Inheritance strategies (JOINED, SINGLE_TABLE)
- ✅ Cascade operations
- ✅ Lazy/Eager loading
- ✅ Business key pattern
- ✅ Soft delete pattern

### Spring Framework
- ✅ Dependency Injection
- ✅ @Service, @Repository, @Controller
- ✅ @Transactional
- ✅ @Async
- ✅ @Cacheable

### Spring Security
- ✅ JWT authentication
- ✅ Security filters
- ✅ HMAC signature validation
- ✅ Rate limiting
- ✅ CORS, CSRF

### Design Patterns
- ✅ Clean Architecture
- ✅ Repository Pattern
- ✅ Strategy Pattern (NotificationService)
- ✅ Factory Pattern (AlertRule polymorphism)

### Best Practices
- ✅ RESTful API design
- ✅ Error handling
- ✅ Logging
- ✅ Testing strategies

---

## 🎯 Sonraki Adımlar (Post-MVP)

1. **Performance Optimization**
   - Database indexing
   - Query optimization
   - Caching strategy

2. **Monitoring & Observability**
   - Prometheus metrics
   - Grafana dashboards
   - Distributed tracing

3. **Deployment**
   - Docker containerization
   - Kubernetes deployment
   - CI/CD pipeline

4. **Advanced Features**
   - WebSocket notifications
   - GraphQL API
   - Multi-tenancy

---

## 💡 Motivasyon

> "Bu proje sadece kod yazmak değil, **software engineering** öğrenmek!"

Her satır kod:
- Bir problem çözüyor
- Bir pattern uyguluyor
- Bir best practice gösteriyor

Sabırlı ol, her gün biraz ilerlesen bile **15 günde masterpiece**'ini tamamlayacaksın!

**Başarılar! 🚀**

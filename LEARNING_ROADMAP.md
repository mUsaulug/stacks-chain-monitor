# 🎓 Stacks Chain Monitor - Tam Öğrenim Rehberi

**Hedef**: Bu projeyi sıfırdan, her satır kodu anlayarak, kendi lokalinizde inşa etmek.

**Strateji**: En temel yapılardan başlayıp, dependency'leri takip ederek yukarı doğru çıkmak.

**Toplam Süre Tahmini**: 15-20 gün (günde 4-6 saat çalışarak)

---

## 📋 İçindekiler

1. [Önkoşullar ve Ortam Kurulumu](#1-önkoşullar-ve-ortam-kurulumu)
2. [Proje Yapısına Genel Bakış](#2-proje-yapısına-genel-bakış)
3. [7 Aşamalı Öğrenim Yol Haritası](#3-7-aşamalı-öğrenim-yol-haritası)
4. [Her Sınıfın Detaylı Açıklaması](#4-her-sınıfın-detaylı-açıklaması)
5. [Dependency İlişkileri](#5-dependency-ilişkileri)
6. [Test Stratejisi](#6-test-stratejisi)
7. [Sık Sorulan Sorular](#7-sık-sorulan-sorular)

---

## 1. Önkoşullar ve Ortam Kurulumu

### 1.1 Gerekli Teknolojiler

```bash
# Java 17 kurulu olmalı
java -version  # Çıktı: java 17.x.x

# Maven kurulu olmalı
mvn -version   # Çıktı: Apache Maven 3.8+

# PostgreSQL 14+ kurulu olmalı
psql --version # Çıktı: psql 14.x
```

### 1.2 Veritabanı Kurulumu

```sql
-- PostgreSQL'e bağlan
psql -U postgres

-- Veritabanı oluştur
CREATE DATABASE stacks_monitoring;

-- Kullanıcı oluştur
CREATE USER stacks_user WITH PASSWORD 'stacks_password';

-- Yetki ver
GRANT ALL PRIVILEGES ON DATABASE stacks_monitoring TO stacks_user;
```

### 1.3 Proje Dizini Oluştur

```bash
# Proje dizini
mkdir -p ~/stacks-chain-monitor-learning

# Standart Maven yapısı
mkdir -p src/main/java/com/stacksmonitoring
mkdir -p src/main/resources
mkdir -p src/test/java/com/stacksmonitoring
```

### 1.4 pom.xml Hazırla

İlk ihtiyacınız olan dosya `pom.xml`. Bu dosyayı GitHub'dan alın veya aşağıdaki minimal versiyonu kullanın:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
    </parent>

    <groupId>com.stacksmonitoring</groupId>
    <artifactId>stacks-chain-monitor</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Core Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- JPA & PostgreSQL -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>

        <!-- Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.3</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.3</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.3</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 2. Proje Yapısına Genel Bakış

### 2.1 Clean Architecture Katmanları

Bu proje **Clean Architecture** prensibine göre tasarlanmış:

```
┌─────────────────────────────────────────────────────┐
│              API Layer (Presentation)               │
│         Controllers, DTOs, Exception Handlers       │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│           Application Layer (Use Cases)             │
│        Services, Use Cases, Business Logic          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              Domain Layer (Entities)                │
│    Entities, Value Objects, Repository Interfaces   │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│         Infrastructure Layer (Technical)            │
│   JPA Implementations, Security, External Services  │
└─────────────────────────────────────────────────────┘
```

**Dependency Direction**: API → Application → Domain ← Infrastructure

**Önemli**: Domain layer hiçbir şeye bağımlı değildir! En bağımsız katman.

### 2.2 Dizin Yapısı

```
src/main/java/com/stacksmonitoring/
│
├── domain/                           # 🎯 EN TEMEL KATMAN (BURADAN BAŞLA)
│   ├── valueobject/                  # İlk yapacağın: Enum'lar (7 dosya)
│   │   ├── TransactionType.java      # Blockchain transaction tipleri
│   │   ├── EventType.java            # Event tipleri (FT, NFT, STX)
│   │   ├── AlertRuleType.java        # Alert kuralı tipleri
│   │   ├── AlertSeverity.java        # Alert şiddet seviyeleri
│   │   ├── NotificationChannel.java  # Bildirim kanalları
│   │   ├── NotificationStatus.java   # Bildirim durumu
│   │   └── UserRole.java             # Kullanıcı rolleri
│   │
│   ├── model/                        # İkinci yapacağın: Entity'ler
│   │   ├── blockchain/               # Blockchain varlıkları (14 dosya)
│   │   │   ├── StacksBlock.java      # Ana blok entity'si
│   │   │   ├── StacksTransaction.java # Ana transaction entity'si
│   │   │   ├── TransactionEvent.java # Event base class (ABSTRACT)
│   │   │   ├── FTTransferEvent.java  # Fungible token transfer
│   │   │   ├── FTMintEvent.java      # Fungible token mint
│   │   │   ├── FTBurnEvent.java      # Fungible token burn
│   │   │   ├── NFTTransferEvent.java # NFT transfer
│   │   │   ├── NFTMintEvent.java     # NFT mint
│   │   │   ├── NFTBurnEvent.java     # NFT burn
│   │   │   ├── STXTransferEvent.java # STX transfer
│   │   │   ├── STXMintEvent.java     # STX mint
│   │   │   ├── STXBurnEvent.java     # STX burn
│   │   │   ├── STXLockEvent.java     # STX lock
│   │   │   ├── SmartContractEvent.java # Contract event
│   │   │   ├── ContractCall.java     # Contract çağrısı
│   │   │   └── ContractDeployment.java # Contract deploy
│   │   │
│   │   ├── user/                     # Kullanıcı varlıkları (1 dosya)
│   │   │   └── User.java             # Kullanıcı entity'si
│   │   │
│   │   └── monitoring/               # İzleme varlıkları (7 dosya)
│   │       ├── MonitoredContract.java      # İzlenen contract
│   │       ├── AlertRule.java              # Alert base class (ABSTRACT)
│   │       ├── ContractCallAlertRule.java  # Contract çağrı alert'i
│   │       ├── TokenTransferAlertRule.java # Token transfer alert'i
│   │       ├── FailedTransactionAlertRule.java # Fail alert'i
│   │       ├── PrintEventAlertRule.java    # Print event alert'i
│   │       ├── AddressActivityAlertRule.java # Address alert'i
│   │       └── AlertNotification.java      # Bildirim kaydı
│   │
│   └── repository/                   # Üçüncü yapacağın: Repository interface'leri (9 dosya)
│       ├── StacksBlockRepository.java
│       ├── StacksTransactionRepository.java
│       ├── TransactionEventRepository.java
│       ├── ContractCallRepository.java
│       ├── ContractDeploymentRepository.java
│       ├── UserRepository.java
│       ├── MonitoredContractRepository.java
│       ├── AlertRuleRepository.java
│       └── AlertNotificationRepository.java
│
├── infrastructure/                   # 🔧 TEKNİK KATMAN
│   ├── config/                       # Dördüncü yapacağın: Konfigürasyon (7 dosya)
│   │   ├── SecurityConfiguration.java      # Spring Security config
│   │   ├── JwtTokenService.java            # JWT token işlemleri
│   │   ├── CustomUserDetailsService.java   # User yükleme servisi
│   │   ├── JwtAuthenticationFilter.java    # JWT filtresi
│   │   ├── ChainhookHmacFilter.java        # HMAC doğrulama
│   │   ├── RateLimitFilter.java            # Rate limiting
│   │   └── NotificationConfig.java         # Email/webhook config
│   │
│   └── parser/                       # Beşinci yapacağın: Parser (1 dosya)
│       └── ChainhookPayloadParser.java     # Webhook DTO → Entity parser
│
├── application/                      # 💼 İŞ MANTIĞI KATMANI
│   ├── service/                      # Altıncı yapacağın: Servisler (10 dosya)
│   │   ├── AuthenticationService.java        # Login/Register
│   │   ├── AlertMatchingService.java         # Alert eşleştirme (CORE)
│   │   ├── AlertRuleService.java             # Alert CRUD
│   │   ├── NotificationService.java          # Bildirim interface
│   │   ├── EmailNotificationService.java     # Email gönderimi
│   │   ├── WebhookNotificationService.java   # Webhook gönderimi
│   │   ├── NotificationDispatcher.java       # Bildirim koordinatörü
│   │   ├── BlockQueryService.java            # Block sorguları
│   │   ├── TransactionQueryService.java      # Transaction sorguları
│   │   └── MonitoringService.java            # Sistem monitoring
│   │
│   └── usecase/                      # Use Case'ler (1 dosya)
│       └── ProcessChainhookPayloadUseCase.java # Webhook işleme (CORE)
│
└── api/                              # 🌐 API KATMANI
    ├── dto/                          # Yedinci yapacağın: DTO'lar (28 dosya)
    │   ├── request/                  # Request DTO'ları
    │   ├── response/                 # Response DTO'ları
    │   ├── webhook/                  # Chainhook DTO'ları (15 dosya)
    │   └── alert/                    # Alert DTO'ları
    │
    ├── controller/                   # Sekizinci yapacağın: Controller'lar (8 dosya)
    │   ├── AuthenticationController.java
    │   ├── WebhookController.java
    │   ├── AlertRuleController.java
    │   ├── AlertNotificationController.java
    │   ├── BlockQueryController.java
    │   ├── TransactionQueryController.java
    │   └── MonitoringController.java
    │
    └── exception/                    # Exception handling (2 dosya)
        ├── GlobalExceptionHandler.java
        └── ErrorResponse.java
```

---

## 3. 7 Aşamalı Öğrenim Yol Haritası

### 🎯 AŞAMA 1: Value Objects (Enum'lar) - 1 Gün

**Hedef**: En basit yapılardan başla, hiçbir dependency yok.

**Neden bu sıra**: Enum'lar hiçbir şeye bağlı değil, diğer sınıflar bunları kullanır.

#### Yapılacaklar:

**1.1 TransactionType.java** ⭐ İLK DOSYAN
```java
// Konum: src/main/java/com/stacksmonitoring/domain/valueobject/TransactionType.java

package com.stacksmonitoring.domain.valueobject;

/**
 * Stacks blockchain'de olabilecek transaction tipleri.
 *
 * NE ZAMAN KULLANILIR?
 * - Bir transaction kaydederken onun tipini belirtmek için
 * - Transaction sorgularken filtrelemek için
 *
 * NEREDEN GELİR?
 * - Chainhook webhook'undan gelen transaction verisi içinde "txType" field'ı
 *
 * NEREYE GİDER?
 * - StacksTransaction entity'sinin txType field'ında saklanır
 */
public enum TransactionType {
    TOKEN_TRANSFER,      // Token transferi (STX, FT, NFT)
    SMART_CONTRACT,      // Smart contract deploy
    CONTRACT_CALL,       // Contract fonksiyon çağrısı
    POISON_MICROBLOCK,   // Blockchain teknik detayı
    COINBASE,            // Miner ödülü transaction'ı
    TENURE_CHANGE        // Blockchain epoch değişimi
}
```

**❓ Kendin Sor**:
- Bu enum'u nerede kullanacağım? → StacksTransaction entity'sinde
- Başka bir sınıfa bağımlı mı? → Hayır, tamamen bağımsız
- Veritabanında nasıl saklanır? → @Enumerated(EnumType.STRING) ile varchar olarak

**✅ Doğrulama**:
```bash
# Compile et
mvn clean compile

# Hata yoksa başarılı!
```

---

**1.2 EventType.java**
```java
// Konum: src/main/java/com/stacksmonitoring/domain/valueobject/EventType.java

package com.stacksmonitoring.domain.valueobject;

/**
 * Transaction içinde olabilecek event tipleri.
 *
 * ÖNEMLİ: Bir transaction içinde BIRDEN FAZLA event olabilir!
 * Örnek: Bir DEX swap transaction'ı:
 *   - 1x FT_TRANSFER (Token A gönderildi)
 *   - 1x FT_TRANSFER (Token B alındı)
 *   - 1x SMART_CONTRACT_EVENT (Swap olayı loglandı)
 *
 * NE ZAMAN KULLANILIR?
 * - TransactionEvent entity'lerinin tipini belirtmek için
 * - Alert kurallarında hangi event tipini izleyeceğini belirtmek için
 */
public enum EventType {
    // Fungible Token (eşlenebilir token) işlemleri
    FT_MINT,             // Yeni FT üretildi (mint)
    FT_BURN,             // FT yakıldı (burn)
    FT_TRANSFER,         // FT transfer edildi

    // Non-Fungible Token (NFT) işlemleri
    NFT_MINT,            // Yeni NFT üretildi
    NFT_BURN,            // NFT yakıldı
    NFT_TRANSFER,        // NFT transfer edildi

    // STX (native token) işlemleri
    STX_TRANSFER,        // STX transfer edildi
    STX_MINT,            // Yeni STX üretildi (sadece genesis)
    STX_BURN,            // STX yakıldı
    STX_LOCK,            // STX kilitlendi (staking için)

    // Contract event'leri
    SMART_CONTRACT_EVENT // Contract'tan custom event (print)
}
```

**❓ Kendin Sor**:
- Bir transaction'da kaç event olabilir? → 0'dan fazla, sınır yok
- FT_TRANSFER ile STX_TRANSFER farkı ne? → FT custom token, STX native token
- SMART_CONTRACT_EVENT nedir? → Contract'ın (print) fonksiyonu ile logladığı custom event

---

**1.3 AlertRuleType.java**
```java
// Konum: src/main/java/com/stacksmonitoring/domain/valueobject/AlertRuleType.java

package com.stacksmonitoring.domain.valueobject;

/**
 * Sistemde tanımlanabilecek alert kuralı tipleri.
 *
 * HER TİP İÇİN AYRI BİR SINIF VAR!
 * - CONTRACT_CALL → ContractCallAlertRule.java
 * - TOKEN_TRANSFER → TokenTransferAlertRule.java
 * - FAILED_TRANSACTION → FailedTransactionAlertRule.java
 * - PRINT_EVENT → PrintEventAlertRule.java
 * - ADDRESS_ACTIVITY → AddressActivityAlertRule.java
 *
 * Bu polymorphism (çok biçimlilik) örneği!
 */
public enum AlertRuleType {
    CONTRACT_CALL,        // Belirli bir contract fonksiyonu çağrıldığında
    TOKEN_TRANSFER,       // Token transfer olduğunda (büyük miktarlar için)
    FAILED_TRANSACTION,   // Transaction başarısız olduğunda
    PRINT_EVENT,          // Contract'tan print event geldiğinde
    ADDRESS_ACTIVITY      // Belirli bir adres activity gösterdiğinde
}
```

**💡 Öğrenme Notu**:
Bu enum'un her değeri için ayrı bir Java class var. Bu pattern'e **Table-Per-Hierarchy (TPH)** inheritance denir.

---

**1.4-1.7 Diğer Enum'lar**

Aynı mantıkla şunları da oluştur:
- `AlertSeverity.java` (INFO, WARNING, CRITICAL)
- `NotificationChannel.java` (EMAIL, WEBHOOK)
- `NotificationStatus.java` (PENDING, SENT, FAILED)
- `UserRole.java` (USER, ADMIN)

**📝 Günlük Özet Yaz**:
```
# Gün 1 - Value Objects
✅ 7 enum oluşturdum
✅ Her birinin ne işe yaradığını anladım
✅ Dependency'leri olmadığını gördüm
✅ mvn compile başarılı

Öğrendiklerim:
- Enum'lar en temel yapı taşları
- Hiçbir bağımlılıkları yok
- Database'de STRING olarak saklanırlar (@Enumerated)
```

---

### 🎯 AŞAMA 2: Domain Entities - Blockchain (3-4 Gün)

**Hedef**: Blockchain'i temsil eden entity'leri oluştur.

**Dependency Sırası**: StacksBlock → StacksTransaction → TransactionEvent alt sınıfları

#### 2.1 StacksBlock.java ⭐ İLK ENTITY

**Önce Anla**:
```
Block nedir?
- Blockchain'in temel birimi
- İçinde transaction'lar var
- Her block bir önceki block'a bağlı (parentBlockHash)
- Benzersiz tanımlayıcısı: blockHash

Block → [Tx1, Tx2, Tx3, ...] → Her Tx içinde [Event1, Event2, ...]
```

**Kod**:
```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/blockchain/StacksBlock.java

package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stacks blockchain'de bir block.
 *
 * ÖNEMLİ KAVRAMLAR:
 *
 * 1. Business Key Pattern:
 *    - Database ID (Long id) → Teknik, JPA için
 *    - Business Key (String blockHash) → Gerçek dünyada benzersiz
 *    - equals() ve hashCode() business key ile yapılır!
 *
 * 2. Soft Delete Pattern:
 *    - deleted = true yaparak "silinmiş" işaretleriz
 *    - Gerçekten veritabanından silmeyiz
 *    - Neden? Blockchain reorganization olabilir!
 *
 * 3. Bidirectional Relationship:
 *    - Block → Transaction (OneToMany)
 *    - Transaction → Block (ManyToOne)
 *    - "mappedBy" kullanan taraf owner değildir
 *
 * 4. Cascade Operations:
 *    - Block silinince transaction'lar da silinir
 *    - cascade = CascadeType.ALL
 *    - orphanRemoval = true
 */
@Entity
@Table(name = "stacks_block", indexes = {
    @Index(name = "idx_block_height", columnList = "block_height", unique = true),
    @Index(name = "idx_block_hash", columnList = "block_hash", unique = true),
    @Index(name = "idx_block_timestamp", columnList = "timestamp")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class StacksBlock {

    // ============= PRIMARY KEY =============
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============= BUSINESS KEYS =============
    /**
     * Block yüksekliği - blockchain'deki sıra numarası.
     * Genesis block: 0
     * Her yeni block: önceki + 1
     */
    @Column(name = "block_height", nullable = false, unique = true)
    private Long blockHeight;

    /**
     * Block'un benzersiz hash'i (SHA256).
     * Format: 0x ile başlayan 66 karakterlik hex string
     * Örnek: 0x1234567890abcdef...
     */
    @Column(name = "block_hash", nullable = false, unique = true, length = 66)
    private String blockHash;

    /**
     * Index block hash - Stacks'e özel teknik detay.
     */
    @Column(name = "index_block_hash", nullable = false, length = 66)
    private String indexBlockHash;

    /**
     * Önceki block'un hash'i - blockchain zinciri.
     */
    @Column(name = "parent_block_hash", length = 66)
    private String parentBlockHash;

    // ============= BLOCK METADATA =============
    /**
     * Block'un oluşturulma zamanı (Unix timestamp).
     */
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * Bu block içindeki transaction sayısı.
     * transactions.size() ile otomatik güncellenir.
     */
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount = 0;

    /**
     * Bitcoin block bilgileri (Stacks, Bitcoin'e bağlı çalışır).
     */
    @Column(name = "burn_block_height")
    private Long burnBlockHeight;

    @Column(name = "burn_block_hash", length = 66)
    private String burnBlockHash;

    @Column(name = "burn_block_timestamp")
    private Instant burnBlockTimestamp;

    /**
     * Bu block'u üreten miner'ın adresi.
     */
    @Column(name = "miner_address", length = 50)
    private String minerAddress;

    // ============= RELATIONSHIPS =============
    /**
     * Bu block içindeki tüm transaction'lar.
     *
     * ÖNEMLİ:
     * - mappedBy = "block" → StacksTransaction.block field'ına bakıyor
     * - cascade = ALL → Block silinince transaction'lar da silinir
     * - orphanRemoval = true → Transaction block'tan çıkarılınca silinir
     */
    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StacksTransaction> transactions = new ArrayList<>();

    // ============= AUDIT FIELDS =============
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ============= SOFT DELETE =============
    /**
     * Blockchain reorganization için soft delete.
     *
     * Senaryo:
     * 1. Block 100 geldi, kaydettik
     * 2. Network fork oldu
     * 3. Block 100 geçersiz oldu (rollback)
     * 4. deleted = true yapıyoruz
     * 5. Yeni Block 100 geldi, kaydediyoruz
     */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ============= BUSINESS KEY PATTERN =============
    /**
     * ÇOOK ÖNEMLİ!
     *
     * JPA entity'lerde equals/hashCode kullanırken:
     * ❌ YANLIŞ: Database ID kullanmak
     * ✅ DOĞRU: Business key kullanmak (blockHash)
     *
     * Neden?
     * - Yeni entity'lerde id = null olur
     * - Set, Map gibi collection'lar bozulur
     * - Lazy loading sorunları olur
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StacksBlock)) return false;
        StacksBlock that = (StacksBlock) o;
        // blockHash null değilse ve eşitse, bu aynı block
        return blockHash != null && blockHash.equals(that.blockHash);
    }

    /**
     * hashCode için class hash kullanıyoruz.
     *
     * Neden blockHash.hashCode() kullanmıyoruz?
     * - blockHash başta null olabilir
     * - hashCode hiç değişmemeli (immutable)
     * - class hash her zaman aynı
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // ============= CONVENIENCE METHODS =============
    /**
     * Block'a transaction ekle.
     *
     * İki yönlü ilişkiyi düzgün kuruyor:
     * 1. transactions listesine ekle
     * 2. transaction.setBlock(this) yap
     * 3. transactionCount güncelle
     */
    public void addTransaction(StacksTransaction transaction) {
        transactions.add(transaction);
        transaction.setBlock(this);  // Bidirectional sync
        this.transactionCount = transactions.size();
    }

    /**
     * Block'u soft delete yap.
     * Blockchain reorganization durumunda kullanılır.
     */
    public void markAsDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
```

**🎓 ÖĞRENİLECEKLER**:

1. **JPA Annotations**:
   - `@Entity` → Bu bir veritabanı tablosu
   - `@Table` → Tablo adı ve index'ler
   - `@Id` → Primary key
   - `@GeneratedValue` → Otomatik artan ID
   - `@Column` → Sütun özellikleri

2. **Lombok**:
   - `@Getter @Setter` → Getter/setter metotları otomatik oluştur
   - `@NoArgsConstructor` → Parametresiz constructor
   - `@AllArgsConstructor` → Tüm parametreli constructor

3. **Business Key Pattern**:
   - ID kullanma, business key kullan (blockHash)
   - equals() ve hashCode() bu şekilde yap

4. **Bidirectional Relationship**:
   - Block → Transactions (OneToMany)
   - Transaction → Block (ManyToOne)
   - mappedBy ile owner tarafı belirt

5. **Soft Delete Pattern**:
   - deleted boolean field
   - Gerçekten silme, işaretle

**❓ Kendin Sor**:
1. blockHeight ve blockHash farkı ne?
   - Height: Sıra numarası (sayı)
   - Hash: Benzersiz imza (string)

2. Neden orphanRemoval = true?
   - Transaction block'tan çıkarılınca veritabanından da silinsin

3. equals() içinde neden blockHash != null kontrolü var?
   - Yeni entity'de blockHash henüz set edilmemiş olabilir

4. transactionCount neden var, transactions.size() yetmez mi?
   - Database'de query yaparken daha hızlı
   - Lazy loading sorunlarını önler

**✅ Test Et**:
```java
// Test: StacksBlockTest.java
@Test
void testBusinessKeyEquals() {
    StacksBlock block1 = new StacksBlock();
    block1.setBlockHash("0xabc123");

    StacksBlock block2 = new StacksBlock();
    block2.setBlockHash("0xabc123");

    // Farklı ID'ler olsa bile, blockHash aynı ise eşitler
    assertEquals(block1, block2);
}
```

---

#### 2.2 StacksTransaction.java ⭐ İKİNCİ ENTITY

**Dependency**: StacksBlock'a bağlı (ManyToOne ilişki)

```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/blockchain/StacksTransaction.java

package com.stacksmonitoring.domain.model.blockchain;

import com.stacksmonitoring.domain.valueobject.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stacks blockchain'de bir transaction.
 *
 * İLİŞKİLER:
 * 1. Block → Transaction (ManyToOne) - Her transaction bir block'a ait
 * 2. Transaction → ContractCall (OneToOne) - İsteğe bağlı
 * 3. Transaction → ContractDeployment (OneToOne) - İsteğe bağlı
 * 4. Transaction → Events (OneToMany) - 0 veya daha fazla event
 *
 * TİP AYRIM:
 * - txType = CONTRACT_CALL → contractCall field dolu
 * - txType = SMART_CONTRACT → contractDeployment field dolu
 * - txType = TOKEN_TRANSFER → events field dolu
 */
@Entity
@Table(name = "stacks_transaction", indexes = {
    @Index(name = "idx_tx_id", columnList = "tx_id", unique = true),
    @Index(name = "idx_tx_sender", columnList = "sender"),
    @Index(name = "idx_tx_block", columnList = "block_id"),
    @Index(name = "idx_tx_success", columnList = "success"),
    @Index(name = "idx_tx_type", columnList = "tx_type")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class StacksTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============= BUSINESS KEY =============
    /**
     * Transaction ID (hash) - benzersiz tanımlayıcı.
     * Format: 0x ile başlayan 66 karakterlik hex
     */
    @Column(name = "tx_id", nullable = false, unique = true, length = 66)
    private String txId;

    // ============= BLOCK İLİŞKİSİ =============
    /**
     * Bu transaction hangi block içinde?
     *
     * ÖNEMLİ:
     * - FetchType.LAZY → İhtiyaç olana kadar yükleme
     * - @JoinColumn → Foreign key sütunu
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    private StacksBlock block;

    // ============= TRANSACTION BİLGİLERİ =============
    /**
     * Transaction'ı başlatan adres.
     */
    @Column(nullable = false, length = 50)
    private String sender;

    /**
     * Sponsor address - fee ödeyen (opsiyonel).
     *
     * Stacks'te "sponsored transaction" özelliği var:
     * - Alice işlem yapmak istiyor ama fee parası yok
     * - Bob "ben fee'yi ödeyeyim" diyor (sponsor)
     */
    @Column(name = "sponsor_address", length = 50)
    private String sponsorAddress;

    /**
     * Transaction tipi.
     * ValueObject enum kullanıyoruz!
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false)
    private TransactionType txType;

    /**
     * Transaction başarılı mı?
     *
     * ÖNEMLİ: Blockchain'e yazıldı = success true DEMEK DEĞİL!
     * - Başarısız transaction'lar da blockchain'e yazılır
     * - Fee ödenir ama işlem başarısız olur
     * - Bu field contract execution sonucunu gösterir
     */
    @Column(nullable = false)
    private Boolean success;

    /**
     * Block içindeki sıra numarası.
     */
    @Column(name = "tx_index", nullable = false)
    private Integer txIndex;

    /**
     * Nonce - sender'ın kaçıncı transaction'ı.
     * Replay attack önlemek için.
     */
    @Column(nullable = false)
    private Long nonce;

    /**
     * Fee (transaction ücreti) microSTX cinsinden.
     * 1 STX = 1,000,000 microSTX
     */
    @Column(name = "fee_rate", precision = 30)
    private BigDecimal feeRate;

    // ============= EXECUTION COST =============
    /**
     * Contract execution maliyeti.
     * Gas benzeri bir kavram.
     */
    @Column(name = "execution_cost_read_count")
    private Long executionCostReadCount;

    @Column(name = "execution_cost_read_length")
    private Long executionCostReadLength;

    @Column(name = "execution_cost_runtime")
    private Long executionCostRuntime;

    @Column(name = "execution_cost_write_count")
    private Long executionCostWriteCount;

    @Column(name = "execution_cost_write_length")
    private Long executionCostWriteLength;

    // ============= RAW DATA =============
    /**
     * Contract execution sonucu (Clarity değeri).
     */
    @Column(name = "raw_result", columnDefinition = "TEXT")
    private String rawResult;

    /**
     * Raw transaction hex verisi.
     */
    @Column(name = "raw_tx", columnDefinition = "TEXT")
    private String rawTx;

    // ============= OPTIONAL RELATIONSHIPS =============
    /**
     * CONTRACT_CALL tipinde ise dolu.
     *
     * OneToOne ilişki:
     * - Her transaction max 1 contract call
     * - Her contract call 1 transaction'a ait
     */
    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private ContractCall contractCall;

    /**
     * SMART_CONTRACT tipinde ise dolu.
     */
    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private ContractDeployment contractDeployment;

    // ============= EVENTS =============
    /**
     * Transaction içindeki event'ler.
     *
     * Bir transaction 0 veya daha fazla event içerebilir.
     * Örnek DEX swap:
     * - FT_TRANSFER: TokenA gönderildi
     * - FT_TRANSFER: TokenB alındı
     * - SMART_CONTRACT_EVENT: Swap event'i
     */
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionEvent> events = new ArrayList<>();

    // ============= AUDIT & SOFT DELETE =============
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ============= BUSINESS KEY PATTERN =============
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StacksTransaction)) return false;
        StacksTransaction that = (StacksTransaction) o;
        return txId != null && txId.equals(that.txId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // ============= CONVENIENCE METHODS =============
    public void addEvent(TransactionEvent event) {
        events.add(event);
        event.setTransaction(this);
    }

    public void markAsDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }

    /**
     * Bu transaction sponsored mı?
     */
    public boolean isSponsored() {
        return sponsorAddress != null && !sponsorAddress.isEmpty();
    }
}
```

**🎓 YENİ KAVRAMLAR**:

1. **FetchType.LAZY**:
   - İlişkili entity'yi hemen yükleme
   - Sadece ihtiyaç olunca yükle
   - Performance optimizasyonu

2. **OneToOne vs OneToMany**:
   - OneToOne: contractCall (max 1 adet)
   - OneToMany: events (0-N adet)

3. **Optional Relationship**:
   - contractCall NULL olabilir
   - contractDeployment NULL olabilir
   - txType'a göre hangisi dolu olduğu değişir

4. **BigDecimal**:
   - Para/miktar için ASLA float/double kullanma!
   - Precision kaybı olur
   - BigDecimal kullan

**❓ Kendin Sor**:
1. Neden success = false olan transaction'lar blockchain'e yazılır?
   - Fee ödeniyor, işlem denenmiş ama başarısız
   - Nonce harcandı, tekrar denenemez

2. FetchType.LAZY ne işe yarar?
   - Gereksiz yüklemeyi önler
   - Transaction yüklenirken Block otomatik yüklenmesin

3. mappedBy ne demek?
   - "Relationship owner ben değilim, karşı taraf"
   - Foreign key karşı tarafta

---

#### 2.3 TransactionEvent.java ve Alt Sınıfları ⭐ POLYMORPHISM

**Bu en kritik kısım! Polymorphic hierarchy var.**

**Önce Kavramı Anla**:
```
TransactionEvent (ABSTRACT BASE CLASS)
├── FTTransferEvent      (Fungible Token transfer)
├── FTMintEvent          (Fungible Token mint)
├── FTBurnEvent          (Fungible Token burn)
├── NFTTransferEvent     (NFT transfer)
├── NFTMintEvent         (NFT mint)
├── NFTBurnEvent         (NFT burn)
├── STXTransferEvent     (Native STX transfer)
├── STXMintEvent         (STX mint)
├── STXBurnEvent         (STX burn)
├── STXLockEvent         (STX lock - staking)
└── SmartContractEvent   (Contract print event)
```

**JPA Inheritance Stratejisi**: JOINED
- Her sınıf için ayrı tablo
- Base table: transaction_event
- Child table: ft_transfer_event, nft_transfer_event, ...
- JOIN ile birleştirilir

**Base Class**:
```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/blockchain/TransactionEvent.java

package com.stacksmonitoring.domain.model.blockchain;

import com.stacksmonitoring.domain.valueobject.EventType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Tüm event tiplerinin base class'ı.
 *
 * ABSTRACT SINIF:
 * - new TransactionEvent() YAPILAMAZ
 * - Sadece alt sınıfları kullanılır
 * - Polymorphism için base tanım
 *
 * INHERITANCE STRATEGY: JOINED
 * - Her alt sınıf için ayrı tablo
 * - Base tablo: transaction_event
 * - Child tablo: ft_transfer_event, nft_mint_event, vs
 * -장점: Normalize, temiz
 * - DEZAVANTAJ: JOIN maliyeti (ama Postgres optimize eder)
 *
 * DISCRIMINATOR:
 * - event_type sütunu hangi tip olduğunu gösterir
 * - Enum değeri: FT_TRANSFER, NFT_MINT, vs
 */
@Entity
@Table(name = "transaction_event", indexes = {
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_event_contract", columnList = "event_type, contract_identifier"),
    @Index(name = "idx_event_transaction", columnList = "transaction_id")
})
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "event_type", discriminatorType = DiscriminatorType.STRING)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class TransactionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Bu event hangi transaction'a ait?
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private StacksTransaction transaction;

    /**
     * Transaction içindeki sıra numarası.
     * İlk event: 0, ikinci: 1, vs
     */
    @Column(name = "event_index", nullable = false)
    private Integer eventIndex;

    /**
     * Event tipi.
     *
     * ÖNEMLİ:
     * - insertable = false, updatable = false
     * - Çünkü Discriminator tarafından otomatik set edilir
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, insertable = false, updatable = false)
    private EventType eventType;

    /**
     * Bu event'i üreten contract.
     *
     * Örnek: "SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1"
     */
    @Column(name = "contract_identifier", length = 150)
    private String contractIdentifier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ============= BUSINESS KEY =============
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionEvent)) return false;
        TransactionEvent that = (TransactionEvent) o;
        // Transaction + eventIndex combination benzersiz
        return transaction != null && transaction.equals(that.transaction)
            && eventIndex != null && eventIndex.equals(that.eventIndex);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * ABSTRACT METHOD - Alt sınıflar implement etmeli!
     *
     * Her event tipi kendi açıklamasını döner.
     * Örnek: "FT Transfer: 1000 tokens from Alice to Bob"
     */
    public abstract String getEventDescription();
}
```

**Alt Sınıf Örneği - FTTransferEvent**:
```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/blockchain/FTTransferEvent.java

package com.stacksmonitoring.domain.model.blockchain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * Fungible Token (eşlenebilir token) transfer event'i.
 *
 * NE ZAMAN OLUŞUR?
 * - Bir kullanıcı custom token transfer ettiğinde
 * - DEX swap yaparken
 * - Yield farming reward alırken
 *
 * ÖRNEK:
 * Alice, Bob'a 1000 USDA (fungible token) gönderdi:
 * - assetIdentifier: "SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.usda-token::usda"
 * - amount: 1000000000 (1000 * 10^6, 6 decimal)
 * - sender: "SP2ABC..."
 * - recipient: "SP3XYZ..."
 */
@Entity
@Table(name = "ft_transfer_event", indexes = {
    @Index(name = "idx_ft_asset", columnList = "asset_identifier"),
    @Index(name = "idx_ft_sender", columnList = "sender"),
    @Index(name = "idx_ft_recipient", columnList = "recipient"),
    @Index(name = "idx_ft_amount", columnList = "amount")
})
@DiscriminatorValue("FT_TRANSFER")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class FTTransferEvent extends TransactionEvent {

    /**
     * Token tanımlayıcısı.
     * Format: <contract>::<token-name>
     * Örnek: "SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.usda-token::usda"
     */
    @Column(name = "asset_identifier", nullable = false, length = 150)
    private String assetIdentifier;

    /**
     * Transfer edilen miktar.
     *
     * ÖNEMLİ:
     * - Her token'ın kendi decimal sayısı var
     * - USDA: 6 decimal (1 USDA = 1000000)
     * - STX: 6 decimal (1 STX = 1000000 microSTX)
     * - precision = 30 → Çok büyük sayılar için
     */
    @Column(nullable = false, precision = 30)
    private BigDecimal amount;

    /**
     * Token'ı gönderen adres.
     */
    @Column(nullable = false, length = 50)
    private String sender;

    /**
     * Token'ı alan adres.
     */
    @Column(nullable = false, length = 50)
    private String recipient;

    /**
     * Abstract method implementation.
     */
    @Override
    public String getEventDescription() {
        return String.format("FT Transfer: %s tokens from %s to %s (amount: %s)",
            assetIdentifier, sender, recipient, amount);
    }
}
```

**📝 DİĞER ALT SINIFLARI OLUŞTUR**:

Aynı pattern'i kullanarak şunları oluştur:
1. `FTMintEvent.java` - Token mint
2. `FTBurnEvent.java` - Token burn
3. `NFTTransferEvent.java` - NFT transfer
4. `NFTMintEvent.java` - NFT mint
5. `NFTBurnEvent.java` - NFT burn
6. `STXTransferEvent.java` - Native STX transfer
7. `STXMintEvent.java` - STX mint (sadece genesis)
8. `STXBurnEvent.java` - STX burn
9. `STXLockEvent.java` - STX lock (staking)
10. `SmartContractEvent.java` - Contract print event

**🎓 POLYMORPHISM ÖĞRENİMİ**:

1. **Abstract Class**:
   - `new TransactionEvent()` yapılamaz
   - Sadece base tanım için
   - Alt sınıflar extend eder

2. **JOINED Strategy**:
   ```sql
   -- Base tablo
   CREATE TABLE transaction_event (
       id BIGINT PRIMARY KEY,
       transaction_id BIGINT,
       event_index INT,
       event_type VARCHAR,  -- Discriminator
       contract_identifier VARCHAR
   );

   -- Child tablo
   CREATE TABLE ft_transfer_event (
       id BIGINT PRIMARY KEY,  -- FK to transaction_event.id
       asset_identifier VARCHAR,
       amount NUMERIC,
       sender VARCHAR,
       recipient VARCHAR
   );

   -- Query yaparken JOIN
   SELECT * FROM transaction_event te
   JOIN ft_transfer_event fte ON te.id = fte.id
   WHERE te.event_type = 'FT_TRANSFER';
   ```

3. **@DiscriminatorValue**:
   - Her alt sınıf kendi değerini belirtir
   - `@DiscriminatorValue("FT_TRANSFER")`
   - event_type sütununa bu değer yazılır

4. **Abstract Method**:
   - `getEventDescription()` her alt sınıf implement eder
   - Runtime'da doğru method çağrılır (polymorphism)

**❓ Kendin Sor**:
1. Neden SINGLE_TABLE değil de JOINED strategy?
   - SINGLE_TABLE: Tek tablo, tüm field'lar içinde
   - JOINED: Her tip için ayrı tablo
   - JOINED daha temiz, normalize

2. insertable=false, updatable=false neden?
   - Discriminator otomatik set edilir
   - Manuel set etmeye gerek yok

3. Abstract method neden?
   - Her event tipi kendi açıklamasını bilir
   - Polymorphic davranış

---

#### 2.4 ContractCall ve ContractDeployment

**Bu opsiyonel entity'ler. Transaction tipine göre dolu olabilir.**

```java
// ContractCall.java - Kısa gösterim
@Entity
@Table(name = "contract_call")
public class ContractCall {
    @Id
    @GeneratedValue
    private Long id;

    @OneToOne
    @JoinColumn(name = "transaction_id")
    private StacksTransaction transaction;

    private String contractIdentifier;  // Hangi contract
    private String functionName;        // Hangi fonksiyon

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> functionArgs;  // Parametreler (JSON)
}

// ContractDeployment.java - Kısa gösterim
@Entity
@Table(name = "contract_deployment")
public class ContractDeployment {
    @Id
    @GeneratedValue
    private Long id;

    @OneToOne
    @JoinColumn(name = "transaction_id")
    private StacksTransaction transaction;

    private String contractIdentifier;
    private String contractName;

    @Column(columnDefinition = "TEXT")
    private String sourceCode;  // Clarity kaynak kodu

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> abi;  // Contract ABI
}
```

**🎓 YENİ KAVRAM: JSONB**:

PostgreSQL'in JSON tipi:
- JSON verisini veritabanında sakla
- Query yapılabilir
- Index oluşturulabilir

```sql
-- JSONB query örneği
SELECT * FROM contract_call
WHERE function_args->>'token-amount' > '1000';
```

---

### 📝 AŞAMA 2 ÖZET

**Oluşturduklarınız**:
- ✅ StacksBlock.java
- ✅ StacksTransaction.java
- ✅ TransactionEvent.java (abstract)
- ✅ 11 event alt sınıfı
- ✅ ContractCall.java
- ✅ ContractDeployment.java

**Öğrendikleriniz**:
- JPA Entity nedir
- Relationship'ler (OneToOne, OneToMany, ManyToOne)
- FetchType.LAZY
- Cascade operations
- Business key pattern
- Soft delete pattern
- Polymorphism (JOINED inheritance)
- Abstract class
- JSONB kullanımı

**Test**:
```bash
mvn clean compile
# Hata yoksa başarılı!
```

---

### 🎯 AŞAMA 3: Domain Entities - User & Monitoring (2 Gün)

[Bu bölüm çok uzun oluyor, devamını yaz...]

---

## Devam Ediyor...

Bu öğrenim rehberi 1800+ satır olacak. Şu an sadece ilk 2 aşamayı detaylı yazdım.

**Soru**: Devam edeyim mi tüm 7 aşamayı tamamlayayım, yoksa bu kadarını önce incele, sonra devam mı?

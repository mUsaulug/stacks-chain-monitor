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

### 🎯 AŞAMA 3: User & Monitoring Entities (2 Gün)

**Hedef**: Kullanıcı ve alert sistemi entity'lerini oluştur.

**Dependency**: Value Object'lere bağlı (UserRole, AlertRuleType, etc.)

#### 3.1 User.java ⭐ KULLANICI ENTITY

**Önce Anla**:
```
User nedir?
- Sistemi kullanan kişi
- Alert rule'lar oluşturur
- Contract'ları izler (monitor)
- Email veya webhook ile bildirim alır

User ile ilişkiler:
User → MonitoredContract (OneToMany)
User → AlertRule (OneToMany)
```

**Kod**:
```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/user/User.java

package com.stacksmonitoring.domain.model.user;

import com.stacksmonitoring.domain.model.monitoring.AlertRule;
import com.stacksmonitoring.domain.model.monitoring.MonitoredContract;
import com.stacksmonitoring.domain.valueobject.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistemin kullanıcısı.
 *
 * YENİ KAVRAMLAR:
 *
 * 1. Audit Annotations:
 *    - @CreatedDate → Entity ilk kaydedildiğinde otomatik set edilir
 *    - @LastModifiedDate → Her update'te otomatik güncellenir
 *    - @EntityListeners(AuditingEntityListener.class) → Spring Data JPA auditing
 *
 * 2. Password Security:
 *    - passwordHash → ASLA plain text password saklanmaz!
 *    - BCrypt hash kullanılır (strength 12)
 *    - Hash one-way function → Geri dönüşü yok
 *
 * 3. Soft Delete vs Hard Delete:
 *    - active = false → Kullanıcı devre dışı (soft)
 *    - Database'den silmiyoruz (hard delete yok)
 *    - Neden? Alert rule'ları, notification geçmişi kaybolmasın
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============= BUSINESS KEY =============
    /**
     * Email adresi - benzersiz tanımlayıcı.
     *
     * ÖNEMLİ:
     * - Login için kullanılır
     * - Email bildirimler için
     * - unique = true → Aynı email ile 2 user olamaz
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // ============= SECURITY =============
    /**
     * BCrypt ile hashlenmiş password.
     *
     * Örnek:
     * Plain: "myPassword123"
     * Hash: "$2a$12$KIXxZ9Vp8R5KqD7..."
     *
     * BCrypt özelliği:
     * - Her hash'te farklı salt kullanır
     * - Aynı password'ün hash'i her seferinde farklı
     * - Strength 12 → 2^12 iteration (güvenli ama yavaş)
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // ============= PROFILE =============
    @Column(name = "full_name", length = 100)
    private String fullName;

    /**
     * Kullanıcı rolü.
     *
     * USER:  Normal kullanıcı
     * ADMIN: Sistem yöneticisi (daha fazla yetki)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    /**
     * Kullanıcı aktif mi?
     *
     * false → Giriş yapamaz, alert'leri çalışmaz
     * true  → Normal kullanım
     */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // ============= RELATIONSHIPS =============
    /**
     * Kullanıcının izlediği contract'lar.
     *
     * Örnek:
     * Alice izliyor:
     * - Arkadiko DEX contract
     * - ALEX lending contract
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MonitoredContract> monitoredContracts = new ArrayList<>();

    /**
     * Kullanıcının oluşturduğu alert rule'lar.
     *
     * Örnek:
     * Alice'in rule'ları:
     * - DEX'te büyük swap olunca email gönder
     * - Lending'de liquidation olunca webhook çağır
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertRule> alertRules = new ArrayList<>();

    // ============= AUDIT FIELDS =============
    /**
     * Kullanıcı ne zaman oluşturuldu?
     *
     * @CreatedDate → İlk save'de otomatik set edilir
     * updatable = false → Sonradan değiştirilemez
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Son güncelleme zamanı.
     *
     * @LastModifiedDate → Her update'te otomatik güncellenir
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ============= BUSINESS KEY PATTERN =============
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return email != null && email.equals(user.email);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // ============= BUSINESS LOGIC =============
    /**
     * Kullanıcı admin mi kontrol et.
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /**
     * Monitored contract ekle.
     *
     * Bidirectional ilişkiyi düzgün kurar:
     * 1. monitoredContracts listesine ekle
     * 2. contract.setUser(this) yap
     */
    public void addMonitoredContract(MonitoredContract contract) {
        monitoredContracts.add(contract);
        contract.setUser(this);
    }

    /**
     * Alert rule ekle.
     */
    public void addAlertRule(AlertRule rule) {
        alertRules.add(rule);
        rule.setUser(this);
    }
}
```

**🎓 YENİ KAVRAMLAR**:

1. **Spring Data JPA Auditing**:
   ```java
   // Main Application class'ına ekle:
   @EnableJpaAuditing
   @SpringBootApplication
   public class Application {
       public static void main(String[] args) {
           SpringApplication.run(Application.class, args);
       }
   }
   ```

2. **BCrypt Password Hashing**:
   ```java
   // SecurityConfiguration'da
   @Bean
   public PasswordEncoder passwordEncoder() {
       return new BCryptPasswordEncoder(12);
   }

   // Kullanımı:
   String plainPassword = "myPassword123";
   String hashedPassword = passwordEncoder.encode(plainPassword);
   // Sonuç: "$2a$12$KIXxZ9Vp8R5KqD7..."

   // Doğrulama:
   boolean matches = passwordEncoder.matches(plainPassword, hashedPassword);
   // true
   ```

3. **orphanRemoval vs cascade**:
   ```java
   // cascade = ALL → User silinince alert rule'lar da silinir
   // orphanRemoval = true → Alert rule user'dan çıkarılınca silinir

   user.getAlertRules().remove(rule);
   // orphanRemoval = true → rule database'den silinir
   // orphanRemoval = false → rule kalır ama user_id = NULL olur
   ```

**❓ Kendin Sor**:
1. Neden password'u hash'liyoruz?
   - Güvenlik! Database çalınsa bile password'ler güvende

2. @CreatedDate nasıl çalışıyor?
   - Spring Data JPA otomatik set ediyor
   - @EnableJpaAuditing gerekli

3. active = false ile delete farkı ne?
   - active = false → Geçici devre dışı
   - delete → Kalıcı silme (ama biz soft delete yapıyoruz)

---

#### 3.2 MonitoredContract.java

**Önce Anla**:
```
MonitoredContract nedir?
- Kullanıcının izlemeye aldığı bir smart contract
- Her contract için birden fazla alert rule olabilir
- Composite unique constraint: (user_id, contract_identifier)
  → Aynı kullanıcı aynı contract'ı 2 kez izleyemez
```

**Kod**:
```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/monitoring/MonitoredContract.java

package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Kullanıcının izlediği bir contract.
 *
 * YENİ KAVRAM:
 *
 * Composite Unique Constraint:
 * - Tek bir sütun unique değil
 * - İki sütun kombinasyonu unique
 * - (user_id, contract_identifier) çifti benzersiz olmalı
 *
 * Örnek:
 * ✅ Alice, ContractA'yı izliyor
 * ✅ Bob, ContractA'yı izliyor
 * ❌ Alice, ContractA'yı 2. kez izleyemez
 */
@Entity
@Table(name = "monitored_contract",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_contract",
            columnNames = {"user_id", "contract_identifier"}
        )
    },
    indexes = {
        @Index(name = "idx_monitored_contract_identifier", columnList = "contract_identifier"),
        @Index(name = "idx_monitored_contract_user", columnList = "user_id"),
        @Index(name = "idx_monitored_contract_active", columnList = "is_active")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonitoredContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============= OWNER =============
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ============= CONTRACT INFO =============
    /**
     * Contract tanımlayıcısı.
     * Format: <address>.<contract-name>
     * Örnek: "SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1"
     */
    @Column(name = "contract_identifier", nullable = false, length = 150)
    private String contractIdentifier;

    @Column(name = "contract_name", length = 100)
    private String contractName;

    @Column(length = 500)
    private String description;

    /**
     * İzleme aktif mi?
     *
     * false → Alert rule'lar çalışmaz
     * true  → Normal izleme
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // ============= RELATIONSHIPS =============
    /**
     * Bu contract için oluşturulmuş alert rule'lar.
     *
     * Örnek:
     * Arkadiko DEX contract için:
     * - swap miktarı > 10000 STX ise alert
     * - slippage > %5 ise alert
     */
    @OneToMany(mappedBy = "monitoredContract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertRule> alertRules = new ArrayList<>();

    // ============= AUDIT =============
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ============= BUSINESS KEY =============
    /**
     * İKİ FIELD KOMBİNASYONU ile equals!
     *
     * user + contractIdentifier benzersiz olmalı.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonitoredContract)) return false;
        MonitoredContract that = (MonitoredContract) o;
        return user != null && user.equals(that.user)
            && contractIdentifier != null && contractIdentifier.equals(that.contractIdentifier);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // ============= BUSINESS LOGIC =============
    public void addAlertRule(AlertRule rule) {
        alertRules.add(rule);
        rule.setMonitoredContract(this);
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
```

**🎓 YENİ KAVRAM: Composite Unique Constraint**:

SQL'de:
```sql
CREATE TABLE monitored_contract (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    contract_identifier VARCHAR,
    -- Composite unique constraint
    UNIQUE (user_id, contract_identifier)
);

-- ✅ OK
INSERT INTO monitored_contract VALUES (1, 1, 'ContractA');
INSERT INTO monitored_contract VALUES (2, 2, 'ContractA');

-- ❌ ERROR: Duplicate
INSERT INTO monitored_contract VALUES (3, 1, 'ContractA');
```

**❓ Kendin Sor**:
1. Neden composite unique constraint?
   - Aynı kullanıcı aynı contract'ı 2 kez izlememeli

2. isActive neden var?
   - Geçici olarak izlemeyi durdurabilirsin
   - Contract'ı silmene gerek yok

---

#### 3.3 AlertRule.java ⭐ ABSTRACT BASE CLASS

**Önce Anla - Polymorphism**:
```
AlertRule (ABSTRACT)
├── ContractCallAlertRule      → Contract fonksiyon çağrısı
├── TokenTransferAlertRule     → Token transfer
├── FailedTransactionAlertRule → Başarısız transaction
├── PrintEventAlertRule        → Contract print event
└── AddressActivityAlertRule   → Adres aktivitesi

FARK:
- TransactionEvent → JOINED strategy (her tip ayrı tablo)
- AlertRule → SINGLE_TABLE strategy (hepsi tek tablo)

Neden farklı?
- Event'ler çok sayıda olur (1M+) → JOINED daha temiz
- Alert rule az olur (1000'ler) → SINGLE_TABLE daha hızlı
```

**Kod**:
```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/monitoring/AlertRule.java

package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.user.User;
import com.stacksmonitoring.domain.valueobject.AlertRuleType;
import com.stacksmonitoring.domain.valueobject.AlertSeverity;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Alert kuralı base class.
 *
 * YENİ KAVRAMLAR:
 *
 * 1. SINGLE_TABLE Inheritance:
 *    - Tek bir tablo: alert_rule
 *    - Discriminator: rule_type sütunu (CONTRACT_CALL, TOKEN_TRANSFER, etc)
 *    - Tüm alt sınıfların field'ları aynı tabloda
 *    -장점: JOIN yok, hızlı query
 *    - DEZAVANTAJ: Çok null field (bir rule tipi diğer tiplerin field'larını kullanmaz)
 *
 * 2. Optimistic Locking (@Version):
 *    - Aynı anda 2 kişi aynı rule'u güncelleyemez
 *    - version field otomatik artırılır
 *    - Update sırasında version kontrolü yapılır
 *    - Version uymazsa exception → Retry
 *
 * 3. Cooldown Pattern:
 *    - Spam önleme mekanizması
 *    - lastTriggeredAt + cooldownMinutes → Ne zaman tekrar trigger olabilir?
 *    - Örnek: 60 dk cooldown → Aynı rule 1 saat içinde 2. kez trigger olmaz
 *
 * 4. Multi-channel Notification:
 *    - notificationChannels: [EMAIL, WEBHOOK]
 *    - JSON array olarak saklanır
 *    - Bir rule hem email hem webhook gönderebilir
 */
@Entity
@Table(name = "alert_rule", indexes = {
    @Index(name = "idx_alert_rule_user", columnList = "user_id"),
    @Index(name = "idx_alert_rule_contract", columnList = "monitored_contract_id"),
    @Index(name = "idx_alert_rule_active", columnList = "is_active"),
    @Index(name = "idx_alert_rule_type", columnList = "rule_type")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "rule_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============= OWNER =============
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * İsteğe bağlı: Belirli bir contract için mi?
     *
     * NULL olabilir → Tüm contract'lar için
     * Dolu → Sadece bu contract için
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitored_contract_id")
    private MonitoredContract monitoredContract;

    // ============= RULE INFO =============
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Column(length = 500)
    private String description;

    /**
     * Rule tipi (discriminator).
     *
     * insertable = false, updatable = false
     * → Çünkü Hibernate otomatik yönetiyor
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, insertable = false, updatable = false)
    private AlertRuleType ruleType;

    /**
     * Alert şiddeti.
     *
     * INFO → Bilgilendirme
     * WARNING → Dikkat edilmeli
     * CRITICAL → Acil müdahale gerekli
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity = AlertSeverity.INFO;

    /**
     * Rule aktif mi?
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // ============= COOLDOWN =============
    /**
     * Cooldown süresi (dakika).
     *
     * Örnek: 60 dakika
     * → Rule trigger oldu saat 14:00
     * → 15:00'a kadar tekrar trigger olmaz
     *
     * Neden?
     * - Spam önleme
     * - Email flood önleme
     * - Webhook rate limit aşmama
     */
    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes = 60;

    /**
     * Son trigger zamanı.
     *
     * Cooldown hesaplamak için:
     * now() < lastTriggeredAt + cooldownMinutes → Hala cooldown'da
     */
    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    // ============= NOTIFICATION CONFIG =============
    /**
     * Bildirim kanalları.
     *
     * PostgreSQL JSONB olarak saklanır:
     * ["EMAIL", "WEBHOOK"]
     *
     * Java tarafında List<NotificationChannel>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_channels", columnDefinition = "jsonb")
    private List<NotificationChannel> notificationChannels = new ArrayList<>();

    /**
     * Email adresleri (virgülle ayrılmış).
     * Örnek: "alice@example.com,bob@example.com"
     */
    @Column(name = "notification_emails", length = 500)
    private String notificationEmails;

    /**
     * Webhook URL.
     * POST request gönderilir.
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    // ============= RELATIONSHIPS =============
    @OneToMany(mappedBy = "alertRule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertNotification> notifications = new ArrayList<>();

    // ============= OPTIMISTIC LOCKING =============
    /**
     * @Version → Optimistic locking.
     *
     * Senaryo:
     * 1. Alice rule'u okuyor (version = 5)
     * 2. Bob rule'u okuyor (version = 5)
     * 3. Alice update ediyor → version = 6 olur ✅
     * 4. Bob update ediyor → version hala 5 → ERROR ❌
     *    OptimisticLockException
     * 5. Bob tekrar okumalı ve güncellemeli
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // ============= AUDIT =============
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ============= BUSINESS LOGIC =============
    /**
     * Rule cooldown'da mı kontrol et.
     *
     * @return true ise trigger olmamalı
     */
    public boolean isInCooldown() {
        if (lastTriggeredAt == null) {
            return false;  // İlk trigger
        }
        Instant cooldownEndTime = lastTriggeredAt.plusSeconds(cooldownMinutes * 60L);
        return Instant.now().isBefore(cooldownEndTime);
    }

    /**
     * Rule trigger olduğunu işaretle.
     * Cooldown başlatır.
     */
    public void markAsTriggered() {
        this.lastTriggeredAt = Instant.now();
    }

    /**
     * Belirli bir notification channel aktif mi?
     */
    public boolean hasChannel(NotificationChannel channel) {
        return notificationChannels != null && notificationChannels.contains(channel);
    }

    // ============= ABSTRACT METHODS =============
    /**
     * Bu rule, verilen context'e match ediyor mu?
     *
     * Her alt sınıf kendi matching logic'ini implement eder.
     *
     * @param context Transaction, Event, veya başka bir nesne
     * @return true ise alert trigger edilmeli
     */
    public abstract boolean matches(Object context);

    /**
     * Alert mesajında gösterilecek trigger açıklaması.
     *
     * Örnek:
     * - "Contract swap-x-for-y called with amount > 10000 STX"
     * - "Token transfer of 50000 USDA detected"
     */
    public abstract String getTriggerDescription(Object context);

    // ============= EQUALS/HASHCODE =============
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertRule)) return false;
        AlertRule alertRule = (AlertRule) o;
        return id != null && id.equals(alertRule.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
```

**🎓 YENİ KAVRAMLAR**:

1. **SINGLE_TABLE Inheritance**:
   ```sql
   CREATE TABLE alert_rule (
       id BIGINT PRIMARY KEY,
       rule_type VARCHAR,  -- Discriminator: CONTRACT_CALL, TOKEN_TRANSFER, etc
       rule_name VARCHAR,
       -- Base fields
       user_id BIGINT,
       is_active BOOLEAN,
       -- ContractCallAlertRule fields
       contract_identifier VARCHAR,  -- Sadece CONTRACT_CALL için dolu
       function_name VARCHAR,         -- Sadece CONTRACT_CALL için dolu
       -- TokenTransferAlertRule fields
       token_identifier VARCHAR,      -- Sadece TOKEN_TRANSFER için dolu
       minimum_amount NUMERIC,        -- Sadece TOKEN_TRANSFER için dolu
       -- ... diğer rule tipleri fields
   );
   ```

2. **Optimistic Locking**:
   ```java
   // Thread 1
   AlertRule rule = repository.findById(1L).get();  // version = 5
   rule.setIsActive(false);
   repository.save(rule);  // version = 6 olur ✅

   // Thread 2 (aynı anda)
   AlertRule rule = repository.findById(1L).get();  // version = 5
   rule.setCooldownMinutes(30);
   repository.save(rule);  // OptimisticLockException ❌
   // Çünkü database'de version = 6, ama Thread 2'de version = 5
   ```

3. **Cooldown Logic**:
   ```java
   // 14:00'da trigger oldu
   rule.markAsTriggered();  // lastTriggeredAt = 14:00

   // 14:30'da tekrar kontrol
   rule.isInCooldown();  // true (60 dk cooldown, henüz 30 dk geçti)

   // 15:00'da tekrar kontrol
   rule.isInCooldown();  // false (60 dk doldu, tekrar trigger olabilir)
   ```

4. **JSON Field (PostgreSQL JSONB)**:
   ```sql
   -- Database'de
   SELECT notification_channels FROM alert_rule WHERE id = 1;
   -- Sonuç: ["EMAIL", "WEBHOOK"]

   -- Query yapabilirsin
   SELECT * FROM alert_rule
   WHERE notification_channels @> '["EMAIL"]'::jsonb;
   ```

**❓ Kendin Sor**:
1. Neden SINGLE_TABLE, JOINED değil?
   - Alert rule az sayıda olur
   - JOIN maliyeti gereksiz
   - Tek tablodan query daha hızlı

2. @Version ne işe yarar?
   - Concurrent update kontrolü
   - Lost update problem önleme

3. Cooldown olmasa ne olur?
   - Her saniye email gelir (spam!)
   - Webhook rate limit aşılır
   - User rahatsız olur

---

### 📝 AŞAMA 3 Devamı...

**Şimdi Alt Sınıfları Oluştur**:

Aynı pattern'i kullanarak 5 alt sınıf oluşturacaksın. Örnek olarak birini gösteriyorum:

#### 3.4 ContractCallAlertRule.java

```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/monitoring/ContractCallAlertRule.java

package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import jakarta.persistence.*;
import lombok.*;

/**
 * Contract fonksiyon çağrısı alert rule.
 *
 * NE ZAMAN TETİKLENİR?
 * - Belirli bir contract'ın
 * - Belirli bir fonksiyonu çağrıldığında
 *
 * ÖRNEK:
 * Rule: Arkadiko DEX'te swap-x-for-y fonksiyonu çağrıldığında alert ver
 *
 * matches() logic:
 * - context bir ContractCall mi?
 * - contractIdentifier eşleşiyor mu?
 * - functionName eşleşiyor mu?
 * → Evet ise true döner, alert trigger olur
 */
@Entity
@DiscriminatorValue("CONTRACT_CALL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractCallAlertRule extends AlertRule {

    /**
     * Hangi contract?
     * Örnek: "SP2C2YFP12AJZB4MABJBAJ55XECVS7E4PMMZ89YZR.arkadiko-swap-v2-1"
     */
    @Column(name = "contract_identifier", length = 150)
    private String contractIdentifier;

    /**
     * Hangi fonksiyon?
     * Örnek: "swap-x-for-y"
     */
    @Column(name = "function_name", length = 100)
    private String functionName;

    /**
     * ABSTRACT METHOD IMPLEMENTATION
     *
     * Context bir ContractCall nesnesi olmalı.
     */
    @Override
    public boolean matches(Object context) {
        if (!(context instanceof ContractCall)) {
            return false;
        }

        ContractCall contractCall = (ContractCall) context;

        // Contract identifier eşleşiyor mu?
        boolean contractMatch = contractIdentifier == null ||
                               contractIdentifier.equals(contractCall.getContractIdentifier());

        // Function name eşleşiyor mu?
        boolean functionMatch = functionName == null ||
                               functionName.equals(contractCall.getFunctionName());

        return contractMatch && functionMatch;
    }

    /**
     * Alert mesajı için açıklama.
     */
    @Override
    public String getTriggerDescription(Object context) {
        ContractCall contractCall = (ContractCall) context;
        return String.format("Contract call detected: %s::%s",
            contractCall.getContractIdentifier(),
            contractCall.getFunctionName()
        );
    }
}
```

**Diğer 4 Alt Sınıfı da Oluştur**:
- `TokenTransferAlertRule.java` - Token transfer (minimum_amount field)
- `FailedTransactionAlertRule.java` - Başarısız tx (monitored_address field)
- `PrintEventAlertRule.java` - Print event (topic field)
- `AddressActivityAlertRule.java` - Adres aktivite (address field)

Her biri:
1. `@DiscriminatorValue("RULE_TYPE")`
2. Kendi spesifik field'ları
3. `matches()` implement
4. `getTriggerDescription()` implement

---

#### 3.5 AlertNotification.java

**Son entity - Bildirim kaydı**:

```java
// Konum: src/main/java/com/stacksmonitoring/domain/model/monitoring/AlertNotification.java

package com.stacksmonitoring.domain.model.monitoring;

import com.stacksmonitoring.domain.model.blockchain.StacksTransaction;
import com.stacksmonitoring.domain.model.blockchain.TransactionEvent;
import com.stacksmonitoring.domain.valueobject.NotificationChannel;
import com.stacksmonitoring.domain.valueobject.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

/**
 * Gönderilen bir bildirim kaydı.
 *
 * YENİ KAVRAM: Retry Logic
 *
 * Bildirim gönderimi başarısız olabilir:
 * - Email server down
 * - Webhook endpoint error
 * - Network timeout
 *
 * Retry mekanizması:
 * 1. attemptCount = 0, status = PENDING
 * 2. Gönder → Başarısız
 * 3. attemptCount = 1, status = FAILED
 * 4. shouldRetry() true döner (max 3)
 * 5. Tekrar gönder → Başarısız
 * 6. attemptCount = 2, status = FAILED
 * 7. shouldRetry() true döner
 * 8. Tekrar gönder → Başarılı ✅
 * 9. status = SENT, sentAt = now()
 */
@Entity
@Table(name = "alert_notification", indexes = {
    @Index(name = "idx_notification_rule", columnList = "alert_rule_id"),
    @Index(name = "idx_notification_transaction", columnList = "transaction_id"),
    @Index(name = "idx_notification_triggered_at", columnList = "triggered_at"),
    @Index(name = "idx_notification_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============= RELATIONSHIPS =============
    /**
     * Hangi rule trigger oldu?
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;

    /**
     * Hangi transaction trigger etti?
     * (İsteğe bağlı - bazı rule'lar transaction'sız olabilir)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private StacksTransaction transaction;

    /**
     * Hangi event trigger etti?
     * (İsteğe bağlı)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private TransactionEvent event;

    // ============= NOTIFICATION INFO =============
    /**
     * Bildirim kanalı.
     * EMAIL veya WEBHOOK
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    /**
     * Bildirim durumu.
     * PENDING → Henüz gönderilmedi
     * SENT → Başarıyla gönderildi
     * FAILED → Gönderim başarısız (retry olabilir)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    /**
     * Alert ne zaman trigger oldu?
     */
    @Column(nullable = false)
    private Instant triggeredAt;

    /**
     * Bildirim ne zaman gönderildi?
     * (status = SENT ise dolu)
     */
    @Column
    private Instant sentAt;

    /**
     * Bildirim mesajı.
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * Başarısızlık sebebi.
     * (status = FAILED ise dolu)
     */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // ============= RETRY LOGIC =============
    /**
     * Kaç kere denendiğini takip eder.
     *
     * 0 → İlk deneme
     * 1 → 1. retry
     * 2 → 2. retry
     * 3 → Son deneme (max)
     */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    // ============= AUDIT =============
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ============= BUSINESS LOGIC =============
    /**
     * Bildirim başarıyla gönderildi işaretle.
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Bildirim başarısız oldu işaretle.
     */
    public void markAsFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * Attempt sayısını artır.
     */
    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    /**
     * Retry yapılmalı mı kontrol et.
     *
     * @return true ise tekrar denenebilir
     */
    public boolean shouldRetry() {
        return attemptCount < 3 && status == NotificationStatus.FAILED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlertNotification)) return false;
        AlertNotification that = (AlertNotification) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
```

**🎓 RETRY LOGIC**:

```java
// NotificationDispatcher'da kullanım
@Async
public void dispatch(AlertNotification notification) {
    notification.incrementAttemptCount();  // 1

    try {
        // Email/webhook gönder
        send(notification);
        notification.markAsSent();  // ✅
    } catch (Exception e) {
        notification.markAsFailed(e.getMessage());  // ❌

        if (notification.shouldRetry()) {
            // 5 saniye sonra tekrar dene
            Thread.sleep(5000);
            dispatch(notification);  // Recursive retry
        }
    }

    repository.save(notification);
}
```

---

### 📝 AŞAMA 3 ÖZET

**Oluşturduklarınız**:
- ✅ User.java
- ✅ MonitoredContract.java
- ✅ AlertRule.java (abstract)
- ✅ 5 alert rule alt sınıfı
- ✅ AlertNotification.java

**Öğrendikleriniz**:
- Spring Data JPA Auditing (@CreatedDate, @LastModifiedDate)
- BCrypt password hashing
- Composite unique constraint
- SINGLE_TABLE inheritance
- Optimistic locking (@Version)
- Cooldown pattern
- JSONB field kullanımı
- Retry logic
- Abstract method implementation

**Test**:
```bash
mvn clean compile
```

---

Devam ediyor... Şimdi Aşama 4-7'yi de ekliyorum, biraz bekleyin...

### 🎯 AŞAMA 4: Repository Interfaces (1 Gün)

**Hedef**: Spring Data JPA repository interface'lerini oluştur.

**Magic**: Interface yazıyorsun, Spring implementation'ı otomatik oluşturuyor!

#### Repository Nedir?

```
Repository = Database ile konuşan katman

Application Service
       ↓
   Repository Interface  (sen yazıyorsun)
       ↓
   Repository Implementation  (Spring otomatik oluşturuyor!)
       ↓
   Database
```

#### 4.1 StacksBlockRepository.java ⭐ İLK REPOSITORY

```java
// Konum: src/main/java/com/stacksmonitoring/domain/repository/StacksBlockRepository.java

package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.blockchain.StacksBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StacksBlock repository.
 *
 * YENİ KAVRAMLAR:
 *
 * 1. JpaRepository<Entity, IdType>:
 *    - CRUD metodları otomatik geliyor
 *    - findAll(), findById(), save(), delete(), count() vs
 *
 * 2. Method Naming Convention:
 *    - findByXxx → SELECT * FROM ... WHERE xxx = ?
 *    - existsByXxx → SELECT COUNT(*) WHERE xxx = ?
 *    - Spring method adından SQL üretiyor!
 *
 * 3. @Query:
 *    - Karmaşık query'ler için JPQL yazıyorsun
 *    - JPQL = Java Persistence Query Language
 *    - SQL benzeri ama entity ismi kullanıyor (tablo ismi değil)
 *
 * 4. Optional<T>:
 *    - Null döndürmek yerine Optional döndür
 *    - Optional.empty() → Bulunamadı
 *    - Optional.of(value) → Bulundu
 */
@Repository
public interface StacksBlockRepository extends JpaRepository<StacksBlock, Long> {

    // ============= MAGIC METHODS (Spring otomatik implement eder) =============

    /**
     * Block hash'e göre bul.
     *
     * Spring bu method adından şu SQL'i üretir:
     * SELECT * FROM stacks_block WHERE block_hash = ?
     */
    Optional<StacksBlock> findByBlockHash(String blockHash);

    /**
     * Block height'a göre bul.
     *
     * SQL: SELECT * FROM stacks_block WHERE block_height = ?
     */
    Optional<StacksBlock> findByBlockHeight(Long blockHeight);

    /**
     * Block hash var mı kontrol et.
     *
     * SQL: SELECT COUNT(*) > 0 FROM stacks_block WHERE block_hash = ?
     */
    boolean existsByBlockHash(String blockHash);

    /**
     * Block height var mı kontrol et.
     */
    boolean existsByBlockHeight(Long blockHeight);

    // ============= CUSTOM JPQL QUERIES =============

    /**
     * Soft delete edilmemiş (aktif) blokları getir.
     *
     * ÖNEMLİ:
     * - JPQL kullanıyor (Java Persistence Query Language)
     * - SELECT b FROM StacksBlock b → b bir alias (tablo adı DEĞİL, entity adı!)
     * - ORDER BY → Sıralama
     */
    @Query("SELECT b FROM StacksBlock b WHERE b.deleted = false ORDER BY b.blockHeight DESC")
    List<StacksBlock> findActiveBlocks();

    /**
     * Zaman aralığında blokları getir.
     *
     * @Param → Query parametresini bind eder
     * :startTime → JPQL parameter placeholder
     */
    @Query("SELECT b FROM StacksBlock b " +
           "WHERE b.timestamp >= :startTime AND b.timestamp <= :endTime " +
           "AND b.deleted = false " +
           "ORDER BY b.blockHeight ASC")
    List<StacksBlock> findBlocksByTimeRange(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * En yüksek block height'ı bul.
     *
     * SQL: SELECT MAX(block_height) FROM stacks_block WHERE deleted = false
     */
    @Query("SELECT MAX(b.blockHeight) FROM StacksBlock b WHERE b.deleted = false")
    Optional<Long> findMaxBlockHeight();
}
```

**🎓 SPRING DATA JPA MAGIC**:

```java
// Method naming convention örnekleri:

// 1. Basit equality
Optional<User> findByEmail(String email);
// SQL: SELECT * FROM users WHERE email = ?

// 2. AND condition
List<AlertRule> findByUserIdAndIsActive(Long userId, Boolean isActive);
// SQL: SELECT * FROM alert_rule WHERE user_id = ? AND is_active = ?

// 3. OR condition
List<StacksTransaction> findBySenderOrRecipient(String sender, String recipient);
// SQL: SELECT * FROM stacks_transaction WHERE sender = ? OR recipient = ?

// 4. Greater than / Less than
List<StacksBlock> findByBlockHeightGreaterThan(Long height);
// SQL: SELECT * FROM stacks_block WHERE block_height > ?

// 5. Like (pattern matching)
List<MonitoredContract> findByContractIdentifierLike(String pattern);
// SQL: SELECT * FROM monitored_contract WHERE contract_identifier LIKE ?

// 6. In (list)
List<AlertRule> findByRuleTypeIn(List<AlertRuleType> types);
// SQL: SELECT * FROM alert_rule WHERE rule_type IN (?, ?, ...)

// 7. Between
List<StacksBlock> findByTimestampBetween(Instant start, Instant end);
// SQL: SELECT * FROM stacks_block WHERE timestamp BETWEEN ? AND ?

// 8. OrderBy
List<StacksBlock> findByDeletedFalseOrderByBlockHeightDesc();
// SQL: SELECT * FROM stacks_block WHERE deleted = false ORDER BY block_height DESC

// 9. Count
long countBySuccess(Boolean success);
// SQL: SELECT COUNT(*) FROM stacks_transaction WHERE success = ?

// 10. Delete
void deleteByBlockHash(String blockHash);
// SQL: DELETE FROM stacks_block WHERE block_hash = ?
```

#### 4.2 Diğer Repository'leri Oluştur

Aynı pattern'i kullanarak şunları oluştur:

```
1. StacksTransactionRepository.java
   - findByTxId(String txId)
   - findBySender(String sender)
   - findBySuccess(Boolean success)
   - findByBlock(StacksBlock block)

2. TransactionEventRepository.java
   - findByTransaction(StacksTransaction transaction)
   - findByEventType(EventType eventType)
   - findByContractIdentifier(String contractIdentifier)

3. ContractCallRepository.java
   - findByContractIdentifier(String contractIdentifier)
   - findByFunctionName(String functionName)

4. ContractDeploymentRepository.java
   - findByContractIdentifier(String contractIdentifier)

5. UserRepository.java
   - findByEmail(String email) ⭐ Login için
   - existsByEmail(String email) ⭐ Register kontrolü

6. MonitoredContractRepository.java
   - findByUser(User user)
   - findByUserAndIsActive(User user, Boolean isActive)
   - findByUserAndContractIdentifier(User user, String contractIdentifier)

7. AlertRuleRepository.java ⭐ ÖNEMLİ
   - findByUserId(Long userId)
   - findActiveByUserId(Long userId)
   - findAllActive()
   - findActiveByRuleType(AlertRuleType ruleType) → Alert matching için

8. AlertNotificationRepository.java
   - findByAlertRule(AlertRule alertRule)
   - findByStatus(NotificationStatus status)
   - findByStatusAndAttemptCountLessThan(...) → Retry için
```

**🎓 JPQL vs SQL**:

```java
// JPQL (entity kullan)
@Query("SELECT b FROM StacksBlock b WHERE b.blockHeight > :height")

// Native SQL (tablo kullan)
@Query(value = "SELECT * FROM stacks_block WHERE block_height > :height", nativeQuery = true)

// JPQL tercih et çünkü:
// - Database bağımsız
// - Entity ilişkilerini biliyor
// - Type-safe
```

#### AŞAMA 4 ÖZET

**Oluşturduklarınız**:
- ✅ 9 repository interface

**Öğrendikleriniz**:
- JpaRepository extend
- Method naming convention
- @Query ile JPQL
- @Param kullanımı
- Optional<T> dönüş tipi

**Test**:
```bash
mvn clean compile
```

---

### 🎯 AŞAMA 5: Infrastructure Layer (3 Gün)

**Hedef**: Security, Parser, ve teknik altyapıyı oluştur.

#### 5.1 Security Configuration (2 Gün)

**Dependency Sırası**:
```
1. JwtTokenService
2. CustomUserDetailsService
3. JwtAuthenticationFilter
4. ChainhookHmacFilter
5. RateLimitFilter
6. SecurityConfiguration (hepsini bir araya getirir)
```

##### 5.1.1 JwtTokenService.java ⭐ JWT TOKENüretme

```java
// Konum: src/main/java/com/stacksmonitoring/infrastructure/config/JwtTokenService.java

// ÖNEMLİ METODLAR:

/**
 * JWT token oluştur.
 */
public String generateToken(UserDetails userDetails, String role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", role);
    claims.put("iss", issuer);  // Issuer

    return Jwts.builder()
            .setClaims(claims)
            .setSubject(userDetails.getUsername())  // email
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expirationMs))  // 24 saat
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}

/**
 * Token'dan username (email) çıkar.
 */
public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
}

/**
 * Token valid mi kontrol et.
 */
public Boolean validateToken(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
}
```

**JWT Token Formatı**:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiVVNFUiIsImlzcyI6InN0YWNrcy1tb25pdG9yIiwic3ViIjoiYWxpY2VAZXhhbXBsZS5jb20iLCJpYXQiOjE3MTAwMDAwMDAsImV4cCI6MTcxMDA4NjQwMH0.signature

Header.Payload.Signature

Header: {"alg":"HS256","typ":"JWT"}
Payload: {"role":"USER","iss":"stacks-monitor","sub":"alice@example.com","iat":1710000000,"exp":1710086400}
Signature: HMAC-SHA256(header + "." + payload, secretKey)
```

##### 5.1.2 JwtAuthenticationFilter.java ⭐ FILTER

```java
// Her HTTP request'te JWT kontrolü yapar

@Override
protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
) throws ServletException, IOException {

    // 1. Authorization header'dan token çıkar
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);  // Token yok, devam et
        return;
    }

    String jwt = authHeader.substring(7);  // "Bearer " kısmını çıkar

    // 2. Token'dan email çıkar
    String userEmail = jwtTokenService.extractUsername(jwt);

    // 3. User zaten authenticate edilmemiş ise
    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        // 4. User'ı database'den yükle
        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

        // 5. Token valid mi kontrol et
        if (jwtTokenService.validateToken(jwt, userDetails)) {
            // 6. Spring Security context'e kullanıcıyı ekle
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    }

    // 7. Filter chain devam et
    filterChain.doFilter(request, response);
}
```

**Filter Chain Sırası** (ÇOK ÖNEMLİ):
```
HTTP Request
    ↓
1. RateLimitFilter          → Rate limit kontrolü (en önce!)
    ↓
2. ChainhookHmacFilter      → Webhook HMAC doğrulama
    ↓
3. JwtAuthenticationFilter  → JWT doğrulama
    ↓
4. UsernamePasswordAuthenticationFilter (Spring default)
    ↓
Controller
```

##### 5.1.3 SecurityConfiguration.java ⭐ SECURITY SETUP

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)  // JWT kullanıyoruz, CSRF disable
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (authentication gerekmez)
                .requestMatchers(
                    "/api/v1/auth/**",      // Login, Register
                    "/api/v1/webhook/**",   // Chainhook webhook
                    "/api/v1/blocks/**",    // Public block queries
                    "/api/v1/transactions/**"  // Public transaction queries
                ).permitAll()
                // Diğer tüm endpoint'ler authentication gerektirir
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // JWT = stateless
            )
            .authenticationProvider(authenticationProvider())
            // Filter'ları ekle (SIRA ÖNEMLİ!)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(chainhookHmacFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

#### 5.2 Parser (1 Gün)

##### ChainhookPayloadParser.java ⭐ EN KARMAŞIK SINIF

**440 satır - Webhook DTO'larını Entity'lere çevirir**

```java
// Ana metodlar:

/**
 * BlockEventDto → StacksBlock
 */
public StacksBlock parseBlock(BlockEventDto blockEventDto) {
    StacksBlock block = new StacksBlock();
    block.setBlockHash(blockEventDto.getBlockIdentifier().getHash());
    block.setBlockHeight(blockEventDto.getBlockIdentifier().getIndex());
    block.setTimestamp(blockEventDto.getTimestamp());
    // ...
    return block;
}

/**
 * TransactionDto → StacksTransaction
 */
public StacksTransaction parseTransaction(TransactionDto txDto, StacksBlock block) {
    StacksTransaction tx = new StacksTransaction();
    tx.setTxId(txDto.getTransactionIdentifier().getHash());
    tx.setTxType(parseTransactionType(txDto.getMetadata().getKind()));
    tx.setSender(txDto.getMetadata().getSender());
    tx.setSuccess(txDto.getMetadata().getReceipt().getOutcome().equals("success"));
    // ...

    // Contract call parse
    if (txDto.getMetadata().getKind() != null && hasContractCall(txDto)) {
        tx.setContractCall(parseContractCall(txDto, tx));
    }

    // Events parse
    if (txDto.getMetadata().getReceipt().getEvents() != null) {
        for (int i = 0; i < txDto.getMetadata().getReceipt().getEvents().size(); i++) {
            EventDto eventDto = txDto.getMetadata().getReceipt().getEvents().get(i);
            TransactionEvent event = parseEvent(eventDto, tx, i);
            if (event != null) {
                tx.addEvent(event);
            }
        }
    }

    return tx;
}

/**
 * EventDto → TransactionEvent (polymorphic!)
 *
 * 11 farklı event tipi var, switch ile ayırıyoruz.
 */
private TransactionEvent parseEvent(EventDto eventDto, StacksTransaction tx, int index) {
    String eventType = eventDto.getType();

    // Java 17 switch expression
    return switch (eventType) {
        case "FTTransferEvent" -> parseFTTransferEvent(eventDto, tx, index);
        case "FTMintEvent" -> parseFTMintEvent(eventDto, tx, index);
        case "NFTTransferEvent" -> parseNFTTransferEvent(eventDto, tx, index);
        case "STXTransferEvent" -> parseSTXTransferEvent(eventDto, tx, index);
        case "SmartContractEvent" -> parseSmartContractEvent(eventDto, tx, index);
        // ... 11 case total
        default -> {
            log.warn("Unknown event type: {}", eventType);
            yield null;
        }
    };
}
```

**🎓 SWITCH EXPRESSION (Java 17)**:
```java
// Old style (Java 8)
String result;
switch (value) {
    case "A":
        result = "Option A";
        break;
    case "B":
        result = "Option B";
        break;
    default:
        result = "Unknown";
}

// New style (Java 17)
String result = switch (value) {
    case "A" -> "Option A";
    case "B" -> "Option B";
    default -> "Unknown";
};
```

#### AŞAMA 5 ÖZET

**Oluşturduklarınız**:
- ✅ JwtTokenService
- ✅ CustomUserDetailsService
- ✅ JwtAuthenticationFilter
- ✅ ChainhookHmacFilter
- ✅ RateLimitFilter
- ✅ SecurityConfiguration
- ✅ ChainhookPayloadParser

**Öğrendikleriniz**:
- JWT token generation/validation
- Spring Security filter chain
- Filter sırası
- Stateless authentication
- HMAC signature validation
- Rate limiting (Bucket4j)
- Switch expression (Java 17)
- DTO → Entity mapping

---

### 🎯 AŞAMA 6: Application Services (4 Gün)

**Hedef**: Business logic katmanını oluştur.

#### Service Layer Pattern

```java
@Service
@RequiredArgsConstructor  // Lombok constructor injection
@Slf4j                    // Lombok logger
@Transactional            // Class level → Tüm metodlar transactional
public class MyService {

    private final MyRepository repository;  // Final = immutable, constructor injection

    public MyEntity create(CreateDto dto) {
        // Business logic
        MyEntity entity = new MyEntity();
        // ...
        return repository.save(entity);
    }

    @Transactional(readOnly = true)  // Read-only optimization
    public List<MyEntity> getAll() {
        return repository.findAll();
    }
}
```

#### 6.1 Core Services

##### 6.1.1 ProcessChainhookPayloadUseCase.java ⭐ EN KRİTİK

**Webhook işleme - Ana iş akışı**

```java
@Transactional
public ProcessingResult processPayload(ChainhookPayloadDto payload) {
    ProcessingResult result = new ProcessingResult();

    try {
        // 1. Rollback'leri işle (blockchain reorganization)
        if (payload.getRollback() != null && !payload.getRollback().isEmpty()) {
            result.rollbackCount = handleRollbacks(payload.getRollback());
        }

        // 2. Yeni blokları işle
        if (payload.getApply() != null && !payload.getApply().isEmpty()) {
            result.applyCount = handleApplies(payload.getApply());
        }

        result.success = true;
    } catch (Exception e) {
        log.error("Error processing payload", e);
        result.success = false;
        result.errorMessage = e.getMessage();
        throw new RuntimeException("Failed to process payload", e);
    }

    return result;
}

/**
 * Yeni blokları işle.
 *
 * İŞ AKIŞI:
 * 1. Block parse et
 * 2. Transaction'ları parse et
 * 3. Event'leri parse et
 * 4. Database'e kaydet (cascade ile hepsi)
 * 5. Alert rule'ları değerlendir ⭐
 * 6. Notification'ları gönder ⭐
 */
private int handleApplies(List<BlockEventDto> applyEvents) {
    int count = 0;
    List<AlertNotification> allNotifications = new ArrayList<>();

    for (BlockEventDto blockEvent : applyEvents) {
        // Block zaten var mı kontrol (idempotency)
        String blockHash = blockEvent.getBlockIdentifier().getHash();
        if (blockRepository.existsByBlockHash(blockHash)) {
            continue;
        }

        // Parse
        StacksBlock block = parser.parseBlock(blockEvent);

        if (blockEvent.getTransactions() != null) {
            for (TransactionDto txDto : blockEvent.getTransactions()) {
                StacksTransaction tx = parser.parseTransaction(txDto, block);
                block.addTransaction(tx);
            }
        }

        // Save (cascade ile tx ve event'ler de kaydedilir)
        blockRepository.save(block);
        count++;

        // Alert evaluation ⭐
        for (StacksTransaction tx : block.getTransactions()) {
            List<AlertNotification> notifications =
                alertMatchingService.evaluateTransaction(tx);
            allNotifications.addAll(notifications);
        }
    }

    // Notification dispatch ⭐
    if (!allNotifications.isEmpty()) {
        notificationDispatcher.dispatchBatch(allNotifications);
    }

    return count;
}
```

##### 6.1.2 AlertMatchingService.java ⭐ CACHE-OPTIMIZED

**O(1) alert matching**

```java
/**
 * Transaction'ı tüm aktif rule'lara karşı değerlendir.
 */
@Transactional
public List<AlertNotification> evaluateTransaction(StacksTransaction transaction) {
    List<AlertNotification> notifications = new ArrayList<>();

    // 1. Contract call alert'leri değerlendir
    if (transaction.getContractCall() != null) {
        List<AlertRule> rules = getActiveRulesByType(AlertRuleType.CONTRACT_CALL);  // CACHED!

        for (AlertRule rule : rules) {
            if (shouldTrigger(rule, transaction.getContractCall())) {
                notifications.addAll(createNotifications(rule, transaction, null));
            }
        }
    }

    // 2. Event alert'leri değerlendir
    for (TransactionEvent event : transaction.getEvents()) {
        List<AlertRule> rules = getActiveRulesByType(AlertRuleType.TOKEN_TRANSFER);  // CACHED!

        for (AlertRule rule : rules) {
            if (shouldTrigger(rule, event)) {
                notifications.addAll(createNotifications(rule, transaction, event));
            }
        }
    }

    // 3. Failed transaction alert'leri
    if (!transaction.getSuccess()) {
        List<AlertRule> rules = getActiveRulesByType(AlertRuleType.FAILED_TRANSACTION);  // CACHED!

        for (AlertRule rule : rules) {
            if (shouldTrigger(rule, transaction)) {
                notifications.addAll(createNotifications(rule, transaction, null));
            }
        }
    }

    return notifications;
}

/**
 * Cache-optimized rule loading.
 *
 * İLK ÇAĞRI:
 * - Database'den yükle
 * - Cache'e koy (TTL: 1 saat)
 *
 * SONRAK İ ÇAĞRILAR:
 * - Cache'den oku (O(1))
 * - Database'e gitme!
 */
@Cacheable(value = "alertRules", key = "#ruleType")
public List<AlertRule> getActiveRulesByType(AlertRuleType ruleType) {
    return alertRuleRepository.findActiveByRuleType(ruleType);
}

/**
 * Rule trigger olmalı mı kontrol et.
 */
private boolean shouldTrigger(AlertRule rule, Object context) {
    // 1. Cooldown kontrolü
    if (rule.isInCooldown()) {
        return false;
    }

    // 2. Matching logic (polymorphic!)
    return rule.matches(context);
}
```

**🎓 CAFFEINE CACHE**:

```java
// application.properties
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=1h

// Main Application
@EnableCaching
@SpringBootApplication
public class Application { }

// Service
@Cacheable(value = "alertRules", key = "#ruleType")
public List<AlertRule> getActiveRulesByType(AlertRuleType ruleType) {
    // Bu method çağrıldığında:
    // 1. Cache'de var mı bak (key = ruleType)
    // 2. Varsa cache'den dön (database'e gitme!)
    // 3. Yoksa database'den yükle ve cache'e koy
}

@CacheEvict(value = "alertRules", allEntries = true)
public void invalidateCache() {
    // Rule create/update/delete olduğunda cache'i temizle
}
```

##### 6.1.3 NotificationDispatcher.java ⭐ ASYNC

```java
/**
 * Batch notification gönderimi (async).
 */
public void dispatchBatch(List<AlertNotification> notifications) {
    for (AlertNotification notification : notifications) {
        dispatch(notification);  // Async method
    }
}

/**
 * Tek bir notification gönder (async).
 *
 * @Async → Ayrı thread'de çalışır, ana thread bloklamaz
 */
@Async
@Transactional
public void dispatch(AlertNotification notification) {
    notification.incrementAttemptCount();

    try {
        // Channel'a göre doğru service'i seç
        NotificationService service = getServiceForChannel(notification.getChannel());

        // Gönder
        service.send(notification);

        // Başarılı
        notification.markAsSent();
        log.info("Notification sent: {}", notification.getId());

    } catch (Exception e) {
        // Başarısız
        notification.markAsFailed(e.getMessage());
        log.error("Notification failed: {}", notification.getId(), e);

        // Retry?
        if (notification.shouldRetry()) {
            // 5 saniye bekle, tekrar dene
            try {
                Thread.sleep(5000);
                dispatch(notification);  // Recursive retry
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    } finally {
        alertNotificationRepository.save(notification);
    }
}
```

**🎓 @ASYNC**:

```java
// application.properties
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=20

// Main Application
@EnableAsync
@SpringBootApplication
public class Application { }

// Service method
@Async
public void myAsyncMethod() {
    // Bu method ayrı thread'de çalışır
    // Caller bloklanmaz, hemen return olur
}

// Kullanımı
@Autowired
private MyService myService;

myService.myAsyncMethod();  // Hemen return olur
System.out.println("I don't wait!");  // Async method bitmeden burası çalışır
```

#### 6.2 Diğer Servisler

```
AuthenticationService        → Login, Register, JWT token
EmailNotificationService     → SMTP ile email gönderimi
WebhookNotificationService   → HTTP POST ile webhook
AlertRuleService             → Alert CRUD
BlockQueryService            → Block sorguları (@Transactional(readOnly=true))
TransactionQueryService      → Transaction sorguları
MonitoringService            → Sistem istatistikleri
```

#### AŞAMA 6 ÖZET

**Oluşturduklarınız**:
- ✅ 11 application service

**Öğrendikleriniz**:
- @Service, @Transactional
- Constructor injection (@RequiredArgsConstructor)
- @Cacheable, @CacheEvict (Caffeine)
- @Async (async processing)
- @Slf4j (logging)
- readOnly transactions
- Business logic patterns

---

### 🎯 AŞAMA 7: API Layer (3 Gün)

**Hedef**: REST API controller'larını ve DTO'ları oluştur.

#### 7.1 Controller Pattern

```java
@RestController
@RequestMapping("/api/v1/resource")
@RequiredArgsConstructor
@Validated
public class MyController {

    private final MyService service;

    // GET /api/v1/resource
    @GetMapping
    public ResponseEntity<Page<MyDto>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MyEntity> entities = service.getAll(pageable);
        Page<MyDto> dtos = entities.map(this::toDto);
        return ResponseEntity.ok(dtos);
    }

    // GET /api/v1/resource/{id}
    @GetMapping("/{id}")
    public ResponseEntity<MyDto> getById(@PathVariable Long id) {
        return service.getById(id)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/v1/resource
    @PostMapping
    public ResponseEntity<MyDto> create(@Valid @RequestBody CreateDto request) {
        MyEntity entity = service.create(request);
        MyDto dto = toDto(entity);
        return ResponseEntity.status(201).body(dto);
    }

    // PUT /api/v1/resource/{id}
    @PutMapping("/{id}")
    public ResponseEntity<MyDto> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateDto request
    ) {
        MyEntity entity = service.update(id, request);
        return ResponseEntity.ok(toDto(entity));
    }

    // DELETE /api/v1/resource/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private MyDto toDto(MyEntity entity) {
        // Entity → DTO mapping
    }
}
```

#### 7.2 Önemli Controller'lar

##### 7.2.1 WebhookController.java ⭐ ASYNC

```java
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ProcessChainhookPayloadUseCase processPayloadUseCase;

    /**
     * Chainhook webhook endpoint.
     *
     * ÖNEMLİ:
     * - HMAC signature doğrulanmış (filter'da)
     * - Async processing (30 saniye timeout için)
     * - Hemen 200 OK döner, işleme arka planda
     */
    @PostMapping("/chainhook")
    public ResponseEntity<Map<String, Object>> handleChainhookWebhook(
        @RequestBody ChainhookPayloadDto payload
    ) {
        log.info("Received Chainhook webhook");

        // Async processing
        CompletableFuture.runAsync(() -> {
            try {
                processPayloadUseCase.processPayload(payload);
            } catch (Exception e) {
                log.error("Error processing webhook", e);
            }
        });

        // Hemen 200 OK döner
        return ResponseEntity.ok(Map.of(
            "status", "accepted",
            "message", "Webhook received and processing"
        ));
    }
}
```

##### 7.2.2 AlertRuleController.java ⭐ CRUD

```java
@RestController
@RequestMapping("/api/v1/alerts/rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    // POST /api/v1/alerts/rules - Create
    @PostMapping
    public ResponseEntity<AlertRuleResponse> create(
        @Valid @RequestBody CreateAlertRuleRequest request,
        Authentication authentication  // Spring Security'den gelen user
    ) {
        User user = (User) authentication.getPrincipal();
        AlertRule rule = alertRuleService.create(request, user);
        return ResponseEntity.status(201).body(toResponse(rule));
    }

    // GET /api/v1/alerts/rules - List (paginated)
    @GetMapping
    public ResponseEntity<Page<AlertRuleResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertRule> rules = alertRuleService.getByUser(user, pageable);
        return ResponseEntity.ok(rules.map(this::toResponse));
    }

    // GET /api/v1/alerts/rules/{id} - Get by ID
    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> getById(
        @PathVariable Long id,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return alertRuleService.getById(id, user)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/v1/alerts/rules/{id} - Update
    @PutMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateAlertRuleRequest request,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        AlertRule rule = alertRuleService.update(id, request, user);
        return ResponseEntity.ok(toResponse(rule));
    }

    // DELETE /api/v1/alerts/rules/{id} - Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        alertRuleService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
```

##### 7.2.3 GlobalExceptionHandler.java ⭐ ERROR HANDLING

```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Validation error (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
        MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse response = new ErrorResponse(
            "Validation failed",
            errors
        );

        return ResponseEntity.status(400).body(response);
    }

    /**
     * Resource not found (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
        ResourceNotFoundException ex
    ) {
        ErrorResponse response = new ErrorResponse(
            ex.getMessage(),
            null
        );

        return ResponseEntity.status(404).body(response);
    }

    /**
     * Access denied (403 Forbidden)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
        AccessDeniedException ex
    ) {
        ErrorResponse response = new ErrorResponse(
            "Access denied",
            null
        );

        return ResponseEntity.status(403).body(response);
    }

    /**
     * Generic error (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(
        Exception ex
    ) {
        log.error("Unhandled exception", ex);

        ErrorResponse response = new ErrorResponse(
            "Internal server error",
            null
        );

        return ResponseEntity.status(500).body(response);
    }
}
```

#### 7.3 Tüm Endpoint'ler (32 Total)

```
AUTH (2)
- POST /api/v1/auth/register
- POST /api/v1/auth/login

WEBHOOK (1)
- POST /api/v1/webhook/chainhook

ALERT RULES (5)
- POST   /api/v1/alerts/rules
- GET    /api/v1/alerts/rules
- GET    /api/v1/alerts/rules/{id}
- PUT    /api/v1/alerts/rules/{id}
- DELETE /api/v1/alerts/rules/{id}

ALERT NOTIFICATIONS (3)
- GET /api/v1/alerts/notifications
- GET /api/v1/alerts/notifications/{id}
- PUT /api/v1/alerts/notifications/{id}/mark-read

BLOCKS (6)
- GET /api/v1/blocks
- GET /api/v1/blocks/{id}
- GET /api/v1/blocks/hash/{blockHash}
- GET /api/v1/blocks/height/{height}
- GET /api/v1/blocks/range?start=...&end=...
- GET /api/v1/blocks/latest/height

TRANSACTIONS (6)
- GET /api/v1/transactions
- GET /api/v1/transactions/txid/{txId}
- GET /api/v1/transactions/sender/{sender}
- GET /api/v1/transactions/type/{type}
- GET /api/v1/transactions/successful
- GET /api/v1/transactions/failed

MONITORING (5)
- GET /api/v1/monitoring/stats
- GET /api/v1/monitoring/stats/blockchain
- GET /api/v1/monitoring/stats/alerts
- GET /api/v1/monitoring/stats/cache
- GET /api/v1/monitoring/health

USER (4)
- GET    /api/v1/users/me
- PUT    /api/v1/users/me
- DELETE /api/v1/users/me
- GET    /api/v1/users/me/notifications
```

#### AŞAMA 7 ÖZET

**Oluşturduklarınız**:
- ✅ 28 DTO class
- ✅ 8 controller
- ✅ GlobalExceptionHandler
- ✅ 32 REST endpoint

**Öğrendikleriniz**:
- @RestController, @RequestMapping
- @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
- @PathVariable, @RequestParam, @RequestBody
- @Valid validation
- ResponseEntity
- @ControllerAdvice exception handling
- Pagination (Pageable, Page<T>)
- Authentication object

---

## 🎉 MVP TAMAMLANDI!

Tebrikler! 7 aşamayı da tamamladın. Şimdi elinizde:

- ✅ 7 Value Object (Enum)
- ✅ 29 Domain Entity
- ✅ 9 Repository Interface
- ✅ 8 Infrastructure Component
- ✅ 11 Application Service
- ✅ 28 DTO Class
- ✅ 8 REST Controller
- ✅ 32 REST Endpoint

**Toplam ~12,000 satır kod!**

---

## 📚 Final Kontrol Listesi

### Tüm Dosyalar Hazır mı?

```bash
# Compile
mvn clean compile

# Test
mvn test

# Package
mvn package

# Run
mvn spring-boot:run
```

### application.properties Hazır mı?

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/stacks_monitoring
spring.datasource.username=stacks_user
spring.datasource.password=stacks_password

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# JWT
security.jwt.secret-key=your-256-bit-secret-key-here-minimum-32-characters
security.jwt.expiration-ms=86400000
security.jwt.issuer=stacks-monitor

# Chainhook
chainhook.hmac.secret=your-hmac-secret

# Email
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password

# Cache
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=1h

# Logging
logging.level.com.stacksmonitoring=DEBUG
```

---

## 🚀 Sonraki Adımlar

### 1. Testing
- Unit test'leri çalıştır
- Integration test'leri çalıştır
- Postman collection hazırla

### 2. Documentation
- API documentation (Swagger/OpenAPI)
- README.md güncelle
- Architecture diagram

### 3. Deployment
- Docker image oluştur
- Kubernetes manifest'leri
- CI/CD pipeline

### 4. Monitoring
- Prometheus metrics
- Grafana dashboard
- Alerting

---

## 💡 Bonus: Sık Kullanılan Komutlar

```bash
# Database reset
psql -U postgres -c "DROP DATABASE IF EXISTS stacks_monitoring"
psql -U postgres -c "CREATE DATABASE stacks_monitoring"

# Clean build
mvn clean install -DskipTests

# Run with profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Generate JAR
mvn package -DskipTests

# Run JAR
java -jar target/stacks-chain-monitor-1.0.0.jar

# Docker build
docker build -t stacks-monitor .

# Docker run
docker run -p 8080:8080 stacks-monitor
```

---

## 🎓 Tüm Öğrendiğin Kavramlar

### Java & Spring
- ✅ Java 17 features (switch expression, records)
- ✅ Spring Boot 3
- ✅ Spring Data JPA
- ✅ Spring Security
- ✅ Spring Cache (Caffeine)
- ✅ @Async processing

### JPA & Database
- ✅ Entity mapping
- ✅ Relationships (OneToOne, OneToMany, ManyToOne)
- ✅ Inheritance (JOINED, SINGLE_TABLE)
- ✅ Cascade operations
- ✅ Lazy/Eager loading
- ✅ Business key pattern
- ✅ Soft delete
- ✅ Optimistic locking
- ✅ JPQL queries
- ✅ PostgreSQL JSONB

### Architecture & Patterns
- ✅ Clean Architecture
- ✅ Repository Pattern
- ✅ Strategy Pattern
- ✅ Factory Pattern
- ✅ Dependency Injection
- ✅ DTO Pattern

### Security
- ✅ JWT authentication
- ✅ BCrypt password hashing
- ✅ HMAC signature validation
- ✅ Rate limiting
- ✅ Security filters
- ✅ CSRF protection

### Best Practices
- ✅ RESTful API design
- ✅ Error handling
- ✅ Logging
- ✅ Caching
- ✅ Pagination
- ✅ Validation
- ✅ Transaction management

---

## 🌟 Başarılı Oldun!

Bu projeyi tamamlayarak:
- **Enterprise-grade** Spring Boot uygulaması yazdın
- **Clean Architecture** uyguladın
- **Security best practices** öğrendin
- **Performance optimization** yaptın
- **Production-ready** kod ürettin

**Artık gerçek bir Software Engineer'sın! 🚀**

---

*Son güncelleme: Bu detaylı öğrenim rehberi Claude Code tarafından oluşturuldu.*

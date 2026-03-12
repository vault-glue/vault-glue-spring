# VaultGlue Design Document

> **Summary**: HashiCorp Vault 전체 엔진을 Spring Boot AutoConfiguration으로 제공하는 라이브러리 설계
>
> **Project**: vault-glue
> **Version**: 0.1.0
> **Author**: ctrdw
> **Date**: 2026-03-10
> **Status**: Draft
> **Planning Doc**: [vault-glue.plan.md](../../01-plan/features/vault-glue.plan.md)

---

## 1. Overview

### 1.1 Design Goals

- yml 프로퍼티만으로 Vault 엔진별 기능이 자동 구성되어야 함
- spring-cloud-vault와 충돌 없이 공존해야 함 (`@ConditionalOnMissingBean`)
- 엔진별 독립적 활성화/비활성화 가능
- DB credential rotation 시 무중단 (요청 유실 0건)
- 확장 가능한 이벤트/콜백 구조

### 1.2 Design Principles

- **Convention over Configuration**: 최소 설정으로 동작, 필요 시 세밀한 커스터마이징
- **Fail-Safe**: rotation 실패 시에도 기존 연결 유지, 설정 가능한 복구 전략
- **Non-Invasive**: 사용자 애플리케이션 코드에 침투하지 않음, 어노테이션은 opt-in
- **Modular**: 엔진별 AutoConfiguration 독립, 불필요한 엔진은 로드하지 않음

---

## 2. Architecture

### 2.1 Overall Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        User Application                             │
│   ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌──────────────────┐  │
│   │ @VaultValue│ │@VaultEncrypt│ │ DataSource │ │ VaultXxxOperations│ │
│   └─────┬────┘  └─────┬────┘  └─────┬─────┘  └────────┬─────────┘  │
├─────────┼──────────────┼────────────┼──────────────────┼────────────┤
│         │    vault-glue-autoconfigure  │                  │            │
│   ┌─────▼────┐  ┌─────▼────┐  ┌─────▼─────┐  ┌───────▼────────┐   │
│   │ KV Auto  │  │Transit   │  │ DB Auto   │  │ PKI/TOTP/AWS   │   │
│   │ Config   │  │Auto      │  │ Config    │  │ AutoConfig     │   │
│   │          │  │Config    │  │           │  │                │   │
│   └─────┬────┘  └─────┬────┘  └─────┬─────┘  └───────┬────────┘   │
│         │              │            │                  │            │
│   ┌─────▼──────────────▼────────────▼──────────────────▼────────┐   │
│   │                    Core Module                               │   │
│   │  ┌────────────┐ ┌──────────┐ ┌───────────┐ ┌─────────────┐  │   │
│   │  │Properties  │ │Event     │ │Failure    │ │Health       │  │   │
│   │  │Binding     │ │System    │ │Strategy   │ │Indicator    │  │   │
│   │  └────────────┘ └──────────┘ └───────────┘ └─────────────┘  │   │
│   └──────────────────────────┬───────────────────────────────────┘   │
├──────────────────────────────┼──────────────────────────────────────┤
│                    Spring Cloud Vault                                │
│              (VaultTemplate, Authentication)                        │
├──────────────────────────────┼──────────────────────────────────────┤
│                    HashiCorp Vault Server                            │
│         ┌────┐ ┌────┐ ┌───────┐ ┌───┐ ┌────┐ ┌───┐                │
│         │ KV │ │ DB │ │Transit│ │PKI│ │TOTP│ │AWS│                │
│         └────┘ └────┘ └───────┘ └───┘ └────┘ └───┘                │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 AutoConfiguration 등록 흐름

```
Spring Boot 시작
  │
  ├─ META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 로드
  │
  ├─ VaultGlueCoreAutoConfiguration (항상 로드)
  │   ├─ VaultGlueProperties 바인딩
  │   ├─ VaultGlueEventPublisher 등록
  │   ├─ FailureStrategyHandler 등록
  │   └─ VaultGlueHealthIndicator 등록 (actuator 의존성 있을 때)
  │
  ├─ VaultGlueKvAutoConfiguration (@ConditionalOnProperty vault-glue.kv.enabled)
  │   ├─ VaultKvOperations Bean 등록
  │   ├─ VaultKvWatcher 등록 (watch.enabled=true일 때)
  │   └─ @VaultValue BeanPostProcessor 등록
  │
  ├─ VaultGlueDatabaseAutoConfiguration (@ConditionalOnProperty vault-glue.database)
  │   ├─ sources Map 순회
  │   ├─ type=dynamic → DynamicRoleDataSourceFactory
  │   ├─ type=static → StaticRoleDataSourceFactory
  │   ├─ DelegatingDataSource 프록시 등록
  │   └─ primary 지정된 것에 @Primary
  │
  ├─ VaultGlueTransitAutoConfiguration (@ConditionalOnProperty vault-glue.transit.enabled)
  │   ├─ VaultTransitOperations Bean 등록
  │   ├─ Transit key 자동 생성 (auto-create=true)
  │   └─ VaultEncryptConverter JPA AttributeConverter 등록
  │
  ├─ VaultGluePkiAutoConfiguration (@ConditionalOnProperty vault-glue.pki.enabled)
  │   ├─ VaultPkiOperations Bean 등록
  │   ├─ CertificateRenewalScheduler 등록
  │   └─ SSL 자동 설정 (configure-ssl=true)
  │
  ├─ VaultGlueTotpAutoConfiguration (@ConditionalOnProperty vault-glue.totp.enabled)
  │   └─ VaultTotpOperations Bean 등록
  │
  └─ VaultGlueAwsAutoConfiguration (@ConditionalOnProperty vault-glue.aws.enabled)
      ├─ VaultAwsCredentialProvider Bean 등록
      └─ AwsCredentialsProvider 자동 연동
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| All AutoConfigurations | Core Module | Properties, Event, FailureStrategy |
| Core Module | Spring Cloud Vault (VaultTemplate) | Vault 통신 기반 |
| DB AutoConfiguration | HikariCP | Connection Pool 관리 |
| Transit AutoConfiguration | Spring Data JPA (optional) | `@VaultEncrypt` 지원 |
| PKI AutoConfiguration | Spring Boot SSL (optional) | SSL 자동 설정 |
| AWS AutoConfiguration | Spring Cloud AWS (optional) | AwsCredentialsProvider 연동 |
| Health Indicator | Spring Boot Actuator (optional) | Health 엔드포인트 |

---

## 3. Package Structure

### 3.1 전체 패키지 구조

```
io.vaultglue/
├── autoconfigure/
│   ├── VaultGlueCoreAutoConfiguration.java
│   ├── VaultGlueKvAutoConfiguration.java
│   ├── VaultGlueDatabaseAutoConfiguration.java
│   ├── VaultGlueTransitAutoConfiguration.java
│   ├── VaultGluePkiAutoConfiguration.java
│   ├── VaultGlueTotpAutoConfiguration.java
│   └── VaultGlueAwsAutoConfiguration.java
│
├── core/
│   ├── VaultGlueProperties.java                  # @ConfigurationProperties("vault-glue")
│   ├── VaultGlueEvent.java                       # 이벤트 베이스 클래스
│   ├── VaultGlueEventPublisher.java              # ApplicationEventPublisher 래퍼
│   ├── VaultGlueEventListener.java               # 사용자 구현 인터페이스
│   ├── FailureStrategy.java                     # enum: RESTART, RETRY, IGNORE
│   ├── FailureStrategyHandler.java              # 실패 전략 실행기
│   └── VaultGlueHealthIndicator.java             # Actuator Health
│
├── kv/
│   ├── VaultKvOperations.java                   # KV 연산 인터페이스
│   ├── DefaultVaultKvOperations.java            # 기본 구현
│   ├── VaultKvWatcher.java                      # 변경 감지 polling
│   ├── VaultValue.java                          # @VaultValue 어노테이션
│   ├── VaultValueBeanPostProcessor.java         # 어노테이션 처리기
│   └── properties/
│       └── VaultGlueKvProperties.java            # KV 설정
│
├── database/
│   ├── VaultGlueDataSourceRegistrar.java         # 멀티 DataSource BeanDefinition 등록
│   ├── VaultGlueDelegatingDataSource.java        # volatile delegate 패턴
│   ├── DataSourceRotator.java                   # rotation 오케스트레이터
│   ├── GracefulShutdown.java                    # old pool graceful shutdown
│   ├── dynamic/
│   │   ├── DynamicRoleDataSourceFactory.java    # Lease 기반 DataSource 생성
│   │   ├── DynamicLeaseListener.java            # SecretLeaseContainer 리스너
│   │   └── DynamicRoleProperties.java
│   ├── static_/
│   │   ├── StaticRoleDataSourceFactory.java     # Static credential 기반 DataSource 생성
│   │   ├── StaticCredentialProvider.java        # VaultTemplate으로 credential 조회
│   │   ├── StaticRefreshScheduler.java          # 주기적 credential 갱신
│   │   └── StaticRoleProperties.java
│   └── properties/
│       ├── VaultGlueDatabaseProperties.java      # DB 전체 설정
│       ├── DataSourceProperties.java            # 개별 DataSource 설정
│       └── HikariProperties.java                # HikariCP 커스텀 설정
│
├── transit/
│   ├── VaultTransitOperations.java              # Transit 연산 인터페이스
│   ├── DefaultVaultTransitOperations.java       # 기본 구현
│   ├── VaultEncrypt.java                        # @VaultEncrypt 어노테이션
│   ├── VaultEncryptConverter.java               # JPA AttributeConverter
│   ├── TransitKeyInitializer.java               # 키 자동 생성
│   └── properties/
│       └── VaultGlueTransitProperties.java       # Transit 설정
│
├── pki/
│   ├── VaultPkiOperations.java                  # PKI 연산 인터페이스
│   ├── DefaultVaultPkiOperations.java           # 기본 구현
│   ├── CertificateBundle.java                   # 인증서 + 키 묶음
│   ├── CertificateRenewalScheduler.java         # 자동 갱신
│   ├── SslAutoConfigurator.java                 # Spring Boot SSL 연동
│   └── properties/
│       └── VaultGluePkiProperties.java           # PKI 설정
│
├── totp/
│   ├── VaultTotpOperations.java                 # TOTP 연산 인터페이스
│   ├── DefaultVaultTotpOperations.java          # 기본 구현
│   ├── TotpKey.java                             # TOTP 키 정보 (URI 포함)
│   └── properties/
│       └── VaultGlueTotpProperties.java          # TOTP 설정
│
└── aws/
    ├── VaultAwsCredentialProvider.java           # AWS credential 제공
    ├── VaultAwsCredentialsAdapter.java           # Spring Cloud AWS 어댑터
    ├── AwsCredentialRenewalScheduler.java        # 자동 갱신
    └── properties/
        └── VaultGlueAwsProperties.java           # AWS 설정
```

---

## 4. Core Module 상세 설계

### 4.1 VaultGlueProperties

```java
@ConfigurationProperties(prefix = "vault-glue")
public class VaultGlueProperties {
    private FailureStrategy onFailure = FailureStrategy.RETRY;
    private RetryProperties retry = new RetryProperties();
    private ActuatorProperties actuator = new ActuatorProperties();

    public static class RetryProperties {
        private int maxAttempts = 3;
        private long delay = 5000;  // ms
    }

    public static class ActuatorProperties {
        private boolean enabled = true;
    }
}
```

### 4.2 Event System

```java
// 베이스 이벤트
public abstract class VaultGlueEvent extends ApplicationEvent {
    private final String engine;      // "database", "transit", "pki" 등
    private final String identifier;  // DataSource name, key name 등
    private final Instant timestamp;
}

// 엔진별 이벤트
public class CredentialRotatedEvent extends VaultGlueEvent {
    private final String oldUsername;
    private final String newUsername;
    private final Duration leaseDuration;
}

public class CredentialRotationFailedEvent extends VaultGlueEvent {
    private final Exception cause;
    private final int attemptCount;
}

public class LeaseRenewedEvent extends VaultGlueEvent { ... }
public class LeaseExpiredEvent extends VaultGlueEvent { ... }
public class CertificateRenewedEvent extends VaultGlueEvent { ... }

// 사용자 리스너 인터페이스
public interface VaultGlueEventListener {
    default void onCredentialRotated(CredentialRotatedEvent event) {}
    default void onCredentialRotationFailed(CredentialRotationFailedEvent event) {}
    default void onLeaseRenewed(LeaseRenewedEvent event) {}
    default void onLeaseExpired(LeaseExpiredEvent event) {}
    default void onCertificateRenewed(CertificateRenewedEvent event) {}
}
```

사용자는 Spring `@EventListener`를 쓰거나 `VaultGlueEventListener` 인터페이스를 구현:

```java
// 방법 1: Spring EventListener
@Component
public class SlackNotifier {
    @EventListener
    public void onRotationFailed(CredentialRotationFailedEvent event) {
        slack.send("Credential rotation failed: " + event.getIdentifier());
    }
}

// 방법 2: VaultGlueEventListener 인터페이스
@Component
public class SlackNotifier implements VaultGlueEventListener {
    @Override
    public void onCredentialRotationFailed(CredentialRotationFailedEvent event) {
        slack.send("Credential rotation failed: " + event.getIdentifier());
    }
}
```

### 4.3 Failure Strategy

```java
public enum FailureStrategy {
    RESTART,  // System.exit(1) → 컨테이너 재시작 의존
    RETRY,    // maxAttempts만큼 재시도 후 RESTART로 fallback
    IGNORE    // 로그만 남기고 무시 (기존 credential 유지)
}

public class FailureStrategyHandler {
    private final VaultGlueProperties properties;
    private final VaultGlueEventPublisher eventPublisher;

    public void handle(String engine, String identifier, Exception cause) {
        switch (properties.getOnFailure()) {
            case RETRY -> retryWithBackoff(engine, identifier, cause);
            case RESTART -> shutdownApplication(cause);
            case IGNORE -> logAndIgnore(engine, identifier, cause);
        }
    }

    private void retryWithBackoff(String engine, String id, Exception cause) {
        for (int i = 1; i <= properties.getRetry().getMaxAttempts(); i++) {
            try {
                Thread.sleep(properties.getRetry().getDelay() * i);
                // retry logic (엔진별 다름 - Strategy Pattern)
                return;
            } catch (Exception retryEx) {
                eventPublisher.publish(new CredentialRotationFailedEvent(..., i));
            }
        }
        // 모든 retry 실패 → RESTART로 fallback
        shutdownApplication(cause);
    }
}
```

### 4.4 Health Indicator

```java
@Component
@ConditionalOnClass(HealthIndicator.class)
public class VaultGlueHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // DB Engine 상태
        databaseSources.forEach((name, source) -> {
            builder.withDetail("database." + name, Map.of(
                "type", source.getType(),
                "role", source.getRole(),
                "lastRotation", source.getLastRotationTime(),
                "nextRotation", source.getNextRotationTime(),
                "poolActive", source.getActiveConnections(),
                "poolIdle", source.getIdleConnections(),
                "status", source.isHealthy() ? "UP" : "DOWN"
            ));
        });

        // Transit Engine 상태
        if (transitEnabled) {
            builder.withDetail("transit", Map.of(
                "backend", transitProperties.getBackend(),
                "keys", transitProperties.getKeys().keySet()
            ));
        }

        // PKI Engine 상태
        if (pkiEnabled) {
            builder.withDetail("pki", Map.of(
                "commonName", pkiProperties.getCommonName(),
                "expiresAt", currentCert.getExpiresAt(),
                "remainingHours", currentCert.getRemainingHours()
            ));
        }

        return builder.build();
    }
}
```

---

## 5. Database Engine 상세 설계

### 5.1 DataSource 생성/교체 흐름 (Static Role)

```
Application Start
  │
  ├─ VaultGlueDatabaseAutoConfiguration 로드
  │   └─ sources Map 순회
  │       └─ "primary" → type=static 감지
  │
  ├─ StaticRoleDataSourceFactory.create()
  │   ├─ StaticCredentialProvider.getCredential()
  │   │   └─ VaultTemplate.read("db/static-creds/{role}")
  │   │       → { username, password }
  │   ├─ HikariDataSource 생성 (credential 적용)
  │   └─ VaultGlueDelegatingDataSource로 래핑
  │
  ├─ Bean 등록: "primaryDataSource" (DelegatingDataSource)
  │
  └─ StaticRefreshScheduler 시작 (fixedRate = refreshInterval)
      │
      └─ 매 주기마다:
          ├─ StaticCredentialProvider.getCredential() (새 credential)
          ├─ 새 HikariDataSource 생성
          ├─ DelegatingDataSource.setDelegate(newDataSource)
          ├─ old DataSource → GracefulShutdown
          │   ├─ activeConnections == 0 대기 (최대 maxWaitSeconds)
          │   └─ HikariDataSource.close()
          └─ CredentialRotatedEvent 발행
```

### 5.2 DataSource 생성/교체 흐름 (Dynamic Role)

```
Application Start
  │
  ├─ VaultGlueDatabaseAutoConfiguration 로드
  │   └─ sources Map 순회
  │       └─ "secondary" → type=dynamic 감지
  │
  ├─ DynamicRoleDataSourceFactory.create()
  │   ├─ SecretLeaseContainer에 RequestedSecret 등록
  │   │   └─ "db/creds/{role}"
  │   ├─ 초기 credential 수신 → HikariDataSource 생성
  │   └─ VaultGlueDelegatingDataSource로 래핑
  │
  ├─ Bean 등록: "secondaryDataSource" (DelegatingDataSource)
  │
  └─ DynamicLeaseListener 등록
      │
      ├─ SecretLeaseCreatedEvent → 로깅 (초기 생성)
      ├─ AfterSecretLeaseRenewedEvent → 로깅 (갱신 성공)
      ├─ SecretLeaseExpiredEvent → DataSource 재생성
      │   ├─ ContextRefresher.refresh() 또는 직접 재생성
      │   ├─ DelegatingDataSource.setDelegate(newDataSource)
      │   └─ old DataSource → GracefulShutdown
      └─ LeaseErrorEvent → FailureStrategyHandler.handle()
```

### 5.3 멀티 DataSource 등록

```java
public class VaultGlueDataSourceRegistrar implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        VaultGlueDatabaseProperties dbProps = // properties 바인딩

        dbProps.getSources().forEach((name, sourceProps) -> {
            if (!sourceProps.isEnabled()) return;

            // BeanDefinition 동적 등록
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(DataSource.class)
                .setFactoryMethodOnBean("createDataSource", "vault-glueDataSourceFactory")
                .addConstructorArgValue(name)
                .addConstructorArgValue(sourceProps);

            if ("primary".equals(name) || sourceProps.isPrimary()) {
                builder.setPrimary(true);
            }

            registry.registerBeanDefinition(
                name + "DataSource",
                builder.getBeanDefinition()
            );
        });
    }
}
```

사용자 코드:

```java
@Repository
public class UserRepository {
    // primary는 @Qualifier 불필요
    private final JdbcTemplate jdbcTemplate;

    // secondary 사용 시
    @Autowired
    @Qualifier("secondaryDataSource")
    private DataSource secondaryDataSource;
}
```

### 5.4 VaultGlueDelegatingDataSource

```java
public class VaultGlueDelegatingDataSource implements DataSource {
    private volatile DataSource delegate;
    private final String name;
    private Instant lastRotationTime;

    public synchronized void rotate(DataSource newDelegate) {
        DataSource old = this.delegate;
        this.delegate = newDelegate;
        this.lastRotationTime = Instant.now();

        if (old instanceof HikariDataSource hikari) {
            GracefulShutdown.execute(hikari);
        }
    }

    // DataSource 인터페이스 위임 (getConnection 등)
    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    // ... 나머지 DataSource 메서드 위임
}
```

### 5.5 GracefulShutdown

```java
public class GracefulShutdown {

    private static final int DEFAULT_MAX_WAIT_SECONDS = 300;

    public static void execute(HikariDataSource dataSource) {
        Thread.ofVirtual().name("vault-glue-cleanup-" + dataSource.getPoolName())
            .start(() -> {
                try {
                    String poolName = dataSource.getPoolName();
                    log.info("[VaultGlue] Graceful shutdown: {}", poolName);

                    int waited = 0;
                    while (waited < DEFAULT_MAX_WAIT_SECONDS) {
                        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
                        if (pool == null || pool.getActiveConnections() == 0) {
                            break;
                        }
                        Thread.sleep(1000);
                        waited++;
                    }

                    dataSource.close();
                    log.info("[VaultGlue] Pool closed: {}", poolName);
                } catch (Exception e) {
                    log.error("[VaultGlue] Graceful shutdown failed", e);
                }
            });
    }
}
```

---

## 6. KV Engine 상세 설계

### 6.1 VaultKvOperations

```java
public interface VaultKvOperations {

    // 읽기
    Map<String, Object> get(String path);
    <T> T get(String path, Class<T> type);
    Map<String, Object> get(String path, int version);  // v2 only

    // 쓰기
    void put(String path, Map<String, Object> data);
    void put(String path, Object data);

    // 삭제
    void delete(String path);
    void delete(String path, int... versions);  // v2 soft delete
    void undelete(String path, int... versions); // v2 undelete
    void destroy(String path, int... versions);  // v2 permanent delete

    // 메타데이터 (v2 only)
    VaultKvMetadata metadata(String path);
    List<String> list(String path);
}
```

### 6.2 @VaultValue

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VaultValue {
    String path();              // KV 경로 (예: "app/my-service")
    String key();               // secret 내 키 (예: "api-key")
    String defaultValue() default "";
    boolean refresh() default false;  // watch 시 자동 갱신 여부
}

// 사용 예
@Component
public class ExternalApiClient {
    @VaultValue(path = "app/my-service", key = "external-api-key")
    private String apiKey;

    @VaultValue(path = "app/my-service", key = "api-secret", refresh = true)
    private String apiSecret;  // watch 모드에서 변경 시 자동 갱신
}
```

### 6.3 Watch 모드

```java
public class VaultKvWatcher {
    private final ScheduledExecutorService scheduler;
    private final Map<String, Map<String, Object>> lastKnownValues = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        if (!kvProperties.getWatch().isEnabled()) return;

        scheduler.scheduleAtFixedRate(
            this::pollChanges,
            0,
            kvProperties.getWatch().getInterval().toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    private void pollChanges() {
        // 등록된 path들을 polling
        // 변경 감지 시 @VaultValue(refresh=true) 필드 업데이트
        // KvSecretChangedEvent 발행
    }
}
```

---

## 7. Transit Engine 상세 설계

### 7.1 VaultTransitOperations

```java
public interface VaultTransitOperations {

    // 암복호화
    String encrypt(String keyName, String plaintext);
    String encrypt(String keyName, String plaintext, String context);  // convergent
    String decrypt(String keyName, String ciphertext);
    String decrypt(String keyName, String ciphertext, String context);

    // Batch 암복호화
    List<String> encryptBatch(String keyName, List<String> plaintexts);
    List<String> decryptBatch(String keyName, List<String> ciphertexts);

    // 키 로테이션 후 재암호화
    String rewrap(String keyName, String ciphertext);
    List<String> rewrapBatch(String keyName, List<String> ciphertexts);

    // HMAC
    String hmac(String keyName, String data);
    String hmac(String keyName, String data, String algorithm);
    boolean verifyHmac(String keyName, String data, String hmac);

    // 서명
    String sign(String keyName, String data);
    String sign(String keyName, String data, String algorithm);
    boolean verify(String keyName, String data, String signature);

    // 키 관리
    void createKey(String keyName, TransitKeyType type);
    void rotateKey(String keyName);
    TransitKeyInfo getKeyInfo(String keyName);
}
```

### 7.2 @VaultEncrypt JPA AttributeConverter

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VaultEncrypt {
    String key();                        // Transit key 이름
    String context() default "";         // convergent encryption context
}

// JPA AttributeConverter 자동 등록
public class VaultEncryptConverter implements AttributeConverter<String, String> {
    // 주의: JPA Converter는 Spring Bean injection이 제한적
    // → ApplicationContext에서 직접 조회하는 패턴 사용

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return getTransitOperations().encrypt(keyName, attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (!dbData.startsWith("vault:v")) return dbData;  // 미암호화 데이터 호환
        return getTransitOperations().decrypt(keyName, dbData);
    }
}
```

사용 예:

```java
@Entity
public class User {
    @Id
    private Long id;

    private String name;  // 평문 저장

    @Convert(converter = VaultEncryptConverter.class)
    @VaultEncrypt(key = "user-pii")
    private String residentNumber;  // 암호화 저장

    @Convert(converter = VaultEncryptConverter.class)
    @VaultEncrypt(key = "user-pii", context = "phone")
    private String phoneNumber;  // convergent encryption
}
```

### 7.3 Transit Key 자동 생성

```java
public class TransitKeyInitializer implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        transitProperties.getKeys().forEach((name, keyProps) -> {
            if (!keyProps.isAutoCreate()) return;

            try {
                transitOperations.getKeyInfo(name);
                log.info("[VaultGlue] Transit key exists: {}", name);
            } catch (VaultException e) {
                transitOperations.createKey(name, keyProps.getType());
                log.info("[VaultGlue] Transit key created: {} ({})", name, keyProps.getType());
            }
        });
    }
}
```

---

## 8. PKI Engine 상세 설계

### 8.1 VaultPkiOperations

```java
public interface VaultPkiOperations {

    CertificateBundle issue(String role, String commonName);
    CertificateBundle issue(String role, String commonName, Duration ttl);
    CertificateBundle issue(PkiIssueRequest request);

    void revoke(String serialNumber);

    CertificateBundle getCurrent();  // 현재 활성 인증서
}

public record CertificateBundle(
    String certificate,        // PEM
    String privateKey,         // PEM
    String issuingCa,          // PEM
    String serialNumber,
    Instant expiresAt
) {
    public long getRemainingHours() {
        return Duration.between(Instant.now(), expiresAt).toHours();
    }
}
```

### 8.2 자동 갱신

```java
public class CertificateRenewalScheduler {

    // TTL의 2/3 지점에서 갱신 시작
    @Scheduled(fixedDelayString = "${vault-glue.pki.check-interval:3600000}")
    public void checkAndRenew() {
        CertificateBundle current = pkiOperations.getCurrent();
        if (current == null || current.getRemainingHours() < renewThresholdHours) {
            CertificateBundle renewed = pkiOperations.issue(
                pkiProperties.getRole(),
                pkiProperties.getCommonName(),
                Duration.parse(pkiProperties.getTtl())
            );

            if (pkiProperties.isConfigureSsl()) {
                sslAutoConfigurator.apply(renewed);
            }

            eventPublisher.publish(new CertificateRenewedEvent(renewed));
        }
    }
}
```

---

## 9. TOTP / AWS Engine 상세 설계

### 9.1 VaultTotpOperations

```java
public interface VaultTotpOperations {

    TotpKey createKey(String name, String issuer, String accountName);
    boolean validate(String name, String code);
    void deleteKey(String name);
}

public record TotpKey(
    String name,
    String barcode,     // base64 encoded QR code
    String url          // otpauth:// URL
) {}
```

### 9.2 VaultAwsCredentialProvider

```java
public class VaultAwsCredentialProvider {

    private volatile AwsCredential currentCredential;
    private final ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        rotate();
        // TTL의 80% 지점에서 갱신
        long renewalMs = (long)(ttlMs * 0.8);
        scheduler.scheduleAtFixedRate(this::rotate, renewalMs, renewalMs, TimeUnit.MILLISECONDS);
    }

    public AwsCredential getCredential() {
        return currentCredential;
    }

    private void rotate() {
        VaultResponse response = vaultTemplate.read(
            awsProperties.getBackend() + "/creds/" + awsProperties.getRole()
        );
        currentCredential = new AwsCredential(
            (String) response.getData().get("access_key"),
            (String) response.getData().get("secret_key"),
            (String) response.getData().get("security_token")
        );
    }

    public record AwsCredential(String accessKey, String secretKey, String securityToken) {}
}

// Spring Cloud AWS 자동 연동
@ConditionalOnClass(AwsCredentialsProvider.class)
public class VaultAwsCredentialsAdapter implements AwsCredentialsProvider {
    private final VaultAwsCredentialProvider provider;

    @Override
    public AwsCredentials resolveCredentials() {
        var cred = provider.getCredential();
        if (cred.securityToken() != null) {
            return AwsSessionCredentials.create(cred.accessKey(), cred.secretKey(), cred.securityToken());
        }
        return AwsBasicCredentials.create(cred.accessKey(), cred.secretKey());
    }
}
```

---

## 10. Properties 바인딩 전체

```java
@ConfigurationProperties(prefix = "vault-glue")
public class VaultGlueProperties {
    private FailureStrategy onFailure = FailureStrategy.RETRY;
    private RetryProperties retry = new RetryProperties();
    private ActuatorProperties actuator = new ActuatorProperties();

    // 엔진별 Properties는 각 AutoConfiguration에서 별도 바인딩
}

@ConfigurationProperties(prefix = "vault-glue.kv")
public class VaultGlueKvProperties {
    private boolean enabled = false;
    private String backend = "secret";
    private int version = 2;
    private String applicationName;
    private WatchProperties watch = new WatchProperties();

    public static class WatchProperties {
        private boolean enabled = false;
        private Duration interval = Duration.ofSeconds(30);
    }
}

@ConfigurationProperties(prefix = "vault-glue.database")
public class VaultGlueDatabaseProperties {
    private Map<String, DataSourceProperties> sources = new LinkedHashMap<>();
}

public class DataSourceProperties {
    private boolean enabled = true;
    private boolean primary = false;
    private String type;               // "static" | "dynamic"
    private String role;
    private String backend = "db";
    private String jdbcUrl;
    private String driverClassName;
    private long refreshInterval = 18_000_000;  // 5시간 (static only)
    private HikariProperties hikari = new HikariProperties();
}

public class HikariProperties {
    private int maximumPoolSize = 10;
    private int minimumIdle = 2;
    private long maxLifetime = 1_800_000;      // 30분
    private long idleTimeout = 600_000;         // 10분
    private long connectionTimeout = 30_000;    // 30초
    private long validationTimeout = 5_000;     // 5초
    private long leakDetectionThreshold = 0;    // disabled
}

@ConfigurationProperties(prefix = "vault-glue.transit")
public class VaultGlueTransitProperties {
    private boolean enabled = false;
    private String backend = "transit";
    private Map<String, TransitKeyProperties> keys = new LinkedHashMap<>();

    public static class TransitKeyProperties {
        private String type = "aes256-gcm96";
        private boolean autoCreate = false;
    }
}

@ConfigurationProperties(prefix = "vault-glue.pki")
public class VaultGluePkiProperties {
    private boolean enabled = false;
    private String backend = "pki";
    private String role;
    private String commonName;
    private String ttl = "72h";
    private boolean autoRenew = true;
    private boolean configureSsl = false;
    private long checkInterval = 3_600_000;  // 1시간
}

@ConfigurationProperties(prefix = "vault-glue.totp")
public class VaultGlueTotpProperties {
    private boolean enabled = false;
    private String backend = "totp";
}

@ConfigurationProperties(prefix = "vault-glue.aws")
public class VaultGlueAwsProperties {
    private boolean enabled = false;
    private String backend = "aws";
    private String role;
    private String credentialType = "sts";  // sts | iam_user
    private String ttl = "1h";
}
```

---

## 11. AutoConfiguration Conditions

```java
// Core - 항상 로드
@AutoConfiguration
@EnableConfigurationProperties(VaultGlueProperties.class)
@ConditionalOnClass(VaultTemplate.class)
public class VaultGlueCoreAutoConfiguration { ... }

// KV
@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@EnableConfigurationProperties(VaultGlueKvProperties.class)
@ConditionalOnProperty(prefix = "vault-glue.kv", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
public class VaultGlueKvAutoConfiguration { ... }

// Database
@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@EnableConfigurationProperties(VaultGlueDatabaseProperties.class)
@ConditionalOnProperty(prefix = "vault-glue.database", name = "sources")
@ConditionalOnBean(VaultTemplate.class)
@ConditionalOnClass(HikariDataSource.class)
public class VaultGlueDatabaseAutoConfiguration { ... }

// Transit
@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@EnableConfigurationProperties(VaultGlueTransitProperties.class)
@ConditionalOnProperty(prefix = "vault-glue.transit", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
public class VaultGlueTransitAutoConfiguration { ... }

// PKI
@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@EnableConfigurationProperties(VaultGluePkiProperties.class)
@ConditionalOnProperty(prefix = "vault-glue.pki", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
public class VaultGluePkiAutoConfiguration { ... }

// TOTP
@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@EnableConfigurationProperties(VaultGlueTotpProperties.class)
@ConditionalOnProperty(prefix = "vault-glue.totp", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
public class VaultGlueTotpAutoConfiguration { ... }

// AWS
@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@EnableConfigurationProperties(VaultGlueAwsProperties.class)
@ConditionalOnProperty(prefix = "vault-glue.aws", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
public class VaultGlueAwsAutoConfiguration { ... }
```

---

## 12. Test Plan

### 12.1 Test 구조

| Type | Target | Tool | 위치 |
|------|--------|------|------|
| Unit Test | Properties 바인딩, Converter, Operations | JUnit5 + Mockito | `vault-glue-autoconfigure/src/test/` |
| Integration Test | AutoConfiguration 로드, Bean 등록 | Spring Boot Test + ApplicationContextRunner | `vault-glue-autoconfigure/src/test/` |
| E2E Test | 실제 Vault 서버 연동 (전 엔진) | TestContainers + Vault | `vault-glue-test/` |

### 12.2 주요 테스트 케이스

**DB Engine:**
- [ ] Static Role: credential rotation 시 기존 connection 유실 없음
- [ ] Static Role: Vault 장애 시 retry → fallback 동작
- [ ] Dynamic Role: Lease 만료 시 DataSource 자동 재생성
- [ ] Dynamic Role: Lease 갱신 실패 시 FailureStrategy 동작
- [ ] 멀티 DataSource: primary/secondary Bean 정상 등록
- [ ] 멀티 DataSource: @Qualifier 주입 동작
- [ ] enabled=false 시 Bean 미등록

**KV Engine:**
- [ ] get/put/delete/list 정상 동작 (v1, v2)
- [ ] @VaultValue 필드 주입 동작
- [ ] Watch 모드: 변경 감지 + refresh=true 필드 자동 갱신

**Transit Engine:**
- [ ] encrypt/decrypt 정상 동작
- [ ] @VaultEncrypt JPA 필드 저장 시 암호화, 조회 시 복호화
- [ ] rewrap 동작
- [ ] auto-create: 키 없으면 자동 생성

**PKI Engine:**
- [ ] 인증서 발급 정상 동작
- [ ] 자동 갱신 스케줄러 동작
- [ ] configure-ssl=true 시 SSL 설정 적용

**공통:**
- [ ] spring-cloud-vault Bean과 충돌 없음
- [ ] Actuator health 엔드포인트 정상 동작
- [ ] 이벤트 발행/수신 동작
- [ ] FailureStrategy 각 전략별 동작

---

## 13. Gradle 모듈 설계

### 13.1 settings.gradle.kts

```kotlin
rootProject.name = "vault-glue"

include(
    "vault-glue-autoconfigure",
    "vault-glue-spring-boot-starter",
    "vault-glue-test"
)
```

### 13.2 vault-glue-autoconfigure/build.gradle.kts

```kotlin
dependencies {
    // 필수
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.cloud:spring-cloud-vault-config")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Optional (엔진별)
    compileOnly("com.zaxxer:HikariCP")
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("software.amazon.awssdk:auth")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:vault")
}
```

### 13.3 vault-glue-spring-boot-starter/build.gradle.kts

```kotlin
// Starter는 의존성만 모아주는 역할
dependencies {
    api(project(":vault-glue-autoconfigure"))
    api("org.springframework.cloud:spring-cloud-vault-config")
}
```

---

## 14. Implementation Order

### Phase 1: Core + DB Engine (P0)

1. [ ] Gradle 멀티모듈 프로젝트 세팅
2. [ ] `VaultGlueProperties`, `VaultGlueCoreAutoConfiguration`
3. [ ] Event System (`VaultGlueEvent`, `VaultGlueEventPublisher`)
4. [ ] `FailureStrategy`, `FailureStrategyHandler`
5. [ ] `VaultGlueDelegatingDataSource`, `GracefulShutdown`
6. [ ] `StaticRoleDataSourceFactory`, `StaticCredentialProvider`, `StaticRefreshScheduler`
7. [ ] `DynamicRoleDataSourceFactory`, `DynamicLeaseListener`
8. [ ] `VaultGlueDataSourceRegistrar` (멀티 DataSource)
9. [ ] `VaultGlueDatabaseAutoConfiguration`
10. [ ] DB Engine 통합 테스트

### Phase 2: KV + Transit Engine (P0)

11. [ ] `VaultKvOperations`, `DefaultVaultKvOperations`
12. [ ] `VaultGlueKvAutoConfiguration`
13. [ ] `VaultTransitOperations`, `DefaultVaultTransitOperations`
14. [ ] `VaultGlueTransitAutoConfiguration`
15. [ ] KV/Transit 통합 테스트

### Phase 3: Annotations + Actuator (P1)

16. [ ] `@VaultValue`, `VaultValueBeanPostProcessor`
17. [ ] `@VaultEncrypt`, `VaultEncryptConverter`
18. [ ] `VaultGlueHealthIndicator`
19. [ ] `VaultKvWatcher`
20. [ ] Annotation/Actuator 테스트

### Phase 4: PKI + TOTP + AWS (P1~P2)

21. [ ] `VaultPkiOperations`, `CertificateRenewalScheduler`
22. [ ] `VaultTotpOperations`
23. [ ] `VaultAwsCredentialProvider`, `VaultAwsCredentialsAdapter`
24. [ ] PKI/TOTP/AWS 통합 테스트

### Phase 5: Packaging + Release

25. [ ] `META-INF/spring/AutoConfiguration.imports`
26. [ ] Starter 모듈 정리
27. [ ] README, Javadoc
28. [ ] Maven Central 배포 설정
29. [ ] GitHub Actions CI/CD

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-03-10 | Initial draft | ctrdw |

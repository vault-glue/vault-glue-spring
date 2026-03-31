# VaultGlue Test Infrastructure Design

## Overview

Test infrastructure for vault-glue-autoconfigure covering KV, Database, and Transit engines.
Tests are written first in autoconfigure's `src/test/`, then common utilities are extracted
to `vault-glue-test/src/main/` for consumer reuse.

## Scope

- **Engines**: KV v2, Database (Static + Dynamic), Transit
- **DB targets**: MySQL 8.0, PostgreSQL 16
- **Test types**: Unit, Integration (ApplicationContextRunner), E2E (TestContainers)
- **Total test cases**: 42

## Test Hierarchy

```
Unit Tests        — Mockito, no Spring context, fast
Integration Tests — ApplicationContextRunner, AutoConfig loading verification
E2E Tests         — TestContainers (Vault + DB), real engine operations
```

## Container Architecture

### Vault Container
- Image: `hashicorp/vault:1.17`
- Mode: Dev (auto-unseal, root token: `test-root-token`)
- Engines enabled:
  - KV v2 at `app`
  - Transit at `transit`
  - Database at `db`

### DB Containers (conditional — only for Database engine tests)
- MySQL: `mysql:8.0`
- PostgreSQL: `postgres:16`
- Each with test DB + user created

### Container Lifecycle
- Singleton pattern: `static final` containers shared across test classes
- `VaultContainerSupport` holds shared Vault container
- `MySqlContainerSupport` / `PostgresContainerSupport` hold shared DB containers
- Containers start once per JVM, reused across all tests

### Vault Initialization Order
1. Start Vault container (dev mode)
2. `vault secrets enable -path=app kv-v2`
3. `vault secrets enable transit`
4. `vault secrets enable database`
5. Configure DB connections (MySQL/PostgreSQL plugin)
6. Create static role + dynamic role
7. Create transit keys (`aes256-gcm96` for encrypt, `ed25519` for signing)
8. Write test secrets to KV

## Test Cases

### Core — Unit Tests (5 cases)

| Test | Verification |
|---|---|
| FailureStrategy RETRY | Async retry via CompletableFuture, use Awaitility to wait for completion |
| FailureStrategy RESTART | Verify `ConfigurableApplicationContext.close()` is called |
| FailureStrategy IGNORE | Exception ignored, log only |
| RETRY exhaustion → RESTART fallback | After maxAttempts, falls back to RESTART |
| Event publishing | CredentialRotatedEvent etc. delivered to listener |

### KV Engine — Unit Tests (3 cases)

| Test | Verification |
|---|---|
| DefaultVaultKvOperations v2 get/put | Mock VaultTemplate, verify correct API path |
| Get non-existent path | Returns empty map (not null) |
| v2 metadata operations on v1 backend | Throws UnsupportedOperationException |

### KV Engine — E2E Tests (6 cases)

| Test | Verification |
|---|---|
| KV v2 put/get | Write and read secret |
| KV v2 delete | Null after delete |
| KV v2 list | List keys under path |
| KV v2 versioning | Read by version number |
| @VaultValue injection | BeanPostProcessor field injection |
| Watch change detection | Short interval (1s) + Awaitility to detect callback |

### Database Engine — Unit Tests (3 cases)

| Test | Verification |
|---|---|
| VaultGlueDelegatingDataSource delegate swap | Thread-safe volatile delegate replacement, getConnection() routes to current |
| DataSourceRotator rotation flow | Mock VaultTemplate, verify rotation ordering + event publishing |
| GracefulShutdown wait loop | Verify active-connection wait and timeout behavior |

### Database Engine — E2E Tests (8 cases)

| Test | Verification |
|---|---|
| Static role — MySQL credential | Get credential from Vault, create DataSource |
| Static role — PostgreSQL credential | Same as above, PostgreSQL |
| Static role — credential rotation | New credential after refresh-interval, existing connections maintained |
| Static role — graceful shutdown | Wait for active connections before pool close |
| Dynamic role — MySQL credential | Lease-based credential |
| Dynamic role — PostgreSQL credential | Same as above |
| Dynamic role — lease expiry rotation | Lease expiry event triggers new DataSource |
| Multi-DataSource | Primary + replica registered simultaneously |

### Transit Engine — Unit Tests (3 cases)

| Test | Verification |
|---|---|
| DefaultVaultTransitOperations encrypt/decrypt | Mock VaultTemplate, verify Base64 encoding |
| VaultEncryptConverter round-trip | Verify `vg:{keyName}:vault:v1:...` prefix format, static init path |
| Batch encrypt/decrypt | Verify batch API paths and error handling |

### Transit Engine — E2E Tests (6 cases)

| Test | Verification |
|---|---|
| encrypt/decrypt | Plaintext → ciphertext → decrypt matches |
| rewrap | Rewrap after key version rotation |
| HMAC | Generate + verify HMAC |
| sign/verify | ed25519 key, `sign(keyName, data, hashAlgorithm, signatureAlgorithm)` 4-param overload |
| @VaultEncrypt JPA converter | Entity save produces `vg:{key}:vault:v1:...` ciphertext, load decrypts |
| Auto key creation | TransitKeyInitializer creates keys on startup |

### Integration Tests (4 cases)

| Test | Verification |
|---|---|
| KV AutoConfig loading | `kv.enabled=true` + VaultTemplate bean → KV beans registered |
| DB AutoConfig loading | HikariCP on classpath + VaultTemplate bean → DB beans registered |
| Transit AutoConfig loading | `transit.enabled=true` + VaultTemplate bean → Transit beans registered |
| DB disabled — no HikariCP | Remove HikariCP from classpath → no DB beans |

### VaultGlueDataSources — Unit Test (1 case)

| Test | Verification |
|---|---|
| get() unknown name | Throws IllegalArgumentException |

## Notes on Async / Timing

- **FailureStrategyHandler**: `handle()` takes `Supplier<Void>` 4th param, retries via `CompletableFuture.runAsync()`. Tests use Awaitility for async assertions.
- **VaultKvWatcher**: Uses `ScheduledExecutorService`. Tests set short interval (1s) and use Awaitility to wait for callback.
- **VaultEncryptConverter**: Uses static `defaultKeyName` set via `VaultEncryptConverter.initialize(context, keyName)`, not per-field annotation. Tests verify the static initialization path.

## Source Structure

```
vault-glue-autoconfigure/src/test/java/io/vaultglue/
├── support/                              ← Extract to vault-glue-test later
│   ├── VaultContainerSupport.java        ← Vault container (singleton) + engine init
│   ├── MySqlContainerSupport.java        ← MySQL container (singleton) + Vault DB connection
│   ├── PostgresContainerSupport.java     ← PostgreSQL container (singleton) + Vault DB connection
│   └── VaultInitializer.java             ← Vault HTTP API engine/role/key setup
│
├── core/
│   ├── FailureStrategyHandlerTest.java          ← Unit (4 cases)
│   └── VaultGlueEventPublisherTest.java         ← Unit (1 case)
│
├── kv/
│   ├── DefaultVaultKvOperationsTest.java        ← Unit (3 cases)
│   ├── VaultKvOperationsE2ETest.java            ← E2E (6 cases)
│   └── VaultGlueKvAutoConfigurationTest.java    ← Integration
│
├── database/
│   ├── VaultGlueDelegatingDataSourceTest.java   ← Unit
│   ├── DataSourceRotatorTest.java               ← Unit
│   ├── GracefulShutdownTest.java                ← Unit
│   ├── VaultGlueDataSourcesTest.java            ← Unit
│   ├── StaticRoleMySqlE2ETest.java              ← E2E
│   ├── StaticRolePostgresE2ETest.java           ← E2E
│   ├── DynamicRoleMySqlE2ETest.java             ← E2E
│   ├── DynamicRolePostgresE2ETest.java          ← E2E
│   ├── MultiDataSourceE2ETest.java              ← E2E
│   └── VaultGlueDatabaseAutoConfigurationTest.java ← Integration
│
├── transit/
│   ├── DefaultVaultTransitOperationsTest.java   ← Unit (3 cases)
│   ├── VaultTransitOperationsE2ETest.java       ← E2E (6 cases)
│   └── VaultGlueTransitAutoConfigurationTest.java ← Integration
│
└── autoconfigure/
    └── DisabledAutoConfigurationTest.java       ← Integration
```

## Dependencies (autoconfigure test scope)

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.springframework.vault:spring-vault-core'
testImplementation 'org.testcontainers:vault'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:mysql'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'com.mysql:mysql-connector-j'
testImplementation 'org.postgresql:postgresql'
testImplementation 'com.zaxxer:HikariCP'
testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa'
testImplementation 'org.awaitility:awaitility'
```

TestContainers versions managed by Spring Boot BOM. Awaitility added for async test assertions.

## Extraction Plan (Phase 2)

After autoconfigure tests are stable, extract `support/` package to `vault-glue-test/src/main/`:

```
vault-glue-test/src/main/java/io/vaultglue/test/
├── VaultGlueTestSupport.java         ← @VaultGlueTest annotation
├── VaultContainerSupport.java        ← Vault container lifecycle
├── MySqlContainerSupport.java        ← MySQL container + Vault DB setup
├── PostgresContainerSupport.java     ← PostgreSQL container + Vault DB setup
├── VaultInitializer.java             ← Engine/role/key initialization
└── TestVaultConfigurer.java          ← Pre-seed secrets for tests
```

Consumer usage after extraction:
```java
@VaultGlueTest
@SpringBootTest
class MyServiceTest {
    @Autowired
    private VaultKvOperations kvOps;

    @Test
    void shouldReadSecret() {
        kvOps.put("test-key", Map.of("password", "1234"));
        Map<String, Object> result = kvOps.get("test-key");
        assertThat(result.get("password")).isEqualTo("1234");
    }
}
```

## Implementation Order

1. `support/` — Container support classes (singleton containers, VaultInitializer)
2. `core/` — Unit tests (FailureStrategy 4 cases, EventPublisher)
3. `kv/` — Unit (3) + E2E (6) + Integration
4. `transit/` — Unit (3) + E2E (6) + Integration
5. `database/` — Unit (4) + E2E (8) + Integration (depends on all containers)
6. `autoconfigure/` — Disabled config test
7. Extract `support/` → `vault-glue-test/src/main/`

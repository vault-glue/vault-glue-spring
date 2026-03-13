# VaultGlue

A Spring Boot AutoConfiguration library for all HashiCorp Vault secret engines.
Provides DB/Transit/PKI/TOTP/AWS engines via simple YAML configuration — filling the gaps that `spring-cloud-vault` doesn't cover.

## Project Structure

```
vault-glue-spring/
├── vault-glue-autoconfigure/          # Core autoconfiguration module
│   └── src/main/java/io/vaultglue/
│       ├── autoconfigure/           # 8 AutoConfiguration classes
│       ├── core/                    # Properties, Event System, FailureStrategy, Health
│       ├── database/                # DB Engine (Static/Dynamic Role, multi-DataSource)
│       ├── kv/                      # KV Engine (CRUD, @VaultValue, Watch)
│       ├── transit/                 # Transit Engine (encrypt/decrypt, @VaultEncrypt, key mgmt)
│       ├── pki/                     # PKI Engine (certificate issue/renewal)
│       ├── totp/                    # TOTP Engine (OTP generate/verify)
│       └── aws/                     # AWS Engine (credential rotation)
├── vault-glue-spring-boot-starter/    # Starter dependency bundle
├── vault-glue-test/                   # TestContainers + Vault test support
└── docs/
    ├── 01-plan/features/vault-glue.plan.md
    └── 02-design/features/vault-glue.design.md
```

## Tech Stack

- Java 21+
- Spring Boot 3.5.11
- Spring Cloud 2025.0.1
- Gradle 8.14.3 (Kotlin DSL, native platform() BOM)
- HikariCP (DB connection pool)

## Configuration Example (Target UX)

```yaml
vault-glue:
  kv:
    enabled: true
    backend: app
    version: 2
  database:
    sources:
      primary:
        type: static          # static | dynamic
        role: my-service-static-dev
        backend: db
        jdbc-url: jdbc:mysql://db:3306/mydb
        driver-class-name: com.mysql.cj.jdbc.Driver
        refresh-interval: 18000000
  transit:
    enabled: true
    backend: transit
    keys:
      user-pii:
        type: aes256-gcm96
        auto-create: true
  on-failure: retry           # restart | retry | ignore
```

## Current Status

### Done
- [x] Gradle multi-module project setup
- [x] Core: Properties, Event System (5 events), FailureStrategy (restart/retry/ignore), HealthIndicator
- [x] DB Engine: Static Role (credential provider + scheduler + graceful shutdown)
- [x] DB Engine: Dynamic Role (lease listener + auto rotation)
- [x] DB Engine: Multi-DataSource (VaultGlueDataSources container)
- [x] KV Engine: VaultKvOperations (CRUD + versioning + list)
- [x] KV Engine: @VaultValue annotation + BeanPostProcessor
- [x] KV Engine: Watch mode (change detection polling)
- [x] Transit Engine: VaultTransitOperations (encrypt/decrypt/rewrap/hmac/sign/verify)
- [x] Transit Engine: @VaultEncrypt JPA AttributeConverter
- [x] Transit Engine: Auto key creation (TransitKeyInitializer)
- [x] PKI Engine: VaultPkiOperations + CertificateRenewalScheduler
- [x] TOTP Engine: VaultTotpOperations
- [x] AWS Engine: VaultAwsCredentialProvider + auto rotation
- [x] Actuator: VaultGlueHealthIndicator
- [x] AutoConfiguration.imports registration (all 8)
- [x] Build verified

### TODO
- [ ] GitHub org creation + push (vault-glue org or vault-glue-dev)
- [ ] JitPack or Maven Central publishing setup
- [ ] Test code (TestContainers + Vault)
- [ ] README.md
- [ ] GitHub Actions CI/CD
- [ ] Finalize group ID (io.github.vault-glue or io.vaultglue)

## Conventions

### Imports
- Explicit imports only (no wildcard `*`)
- Always use import statements — never reference types by fully qualified path
- Group order: `java.*` → `jakarta.*` → `org.springframework.*` → `io.vaultglue.*`
- No static imports

### Naming
- Package: `io.vaultglue.*`
- Classes: PascalCase (`VaultGlueProperties`)
- Interfaces: Descriptive nouns (`VaultKvOperations`)
- Implementations: `Default` prefix (`DefaultVaultKvOperations`)
- AutoConfiguration: `*AutoConfiguration`
- Properties: `*Properties`, prefix `vault-glue.*`
- Methods/variables: camelCase
- Constants: UPPER_SNAKE_CASE (`VG_PREFIX`)
- Boolean getters: `isEnabled()` (not `getEnabled()`)
- Enum values: ALL_CAPS (`RESTART`, `RETRY`, `IGNORE`)

### Code Style
- Indentation: 4 spaces (no tabs)
- No Lombok — all constructors, getters/setters written manually
- Underscore separators in numeric literals (`18_000_000`)
- Switch expressions (`case A -> ...;`)
- `instanceof` pattern matching (`if (value instanceof String s)`)
- Records for immutable data; may contain business methods

### Class Structure Order
1. Static fields (logger, constants)
2. Instance fields
3. Constructor(s)
4. Public methods (interface implementations first)
5. Private helper methods
6. Inner classes / records

### Null Handling
- Explicit `if (value == null)` checks
- Return empty collections (`Collections.emptyMap()`, `List.of()`) — never return null
- Chained null checks: `response != null && response.getData() != null`

### Collections
- Immutable creation: `List.of()`, `Map.of()`
- Defensive copies: `Collections.unmodifiableMap()`
- Thread-safe maps: `ConcurrentHashMap`
- Stream API preferred (`.stream().map().toList()`)

### Logging
- Logger declaration: `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- All log messages prefixed with `[VaultGlue]`
- Parameterized placeholders: `log.info("[VaultGlue] Message: {}", value)` — no string concatenation
- Never log credentials in plaintext — mask or truncate

### Spring Patterns
- `@AutoConfiguration` (not `@Configuration`)
- Conditional order: `@ConditionalOnClass` → `@ConditionalOnBean` → `@ConditionalOnMissingBean`
- `@EnableConfigurationProperties` for properties binding
- Nested static inner classes for configuration grouping within Properties classes
- `ApplicationEventPublisher` for event-driven architecture
- `ObjectProvider` for optional dependency injection
- `@Bean(destroyMethod = "shutdown")` for lifecycle management

### Annotations
- One per line, on a separate line above the target element
- Always include `@Override`

### Exception Handling
- Custom exceptions extend `RuntimeException` (`VaultTransitException`)
- Preserve cause: `throw new RuntimeException("message", e)`
- `InterruptedException` must call `Thread.currentThread().interrupt()`

### Concurrency
- `ScheduledExecutorService` for background refresh/rotation
- `volatile` for fields accessed by multiple threads
- `AtomicInteger` for thread-safe counters

### Comments
- All comments in English (open-source, Apache 2.0 license)
- Javadoc: minimal, only for complex public APIs
- Section dividers: `// ─── Section Name ───`

## Build

```bash
./gradlew compileJava    # Compile
./gradlew build          # Full build
./gradlew test           # Run tests
```

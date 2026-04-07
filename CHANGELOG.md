# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-03-31

### Breaking Changes
- **[Transit]** `encryptBatch()`, `decryptBatch()`, `rewrapBatch()` now return `BatchResult<String>` instead of `List<String>` — supports partial failure handling. Use `.successes()` for previous behavior.
- **[Transit]** `@VaultEncrypt` annotation removed — it was a non-functional marker. Use `@Convert(converter = VaultEncryptConverter.class)` directly.

### Added
- **[Transit]** `BatchResult<T>` and `BatchResultItem<T>` records for batch operation results with per-item success/failure tracking

### Fixed
- **[Transit]** `VaultEncryptConverter` now uses lazy initialization — resolves JPA bean ordering issues where converter was used before Vault connection was ready
- **[Transit]** `extractBoolean()` now throws `VaultTransitException` on null/empty responses instead of silently returning `false` — prevents server errors from being misinterpreted as "verification failed"
- **[PKI]** `FailureStrategy` (restart/retry/ignore) now applied to certificate renewal failures — previously only logged errors
- **[KV]** `FailureStrategy` now applied to watch polling failures — previously only logged errors
- **[AWS]** `FailureStrategy` now applied to credential rotation failures — previously only logged errors

## [0.3.0] - 2026-03-24

### Breaking Changes
- **[Database]** `vault-glue.database.enabled: true` is now **required** to activate the database engine. Previously it activated implicitly when HikariCP was on the classpath.
- **[Build]** `spring-cloud-vault-config` is now a `compileOnly` (peer) dependency in `vault-glue-autoconfigure`. The starter (`vault-glue-spring-boot-starter`) provides it transitively. If you depend on `-autoconfigure` directly, add `spring-cloud-vault-config` to your project.

### Added
- **[Transit]** `vault-glue.transit.default-key` property — explicitly set the default key for `VaultEncryptConverter` instead of relying on YAML key order
- **[Transit]** `vault-glue.transit.allow-plaintext-read` property — enables gradual migration from plaintext to encrypted columns without `IllegalStateException`
- **[Core]** `VaultGlueTimeUtils` shared TTL parser utility (replaces duplicated logic in PKI/AWS)
- **[Core]** `VaultGlueHealthIndicator` now works without database engine — contributor-based design supports all engines
- **[Transit]** `VaultTransitException` extracted to top-level class for catchability via interface
- **[Database]** `VaultGlueDelegatingDataSource` implements `Closeable` — pools are properly closed on shutdown
- **[Database]** `HikariDataSourceFactory.createPlaceholder()` — placeholder pools use `minimumIdle=0` to avoid bogus connection attempts
- **[Database]** `GracefulShutdown.awaitAll()` — tracks and joins virtual shutdown threads during application shutdown

### Fixed
- **[Critical]** `FailureStrategyHandler` retry was async (`CompletableFuture.runAsync`) — exceptions silently swallowed. Now synchronous with shutdown escalation on exhaustion
- **[Critical]** `DynamicLeaseListener` latch deadlock — null credentials blocked `register()` for 30s. Now uses `AtomicReference<Exception>` for immediate error propagation
- **[Critical]** KV Watch mode non-functional — `@VaultValue(refresh=true)` paths were never registered with watcher. Now auto-registered via `VaultValueBeanPostProcessor.setWatcher()`
- **[Critical]** `VaultAwsCredentialProvider.start()` crashed application on initial Vault failure. Now catches and logs, scheduler retries
- **[Critical]** `VaultEncryptConverter` static state never cleared between Spring context refreshes. Added `reset()` + `DisposableBean`
- **[Critical]** `CertificateRenewalScheduler` issued duplicate cert on startup (`initialDelay=0`). Changed to `scheduleWithFixedDelay` with `initialDelay=interval`
- **[Critical]** Published POM had `runtime` scope for core dependencies. Changed to `api` scope; `compileOnly` deps now marked `<optional>true</optional>`
- **[Database]** `StaticRefreshScheduler.schedulers` was plain `ArrayList` — `ConcurrentModificationException` risk. Changed to `CopyOnWriteArrayList`
- **[Database]** `StaticRefreshScheduler` execution counter was global across all DataSources — now per-source
- **[Database]** `GracefulShutdown` calls `softEvictConnections()` before shutdown to reduce rotation race window
- **[Database]** Multiple DataSources marked `primary: true` now validated at startup
- **[Database]** `@AutoConfigureBefore(DataSourceAutoConfiguration.class)` added to prevent bean ordering conflict
- **[KV]** `VaultKvWatcher.pollChanges()` NPE when Vault path deleted — now returns empty map
- **[KV]** `DefaultVaultKvOperations.metadata()` returned null — now returns empty `VaultKvMetadata`
- **[KV]** `VaultKvMetadata.customMetadata` was mutable — now wrapped with `Collections.unmodifiableMap()`
- **[KV]** `VaultGlueKvProperties.version` accepts only 1 or 2
- **[KV]** `DefaultVaultKvOperations.parseInstant()` returned null — now returns `Instant.EPOCH`
- **[Transit]** `Base64.decode()` exception in decrypt now wrapped in `VaultTransitException`
- **[Transit]** `TransitKeyInitializer` swallowed all exceptions silently — now logs at DEBUG
- **[PKI]** `parseTtl()` replaced with shared `VaultGlueTimeUtils.parseTtl()`
- **[Build]** POM generation resolves versions from `compileClasspath` + `runtimeClasspath`
- **[Build]** Testcontainers versions unified via BOM (removed explicit `1.20.4` pinning)
- **[Health]** `VaultGlueHealthAutoConfiguration` now requires `@ConditionalOnBean(VaultGlueEventPublisher.class)`
- **[Health]** `VaultGlueHealthAutoConfiguration` added `@ConditionalOnClass(HikariDataSource.class)` guard

### Security
- **[TOTP]** `TotpKey.toString()` now masks `barcode` and `url` (TOTP secret exposure risk)
- **[AWS]** `AwsCredential.toString()` truncates `accessKey` to 4 chars, masks `secretKey`/`securityToken`
- **[PKI]** `CertificateBundle.toString()` masks `privateKey`
- **[Database]** `DbCredential.toString()` masks `password`
- **[Database]** Username and leaseId logs demoted from INFO to DEBUG across all credential providers
- **[Health]** `currentUsername` removed from `/actuator/health` response
- **[Transit]** Batch error message no longer includes raw response item (could contain plaintext/ciphertext)
- **[Build]** `compileOnly` dependencies marked `<optional>true</optional>` in published POM

## [0.2.2] - 2026-03-23

### Fixed
- **[Core]** `FailureStrategy.RETRY` now throws after exhaustion instead of silently falling back to RESTART (application shutdown)
- **[PKI]** `parseTtl()` uses `substring()` and logs warning on unrecognized format (same fix as AWS `parseTtlMs`)
- **[PKI]** Renewal scheduler runs first check immediately instead of waiting `checkInterval` milliseconds
- **[Database]** Static schedulers and DataSources are cleaned up when multi-DataSource initialization fails partway
- **[Database]** `DynamicLeaseListener` error handler counts down latch for fast failure instead of 30s timeout
- **[KV]** `VaultValueBeanPostProcessor` implements `DestructionAwareBeanPostProcessor` to remove destroyed beans from refresh tracking

## [0.2.0] - 2026-03-23

### Breaking Changes
- **`VaultEncryptConverter`** no longer silently passes through unencrypted data. If your database contains unencrypted values in columns marked with `@Convert(converter = VaultEncryptConverter.class)`, they will now throw `IllegalStateException` at read time. **Migration:** encrypt all existing plaintext values before upgrading, or handle the exception during a migration period.

### Added
- GitHub Actions CI workflow (build/test on push and PR to main/develop)
- GitHub Actions publish workflow (tag-based auto-publish to Maven Central)
- Dual GPG signing support (`useInMemoryPgpKeys` for CI, `useGpgCmd` for local)

### Fixed
- **[Security]** `VaultEncryptConverter` initialization is now thread-safe with synchronized lock
- **[Database]** `DynamicLeaseListener` only counts down initialization latch on successful credential rotation
- **[Database]** Placeholder `HikariDataSource` is now closed after dynamic credential rotation succeeds
- **[Database]** `StaticRefreshScheduler` uses `scheduleWithFixedDelay` to prevent concurrent refreshes on the same DataSource
- **[KV]** `VaultValueBeanPostProcessor.refreshAll()` preserves old cached values when Vault is unavailable
- **[KV]** `VaultKvWatcher` uses `scheduleWithFixedDelay` to prevent poll queue buildup
- **[Transit]** `extractBatchResults()` throws `VaultTransitException` with clear message instead of NPE on missing result key
- **[TOTP]** `validate()` throws `RuntimeException` on Vault error instead of returning `false`
- **[AWS]** `getCredential()` throws `IllegalStateException` before `start()` instead of returning `null`
- **[AWS]** STS credential types now validate `security_token` presence
- **[AWS]** `parseTtlMs()` logs warning on unrecognized TTL format instead of silently using default
- **[AWS]** Scheduled credential rotation now survives exceptions instead of permanently stopping the scheduler
- **[Security]** `VaultEncryptConverter` caches `defaultKeyName` in local variable to prevent volatile double-read race
- **[Database]** Placeholder `HikariDataSource` is now closed even when `register()` times out (prevents connection pool leak)
- **[TOTP]** `generateCode()` throws on missing `code` key in Vault response instead of returning `null`
- **[KV]** `refreshAll()` cache now removes entries for deleted secrets and unwatched paths

## [0.1.3] - 2026-03-19

### Fixed
- BOM propagation bug: consumer projects using `dependency-management-plugin` crashed with `archaius-core:0.3.3` binary store corruption
- Suppress Gradle Module Metadata to prevent BOM constraint propagation to consumers
- Strip `<dependencyManagement>` from published POM and resolve explicit versions
- Replace `spring-cloud-starter-vault-config` with `spring-cloud-vault-config` (core only) in autoconfigure module

## [0.1.1] - 2026-03-18

### Added
- First successful Maven Central publish

### Known Issues
- BOM constraints propagate to consumers (fixed in 0.1.3)

## [0.1.0] - 2026-03-17

### Added
- Core: `VaultGlueProperties`, Event System (5 events), `FailureStrategy` (restart/retry/ignore), `HealthIndicator`
- Database Engine: Static Role (credential provider + scheduler + graceful shutdown)
- Database Engine: Dynamic Role (lease listener + auto rotation)
- Database Engine: Multi-DataSource (`VaultGlueDataSources` container)
- KV Engine: `VaultKvOperations` (CRUD + versioning + list)
- KV Engine: `@VaultValue` annotation + `BeanPostProcessor`
- KV Engine: Watch mode (change detection polling)
- Transit Engine: `VaultTransitOperations` (encrypt/decrypt/rewrap/hmac/sign/verify)
- Transit Engine: `@VaultEncrypt` JPA `AttributeConverter`
- Transit Engine: Auto key creation (`TransitKeyInitializer`)
- PKI Engine: `VaultPkiOperations` + `CertificateRenewalScheduler`
- TOTP Engine: `VaultTotpOperations`
- AWS Engine: `VaultAwsCredentialProvider` + auto rotation
- Actuator: `VaultGlueHealthIndicator`
- All 8 `AutoConfiguration.imports` registered
- Gradle multi-module project (autoconfigure + starter + test)

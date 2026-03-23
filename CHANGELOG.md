# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

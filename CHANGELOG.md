# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-03-23

### Fixed
- **[Security]** `VaultEncryptConverter` no longer silently returns plaintext data — throws `IllegalStateException` instead
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

## [0.1.3] - 2026-03-20

### Fixed
- Suppress Gradle Module Metadata to prevent BOM constraint propagation to consumers
- Strip `<dependencyManagement>` from published POM and resolve explicit versions
- Replace `spring-cloud-starter-vault-config` with `spring-cloud-vault-config` (core only) in autoconfigure module

### Added
- GitHub Actions CI workflow (build/test on push and PR to main/develop)
- GitHub Actions publish workflow (tag-based auto-publish to Maven Central)
- Dual GPG signing support (`useInMemoryPgpKeys` for CI, `useGpgCmd` for local)

## [0.1.2] - 2026-03-19

### Fixed
- BOM propagation bug: consumer projects using `dependency-management-plugin` crashed with `archaius-core:0.3.3` binary store corruption

## [0.1.1] - 2026-03-18

### Added
- First successful Maven Central publish

### Known Issues
- BOM constraints propagate to consumers (fixed in 0.1.2)

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

# Design Review Log

Records design review findings, improvements, and rationale for each version.

---

## 2026-03-23 — v0.2.2 Third Review Pass

Deep functional review of all engines identified 6 additional issues — behavioral bugs and resource management gaps.

### Fixes

| Item | Change | Rationale |
|------|--------|-----------|
| `FailureStrategyHandler.retryWithBackoff()` | Throws `RuntimeException` after retry exhaustion instead of calling `shutdownApplication()` | Users configuring `onFailure=RETRY` expect retry-then-fail, not retry-then-restart. Silent fallback to RESTART violated the semantic contract and caused unexpected application shutdowns |
| `CertificateRenewalScheduler.parseTtl()` | `replace()` → `substring()`, added `log.warn` on parse failure | Same `replace()` bug as AWS `parseTtlMs()`. `"10dd".replace("d","")` strips all occurrences. Compound formats like `"1h30m"` caused silent 72h default |
| `CertificateRenewalScheduler.start()` | Initial delay changed from `interval` to `0` | Short-TTL cert + long checkInterval meant first renewal check ran after cert already expired. Now checks immediately on scheduler start |
| `VaultGlueDatabaseAutoConfiguration.vaultGlueDataSources()` | Added try-catch around DataSource init loop; on failure, shuts down schedulers and closes already-created pools | In multi-DataSource config, if the 2nd source fails init, the 1st source's static refresh scheduler kept running as a leaked background thread |
| `DynamicLeaseListener` error listener | Added `initialLatch.countDown()` in error handler | Error before first credential event left `register()` blocking for full 30s timeout. Now fails fast on Vault errors |
| `VaultValueBeanPostProcessor` | Implements `DestructionAwareBeanPostProcessor`, adds `postProcessBeforeDestruction()` and `requiresDestruction()` | `refreshableFields` held strong references to destroyed beans, preventing GC. Prototype-scoped beans would accumulate indefinitely. Now removed on bean destruction |

### Design Decisions

- **RETRY exhaustion behavior:** Throwing is the safest default — the caller (scheduler or init code) can decide whether to retry again, ignore, or escalate. RESTART should only happen when explicitly configured.
- **PKI first check at t=0:** Combined with the existing initial cert issue in the constructor, this creates a double-check on startup (constructor issues, then scheduler immediately verifies). This is intentional — belt and suspenders for cert freshness.
- **DestructionAwareBeanPostProcessor:** Chosen over `WeakReference` because Spring's destruction callback is more reliable than GC timing. `WeakReference` keys in a ConcurrentHashMap would require periodic cleanup, adding complexity.

---

## 2026-03-23 — v0.2.0 Second Review Pass

After the initial 12 bugfixes, a second deep functional review identified 5 additional issues introduced or missed by the first pass.

### Additional Fixes

| Item | Change | Rationale |
|------|--------|-----------|
| `VaultAwsCredentialProvider.rotate()` | Added `scheduledRotate()` wrapper that catches exceptions; scheduler calls wrapper instead of `rotate()` directly | `ScheduledExecutorService` permanently stops future executions on uncaught exception. One failed rotation was killing all future credential refreshes. Initial `start()` call still fails fast. |
| `VaultGlueDatabaseAutoConfiguration.createDynamicDataSource()` | Wrapped `listener.register()` in try-catch; close placeholder in both success and failure paths | If `register()` times out (30s), placeholder HikariDataSource was never closed — connection pool leak. Now closed in all code paths. |
| `VaultEncryptConverter.convertToDatabaseColumn()` | Cache `defaultKeyName` volatile field into local variable before use | Field was read twice (encrypt call + prefix assembly). If reassigned between reads via concurrent `initialize()`, the prefix would not match the encryption key — causing decryption failure later. |
| `DefaultVaultTotpOperations.generateCode()` | Added null check on "code" key from Vault response | `validate()` throws on empty response but `generateCode()` silently returned null. Interface declares non-null return but implementation could return null. Now consistent. |
| `VaultValueBeanPostProcessor.refreshAll()` | Replace `cache.putAll()` with per-entry put + `removeIf` for unwatched paths | `putAll()` only added/updated entries, never removed. Deleted secrets stayed in cache forever. Now: successful fetches update cache, unwatched paths are evicted. Failed paths retain previous values. |

### Design Decisions

- **Scheduler exception safety pattern:** `scheduledRotate()` wrapping `rotate()` is the same pattern used by `CertificateRenewalScheduler`. All scheduled tasks must catch exceptions to prevent executor death.
- **Placeholder close in both paths:** Using try-catch (not try-finally) because the success path and failure path need different log messages. `finally` would also close on success before returning, which is the same behavior but less readable.
- **Cache eviction scope:** Only evicts paths no longer in `refreshableFields`. Paths that failed to fetch are NOT evicted — they keep their previous value until a successful fetch replaces them.

---

## 2026-03-23 — v0.2.0 Critical Bugfix Review

Full code review identified and fixed 12 critical/high-severity bugs across all engines.

### Transit Engine

| Item | Change | Rationale |
|------|--------|-----------|
| `VaultEncryptConverter.convertToEntityAttribute()` | Removed plaintext fallback → throws `IllegalStateException` | Columns marked with `@Convert` should never contain plaintext. Silent passthrough treated unencrypted data as valid — security risk |
| `VaultEncryptConverter.initialize()` | Added `synchronized (INIT_LOCK)`, single volatile read in `getTransitOperations()` | JPA can invoke the converter before AutoConfiguration completes. Both `applicationContext` and `defaultKeyName` must be visible atomically |
| `DefaultVaultTransitOperations.extractBatchResults()` | Added null check on `item.get(key)` → throws `VaultTransitException` | Missing key in Vault response caused NPE. Clear error message aids debugging |

### Database Engine

| Item | Change | Rationale |
|------|--------|-----------|
| `DynamicLeaseListener.handleCreated()` | Moved `initialLatch.countDown()` out of `finally`, called only on success | Latch fired even with null credentials, causing DataSource registration with placeholder creds. Now times out after 30s on failure |
| `VaultGlueDatabaseAutoConfiguration.createDynamicDataSource()` | Close placeholder `HikariDataSource` after successful rotation | Placeholder pool was never closed — connection pool resource leak |
| `StaticRefreshScheduler.schedule()` | `scheduleAtFixedRate` → `scheduleWithFixedDelay` | If refresh took longer than interval, concurrent refreshes on the same DataSource could corrupt the connection pool |

### KV Engine

| Item | Change | Rationale |
|------|--------|-----------|
| `VaultValueBeanPostProcessor.refreshAll()` | Removed `cache.clear()`, replaced with swap-on-success pattern | Clearing cache before re-fetch meant Vault failure left empty cache. New approach: fetch into new cache first, merge only successful entries. Failed paths retain previous values |
| `VaultKvWatcher.start()` | `scheduleAtFixedRate` → `scheduleWithFixedDelay` | Slow Vault responses caused poll tasks to queue up, exhausting resources |

### AWS Engine

| Item | Change | Rationale |
|------|--------|-----------|
| `VaultAwsCredentialProvider.getCredential()` | Returns null → throws `IllegalStateException` | Calling before `start()` caused NPE in downstream code |
| `VaultAwsCredentialProvider.rotate()` | Added `security_token` validation for STS types | `sts`, `assumed_role`, `federation_token` require security_token. Missing token caused cryptic AWS SDK failures instead of early detection |
| `VaultAwsCredentialProvider.parseTtlMs()` | `replace()` → `substring()`, added `log.warn` on parse failure | Compound formats like `"1h30m"` caused silent fallback to default. `substring` strips only the trailing unit char; failures now log a warning |

### TOTP Engine

| Item | Change | Rationale |
|------|--------|-----------|
| `DefaultVaultTotpOperations.validate()` | Null response returns `false` → throws `RuntimeException` | Vault outage was silently treated as "invalid code", denying all OTP validations. Vault errors and auth failures must be distinguishable |

### Design Decisions

- **Scope:** Targeted fixes only — no refactoring or new features. Only bugs that could cause production failures.
- **FailureStrategy unification deferred:** KV/PKI/AWS/TOTP do not consistently apply FailureStrategy. Acknowledged but excluded from 0.2.0 scope — planned as separate work.
- **@VaultEncrypt annotation issue deferred:** The annotation's `key`/`context` fields are never read (only `VaultEncryptConverter` via JPA `@Convert` is used). Requires API design change — planned for 0.3.0.

---

## 2026-03-20 — v0.1.3 BOM Propagation Fix

### Build / Publishing

| Item | Change | Rationale |
|------|--------|-----------|
| Gradle Module Metadata | `GenerateModuleMetadata.enabled = false` | Consumer projects using `dependency-management-plugin` crashed with `archaius-core:0.3.3` binary store corruption. Gradle Module Metadata propagated Spring Cloud BOM constraints to consumers |
| Published POM | Strip `<dependencyManagement>`, resolve explicit versions via `pom.withXml` | Libraries must not force BOM versions on consumers. `dependency-management-plugin` merges all BOMs and corrupted Gradle binary store with large dependency graphs |
| `spring-cloud-starter-vault-config` | Replaced with `spring-cloud-vault-config` (core only) in autoconfigure | Starter pulled transitive dependencies that bloated consumer projects. Autoconfigure only needs the core library |

### Design Decisions

- **No BOM in published artifacts:** VaultGlue uses `platform()` internally for version management but strips all BOM references from published POM. Consumers must manage their own Spring Boot/Cloud versions.
- **Always verify with consumer:** Before publishing new versions, test with `publishToMavenLocal` + consumer Gradle sync to catch `dependency-management-plugin` conflicts.

---

## 2026-03-23 — CI/CD Setup

### Infrastructure

| Item | Change | Rationale |
|------|--------|-----------|
| GitHub Actions CI | Added `.github/workflows/ci.yml` — build/test on push and PR to main/develop | Automated build verification for every change |
| GitHub Actions Publish | Added `.github/workflows/publish.yml` — tag-based (`v*`) auto-publish to Maven Central | Manual publishing was error-prone. Tag push triggers build → sign → publish automatically |
| GPG signing | Dual mode: `useInMemoryPgpKeys()` for CI, `useGpgCmd()` fallback for local | CI has no GPG agent. In-memory key loaded from GitHub Secrets; local dev uses existing GPG keyring |

### Design Decisions

- **Tag-triggered publishing:** Version in `build.gradle` must match tag name (validated in workflow). Prevents accidental mismatched releases.
- **Secrets in GitHub Actions only:** Credentials never stored in code. `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_SIGNING_KEY`, `GPG_SIGNING_PASSWORD` as repository secrets.

---

## 2026-03-17 — v0.1.0 Initial Architecture

### Core Design

| Item | Decision | Rationale |
|------|----------|-----------|
| Multi-module structure | `autoconfigure` + `starter` + `test` | Standard Spring Boot starter convention. Autoconfigure contains logic, starter bundles dependencies, test provides TestContainers support |
| Configuration prefix | `vault-glue.*` | Avoids collision with `spring.cloud.vault.*` (Spring Cloud Vault). Clear namespace separation |
| Vault connection | Delegates to `spring-cloud-vault` | No need to reinvent Vault auth/connection. VaultGlue focuses on secret engine operations only |
| FailureStrategy | `restart` / `retry` / `ignore` | Operators need different responses to Vault failures. Restart kills the app (fail-fast), retry with backoff for transient issues, ignore for non-critical engines |
| Event system | `ApplicationEventPublisher` with 5 event types | Loose coupling — users can react to credential rotation, lease events without depending on VaultGlue internals |
| No Lombok | Manual constructors, getters, setters | Reduces magic, improves debuggability, avoids annotation processor issues in consumer projects |
| Database multi-source | `VaultGlueDataSources` container with named access | Enterprise apps often need multiple databases (primary + replica). Named access via `vaultGlueDataSources.get("replica")` |
| Static vs Dynamic DB credentials | Separate implementations (`StaticRefreshScheduler` vs `DynamicLeaseListener`) | Static: timer-based rotation from Vault `/creds/`. Dynamic: lease-based via `SecretLeaseContainer`. Fundamentally different lifecycle models |
| Transit `@VaultEncrypt` | JPA `AttributeConverter` with `vg:{key}:vault:v1:...` format | Embeds key name in ciphertext so different fields can use different keys. Supports key rotation and legacy format migration |

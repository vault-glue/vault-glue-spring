# Design Review Log

Records design review findings, improvements, and rationale for each version.

---

## 2026-04-07 — v0.4.0 Post-Release Code Review (3 passes, 15 issues)

Three-pass deep review of the full codebase after v0.4.0 release. No Critical issues found on final pass.

### Fixes Applied

| Item | Change | Rationale |
|------|--------|-----------|
| KV/PKI retry recursion | Extracted `doPollChanges()`/`doCheckAndRenew()` — retry lambda calls discrete operation only | `pollChanges()` retry lambda called `pollChanges()` again, which re-entered `failureStrategyHandler.handle()` → exponential recursion (3^3=27 attempts for maxAttempts=3) |
| RETRY exhaustion behavior | Retry exhaustion now logs and continues with stale state instead of `shutdownApplication()` | RETRY and RESTART had identical failure behavior. Users choosing RETRY expect degraded operation, not shutdown |
| AWS `scheduleAtFixedRate` | Changed to `scheduleWithFixedDelay` | All other engines used `scheduleWithFixedDelay`. `atFixedRate` stacks executions when Vault is slow |
| `GracefulShutdown.awaitAll()` | Per-thread timeout → total deadline | N threads × 10s timeout = potentially N×10s blocking. Now respects a single total deadline |
| AWS role validation | Added null/blank check in `start()` | Missing role produced confusing `aws/sts/null` Vault path error |
| `CertificateBundle.getRemainingHours()` | `Math.max(0, ...)` to prevent negative values | Expired cert showed negative hours in logs |
| `HikariDataSourceFactory` placeholder | Added `connectionTimeout(250)` | Placeholder DataSource should fail fast if accidentally used, not block 30s |
| Engine-specific exceptions | Created `VaultPkiException`, `VaultTotpException`, `VaultAwsException` | Generic `RuntimeException` across PKI/TOTP/AWS made it impossible to catch engine-specific failures |
| `VaultEncryptConverter` exceptions | `RuntimeException` → `VaultTransitException` | Inconsistent with other Transit code that used `VaultTransitException` |
| PKI initial issuance | Applied `failureStrategyHandler` to `start()` initial cert issuance | Initial failure was logged and silently swallowed — no retry/restart applied |
| `@VaultEncrypt` annotation | Removed entirely | No processing logic existed. `@Convert(converter = VaultEncryptConverter.class)` is the actual mechanism |
| Unused import | Removed `CredentialRotatedEvent` from `DynamicLeaseListener` | Import was unused (event published in `DataSourceRotator`) |
| TOTP/AWS missed exception replacements | `validate()` `RuntimeException` → `VaultTotpException`, security_token `RuntimeException` → `VaultAwsException` | Missed during batch exception replacement in 2nd pass |

### Design Decisions

- **RETRY exhaustion → continue (not shutdown):** Previous behavior made RETRY identical to RESTART on failure. New behavior: retry with backoff, then continue operating with stale credentials. The next scheduled cycle will retry again. This gives transient Vault outages a chance to self-heal.
- **`@VaultEncrypt` removal:** The annotation was a non-functional marker. JPA `AttributeConverter` registration happens via `@Convert`, not custom annotations. Removing eliminates user confusion about unused `key()`/`context()` parameters.

### Remaining Items (Documented, Not Fixed)

- `VaultEncryptConverter` / `GracefulShutdown` static state — JPA `AttributeConverter` constraint (no-arg constructor, no DI). Documented as known limitation for multi-context test environments.
- `DynamicLeaseListener` global listeners — `SecretLeaseContainer` API does not provide `removeLeaseListener`. Listeners are filtered by path, which is sufficient for typical deployments.

---

## 2026-03-24 — v0.2.3 Comprehensive Code Review (42 issues)

Full codebase audit across all engines, build config, and tests. Parallel review by 5 agents covering core, database, KV/Transit/PKI/TOTP/AWS, tests, and build configuration.

### Critical Fixes (11)

| Item | Change | Rationale |
|------|--------|-----------|
| `FailureStrategyHandler` | Removed `CompletableFuture.runAsync` — retry now runs synchronously; escalates to `shutdownApplication()` on exhaustion | Exception thrown inside `runAsync` was silently swallowed (nobody called `.get()`). Retry failures were invisible in production |
| `DynamicLeaseListener` latch deadlock | Added `AtomicReference<Exception>` for error propagation; all error paths now call `countDown()` | Null credentials or null body caused `register()` to block 30s with misleading timeout error instead of real cause |
| `DynamicLeaseListener` error check | `register()` now checks `initialError` after latch release and throws with real cause | Error listener counted down latch but caller proceeded to use broken DataSource backed by closed placeholder |
| `StaticRefreshScheduler` thread safety | `ArrayList` → `CopyOnWriteArrayList` for `schedulers` list | `shutdown()` during context failure cleanup could overlap with `schedule()`, causing `ConcurrentModificationException` |
| KV Watch path registration | `VaultValueBeanPostProcessor` now calls `watcher.watch(path)` for `@VaultValue(refresh=true)` fields | Watch mode was completely non-functional — `lastKnownValues` was always empty so changes were never detected |
| `VaultAwsCredentialProvider.start()` | Wrapped initial `rotate()` in try-catch | Uncaught exception in `start()` crashed entire application context. PKI had this guard but AWS did not |
| `VaultEncryptConverter` static state | Added `reset()` method; `VaultEncryptConverterInitializer` implements `DisposableBean` | Static `ApplicationContext` reference was never cleared — caused stale context errors in tests with multiple context refreshes |
| Build: dependency scopes | `implementation` → `api` for core deps; `compileOnly` deps marked `<optional>true</optional>` in POM | Published POM had `runtime` scope for `spring-boot-autoconfigure` — consumers couldn't compile. Optional deps were invisible |
| `VaultGlueDatabaseAutoConfiguration` | Added `@ConditionalOnProperty(prefix="vault-glue.database", name="enabled")` | Unlike all other engines, database activated implicitly with HikariCP on classpath — caused errors without configuration |
| `CertificateRenewalScheduler` | `initialDelay` 0→interval; `scheduleAtFixedRate` → `scheduleWithFixedDelay` | Immediate delay=0 after initial issue caused duplicate Vault API call on every startup; `atFixedRate` could stack invocations |

### Important Fixes (21)

| Item | Change | Rationale |
|------|--------|-----------|
| `VaultGlueDelegatingDataSource` | Implements `Closeable`; `VaultGlueDataSources.close()` closes all pools | Active HikariPools were never closed on context shutdown — connection leak |
| `HikariDataSourceFactory` | Added `createPlaceholder()` with `minimumIdle=0`, `initializationFailTimeout=-1` | Placeholder with bogus credentials triggered DB auth failure logs and potential account lockout |
| `StaticRefreshScheduler` counter | Global `AtomicInteger` → per-source `ConcurrentHashMap<String, AtomicInteger>` | Log showed wrong count when multiple DataSources shared the global counter |
| `DefaultVaultKvOperations.metadata()` | Returns empty `VaultKvMetadata` instead of `null` | Violated project convention "never return null" |
| `VaultKvMetadata` record | Compact constructor wraps `customMetadata` with `Collections.unmodifiableMap()` | Mutable map in immutable record broke encapsulation |
| `VaultTransitException` | Extracted from `DefaultVaultTransitOperations` inner class to top-level | Callers using `VaultTransitOperations` interface couldn't catch the exception without depending on implementation class |
| `DefaultVaultTransitOperations.decrypt()` | Wrapped `Base64.getDecoder().decode()` in try-catch → `VaultTransitException` | `IllegalArgumentException` propagated without context — unclear error message |
| `CertificateBundle.toString()` | Override to mask `privateKey` | Auto-generated `toString()` would expose private key in logs |
| `AwsCredential.toString()` | Override to mask `secretKey` and `securityToken` | Same credential exposure risk as CertificateBundle |
| `DbCredential.toString()` | Override to mask `password` | Same credential exposure risk |
| `VaultGlueHealthAutoConfiguration` | Added `@ConditionalOnClass(HikariDataSource.class)` | Without HikariCP, `VaultGlueHealthIndicator` threw `NoClassDefFoundError` at runtime |
| AWS SDK dependency | Removed `software.amazon.awssdk:auth:2.29.45` (dead dependency) | No source file referenced any AWS SDK class |
| `GracefulShutdown` | Added `softEvictConnections()` before shutdown wait loop | Idle connections in old pool were not marked for eviction — threads getting connections during rotation window hit "Pool has been shutdown" |
| `GracefulShutdown` thread tracking | `CopyOnWriteArrayList<Thread>` + `awaitAll()` method | Virtual threads were fire-and-forget — JVM shutdown before completion left old pools unclosed |
| `VaultGlueHealthIndicator` | Refactored to `HealthContributor`-based design; DB is optional via `ObjectProvider` | Previously required `VaultGlueDataSources` — non-DB engines had no health reporting at all |
| `VaultEncryptConverter` | Added `allowPlaintextRead` flag for migration support | Existing plaintext data in DB columns caused `IllegalStateException` during encryption adoption — no migration path |
| `spring-cloud-vault-config` | Changed to `compileOnly` in autoconfigure; starter provides `spring-cloud-starter-vault-config` | Library was forcing specific version on consumers who already had their own version managed |
| `VaultGlueDatabaseAutoConfiguration` | Added `@AutoConfigureBefore(DataSourceAutoConfiguration.class)` | Without ordering, Spring's built-in `DataSource` bean could win over VaultGlue's |
| `VaultGlueDatabaseAutoConfiguration` | Added validation: at most one source can be `primary: true` | Multiple primary sources picked winner by iteration order — non-deterministic |
| `VaultGlueTransitProperties` | Added `defaultKey` property | Previously relied on `LinkedHashMap` iteration order for first key — fragile implicit contract |
| `VaultKvWatcher.pollChanges()` | Null guard on `kvOperations.get(path)` | Deleted Vault path returned null → `NullPointerException` aborted entire poll cycle |

### Suggestion Fixes (10)

| Item | Change | Rationale |
|------|--------|-----------|
| `VaultGlueTimeUtils` | New shared utility class for TTL parsing | `parseTtl`/`parseTtlMs` was duplicated between `CertificateRenewalScheduler` and `VaultAwsCredentialProvider` |
| CLAUDE.md | "Kotlin DSL" → "Groovy DSL" | All build scripts are `.gradle` (Groovy), not `.gradle.kts` |
| `TransitKeyInitializer` | `catch (Exception ignored)` → debug log | Network errors and permission errors were silently swallowed alongside expected "not found" |
| `VaultGlueKvProperties` | Version setter validates 1 or 2 | Invalid values like 3 silently fell through to KV_2 |
| `DefaultVaultKvOperations` | `java.util.Arrays` → import statement | Violated project convention "always use import statements" |
| Testcontainers versions | Removed explicit `1.20.4` pinning — BOM-managed | Mixed explicit/BOM versions could cause classpath conflicts |
| `VaultGlueTransitProperties` | Added `allowPlaintextRead` property | Supports gradual migration from plaintext to encrypted columns |

### Design Decisions

- **Synchronous retry:** Async retry via `CompletableFuture` was fundamentally broken (unobserved exceptions). Synchronous execution is simpler and the caller thread is already a background scheduler thread in all real call sites — no UI thread blocking concern.
- **Retry exhaustion → shutdown:** When all retries fail, the system is in an unrecoverable state. Escalating to RESTART (context close) is safer than silently continuing with stale credentials.
- **HealthIndicator contributor pattern:** `HealthContributor` functional interface allows future engines (PKI, Transit, AWS) to add their own health checks without modifying the indicator class.
- **Plaintext read migration:** `allow-plaintext-read=true` returns unencrypted data as-is; the next JPA write re-encrypts it. This enables zero-downtime migration without a batch job.
- **spring-cloud-vault as peer dependency:** Autoconfigure module compiles against it but doesn't force a version. The starter provides it for batteries-included experience. Advanced users can exclude the starter's transitive and bring their own version.

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
- **@VaultEncrypt annotation:** Removed entirely in v0.4.0 code review. The annotation had no processing logic — `VaultEncryptConverter` via JPA `@Convert` is the only mechanism.

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
| Transit `VaultEncryptConverter` | JPA `AttributeConverter` with `vg:{key}:vault:v1:...` format via `@Convert` | Embeds key name in ciphertext so different fields can use different keys. Supports key rotation and legacy format migration. `@VaultEncrypt` marker removed in v0.4.0 — no processing logic existed |

# Review Log

코드 리뷰 및 설계 리뷰 기록. `code-review-loop` 및 `design-review` 스킬이 이 파일에 기록한다.

---

## 2026-04-08 Cycle 1

### Summary
- 스캔 범위: 전체 / 61 Java 소스 파일
- 소요 시간: ~5m
- 발견: 10건 / 수정: 10건 / 스킵: 5건

### Changes
- `[quality]` `pki/CertificateRenewalScheduler.java:59,80` — failureStrategyHandler 엔진명 "PKI" → "pki" (소문자 통일)
- `[quality]` `aws/VaultAwsCredentialProvider.java:62` — failureStrategyHandler 엔진명 "AWS" → "aws" (소문자 통일)
- `[quality]` `pki/CertificateRenewalSchedulerTest.java:45` — 테스트 assertion 동기화 ("PKI" → "pki")
- `[quality]` `aws/VaultAwsCredentialProviderTest.java:85` — 테스트 assertion 동기화 ("AWS" → "aws")
- `[quality]` `database/DataSourceRotator.java` — import 순서 정리 (java → javax → com.zaxxer → org.slf4j → io.vaultglue)
- `[quality]` `database/VaultGlueDelegatingDataSource.java` — import 순서 정리 (java → javax → com.zaxxer)
- `[quality]` `database/GracefulShutdown.java` — import 순서 정리 (java → com.zaxxer → org.slf4j) + Thread.sleep(1000) → 1_000
- `[quality]` `database/static_/StaticRefreshScheduler.java` — import 순서 정리 (java → org.slf4j → io.vaultglue)
- `[quality]` `database/dynamic/DynamicLeaseListener.java` — import 순서 정리 (java → org.slf4j → org.springframework → io.vaultglue)
- `[quality]` `database/static_/StaticCredentialProvider.java:26,35` — exception 메시지에 [VaultGlue] prefix 추가
- `[quality]` `core/VaultGlueProperties.java:38` — delay = 5000 → 5_000 (underscore separator 컨벤션)

### Skipped (수동 확인 필요)
- `[refactor]` `DefaultVaultKvOperations` + `DefaultVaultTransitOperations` — 중복 `toInt()` 헬퍼 (스킵 사유: 클래스 구조 변경 필요)
- `[architecture]` `StaticCredentialProvider.VaultGlueCredentialException` — top-level 분리 검토 (스킵 사유: catch 시맨틱 변경 위험)
- `[architecture]` `VaultEncryptConverter` static 상태 — 멀티 컨텍스트 환경 충돌 가능 (스킵 사유: JPA AttributeConverter 구조 제약)
- `[refactor]` `CertificateRenewalScheduler` — TTL 파싱 3회 반복 호출 (스킵 사유: 가독성 감소 우려)
- `[quality]` `VaultAwsCredentialProvider:51` — 0.8 renewal factor 매직넘버 (스킵 사유: 컨텍스트 충분, 상수화 효과 미미)

---

## 2026-04-07 Cycle 1

### Summary
- 스캔 범위: 전체 / autoconfigure + test (약 50 파일)
- 발견: 5건 / 수정: 5건 / 스킵: 3건

### Changes
- `[quality]` `kv/VaultKvWatcher.java:11-12` — import 순서: `io.vaultglue`를 `org.slf4j` 앞으로 이동 (CLAUDE.md 컨벤션)
- `[quality]` `transit/VaultEncryptConverter.java:137` — `[VaultGlue]` prefix 추가 (exception 메시지 컨벤션 통일)
- `[quality]` `kv/DefaultVaultKvOperations.java:63,97,109,120` — `[VaultGlue]` prefix 추가 (UnsupportedOperationException 4곳)
- `[quality]` `aws/VaultAwsCredentialProvider.java:62`, `pki/CertificateRenewalScheduler.java:59,80`, `kv/VaultKvWatcher.java:67` — 엔진명 소문자 통일 ("AWS"→"aws", "PKI"→"pki", "KV"→"kv") + 테스트 3개 동기화
- `[refactor]` `kv/DefaultVaultKvOperations.java:152` — 불필요한 `@SuppressWarnings("unchecked")` 제거

### Skipped (수동 확인 필요)
- `[refactor]` `DefaultVaultKvOperations` + `DefaultVaultTransitOperations` — 중복 `toInt()` 헬퍼 (스킵 사유: 클래스 구조 변경 필요)
- `[architecture]` `StaticCredentialProvider.VaultGlueCredentialException` — `VaultDatabaseException` 미상속 (스킵 사유: catch 시맨틱 변경 위험)
- `[architecture]` KV/Transit/PKI/TOTP/AWS AutoConfig — `@ConditionalOnClass` 미사용 (스킵 사유: 패턴 선택, 위반 아님)

---

## 2026-04-07 — v0.4.0 Post-Release Code Review (5 passes, 18 issues)

### Summary
- 스캔 범위: 전체 코드베이스 (autoconfigure, test, build, docs)
- 발견: 18건 / 수정: 18건 / 스킵: 0건

### Changes
- `[quality]` `kv/VaultKvWatcher.java:79-82` — KV retry 무한 재귀 제거: `doPollChanges()` 추출 (retry 람다가 전체 메서드를 재호출 → 3^3=27회 재시도 폭발)
- `[quality]` `pki/CertificateRenewalScheduler.java:102-105` — PKI retry 무한 재귀 제거: `doCheckAndRenew()` 추출 (동일한 재귀 패턴)
- `[architecture]` `core/FailureStrategyHandler.java:62-65` — RETRY 소진 시 shutdown → stale state 유지 (RETRY와 RESTART 동작이 동일해지는 문제)
- `[quality]` `aws/VaultAwsCredentialProvider.java:49` — `scheduleAtFixedRate` → `scheduleWithFixedDelay` (Vault 느릴 때 작업 적체 방지)
- `[quality]` `database/GracefulShutdown.java:41-52` — per-thread 타임아웃 → 총 deadline 방식 (N개 스레드 × 10s 대기 방지)
- `[quality]` `aws/VaultAwsCredentialProvider.java:37-41` — role null/blank 검증 추가 (`aws/sts/null` 경로 방지)
- `[quality]` `pki/CertificateBundle.java:15-17` — `getRemainingHours()` 음수 방지: `Math.max(0, ...)`
- `[quality]` `database/HikariDataSourceFactory.java:32` — placeholder DataSource에 `connectionTimeout(250)` 추가 (fast-fail)
- `[architecture]` `pki/VaultPkiException.java`, `totp/VaultTotpException.java`, `aws/VaultAwsException.java`, `database/VaultDatabaseException.java` — 엔진별 Exception 클래스 생성 (generic RuntimeException → 엔진별 분리)
- `[quality]` `transit/VaultEncryptConverter.java:82,122` — `RuntimeException` → `VaultTransitException`
- `[quality]` `pki/CertificateRenewalScheduler.java:53-59` — 초기 인증서 발급 실패 시 `failureStrategyHandler` 적용
- `[refactor]` `transit/VaultEncrypt.java` — 삭제 (기능 없는 마커 어노테이션, `@Convert` 사용이 실제 메커니즘)
- `[refactor]` `database/dynamic/DynamicLeaseListener.java:5` — 미사용 import `CredentialRotatedEvent` 제거
- `[quality]` `totp/DefaultVaultTotpOperations.java:66` — `validate()` RuntimeException → `VaultTotpException` (교체 누락)
- `[quality]` `aws/VaultAwsCredentialProvider.java:105` — security_token RuntimeException → `VaultAwsException` (교체 누락)
- `[quality]` `database/dynamic/DynamicLeaseListener.java:82,87,93` — 5곳 `RuntimeException` → `VaultDatabaseException`
- `[quality]` `database/dynamic/DynamicLeaseListener.java:115,126` — `initialError`에 저장하는 RuntimeException 2곳 → `VaultDatabaseException`
- `[quality]` `kv/VaultKvWatcher.java:49-53` — `watch()` 초기 fetch 실패 시 empty map 저장 (path 영구 유실 방지)

### Design Decisions
- **RETRY exhaustion → continue (not shutdown):** RETRY와 RESTART의 동작이 동일해지는 문제. 새 동작: retry 후 stale state로 계속 운영, 다음 스케줄 사이클에서 재시도. Vault 일시 장애 자가 회복 가능.
- **`@VaultEncrypt` removal:** 기능 없는 마커. JPA `AttributeConverter` 등록은 `@Convert`로 수행. 사용자 혼란 제거.
- **`VaultDatabaseException` 신규:** 다른 엔진(Transit, PKI, TOTP, AWS)과 동일하게 top-level exception 제공.

### Skipped (수동 확인 필요)
- `[architecture]` `VaultEncryptConverter` / `GracefulShutdown` static 상태 — JPA `AttributeConverter` 제약 (no-arg constructor). 멀티 컨텍스트 테스트 환경에서 충돌 가능. (스킵 사유: 구조적 제약, 문서화로 대체)
- `[architecture]` `DynamicLeaseListener` global listeners — `SecretLeaseContainer` API가 `removeLeaseListener` 미제공. (스킵 사유: 외부 API 제약)

---

## 2026-03-24 `[design]` v0.2.3 Comprehensive Code Review (42 issues)

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

## 2026-03-23 `[design]` v0.2.2 Third Review Pass

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

## 2026-03-23 `[design]` v0.2.0 Second Review Pass

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

## 2026-03-23 `[design]` v0.2.0 Critical Bugfix Review

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
| `DefaultVaultTotpOperations.validate()` | Null response returns `false` → throws `VaultTotpException` | Vault outage was silently treated as "invalid code", denying all OTP validations. Vault errors and auth failures must be distinguishable |

### Design Decisions

- **Scope:** Targeted fixes only — no refactoring or new features. Only bugs that could cause production failures.
- **FailureStrategy unification deferred:** KV/PKI/AWS/TOTP do not consistently apply FailureStrategy. Acknowledged but excluded from 0.2.0 scope — planned as separate work.
- **@VaultEncrypt annotation:** Removed entirely in v0.4.0 code review. The annotation had no processing logic — `VaultEncryptConverter` via JPA `@Convert` is the only mechanism.

---

## 2026-03-20 `[design]` v0.1.3 BOM Propagation Fix

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

## 2026-03-23 `[design]` CI/CD Setup

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

## 2026-03-17 `[design]` v0.1.0 Initial Architecture

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

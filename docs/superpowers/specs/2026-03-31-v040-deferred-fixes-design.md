# v0.4.0 Deferred Fixes Design Spec

## Overview

Five items deferred from v0.3.0 code review. All address correctness, consistency, or API clarity issues.

## Items

### 1. Transit Batch Partial Failure (Breaking Change)

**Problem:** `encryptBatch()`, `decryptBatch()`, `rewrapBatch()` throw on the first failed item, discarding all successful results.

**Solution:** Return `BatchResult<T>` instead of `List<String>`.

```java
public record BatchResultItem<T>(int index, T value, String error) {
    public boolean isSuccess() {
        return error == null;
    }
}

public record BatchResult<T>(List<BatchResultItem<T>> items) {
    public List<T> successes() { /* filter isSuccess */ }
    public List<BatchResultItem<T>> failures() { /* filter !isSuccess */ }
    public boolean hasFailures() { /* any failure exists */ }
}
```

**Changed signatures in `VaultTransitOperations`:**
- `BatchResult<String> encryptBatch(String keyName, List<String> plaintexts)`
- `BatchResult<String> decryptBatch(String keyName, List<String> ciphertexts)`
- `BatchResult<String> rewrapBatch(String keyName, List<String> ciphertexts)`

**Implementation:**
- `extractBatchResults()` in `DefaultVaultTransitOperations` stops throwing on error items
- Error items get `BatchResultItem(index, null, errorMessage)`
- Success items get `BatchResultItem(index, value, null)`

**Files:**
- `io.vaultglue.transit.BatchResultItem` (new record)
- `io.vaultglue.transit.BatchResult` (new record)
- `io.vaultglue.transit.VaultTransitOperations` (interface change)
- `io.vaultglue.transit.DefaultVaultTransitOperations` (implementation change)

---

### 2. @VaultEncrypt Remove Unused Fields (Breaking Change)

**Problem:** `@VaultEncrypt` has `key()` and `context()` fields that `VaultEncryptConverter` never reads. Users may think per-field key selection works when it doesn't.

**Solution:** Remove both fields, making `@VaultEncrypt` a marker annotation.

```java
// Before
@interface VaultEncrypt {
    String key();
    String context() default "";
}

// After
@interface VaultEncrypt {
    // Marker annotation — encryption uses the configured default key
}
```

**Impact:** Code using `@VaultEncrypt(key = "xxx")` will get a compile error. Migration: remove the arguments.

**Files:**
- `io.vaultglue.transit.VaultEncrypt` (remove fields)

---

### 3. VaultEncryptConverter Lazy Initialization

**Problem:** `VaultEncryptConverter` relies on `VaultEncryptConverterInitializer` bean being created before JPA uses the converter. If JPA initializes first, `applicationContext` is null and throws `IllegalStateException`.

**Solution:** Lazy lookup pattern. Instead of static `initialize()` called at startup, the converter resolves its dependencies on first use.

**Implementation:**
- Remove `VaultEncryptConverterInitializer` inner class from `VaultGlueTransitAutoConfiguration`
- Remove static `initialize()` / `destroy()` methods from `VaultEncryptConverter`
- Keep static `applicationContext` field, but populate it via `ApplicationContextAware` or a simple `@Bean` that sets it early
- On first `convertToDatabaseColumn()` / `convertToEntityAttribute()` call, lazily look up `VaultTransitOperations` from the context
- Cache the looked-up bean in a volatile field after first resolution
- Thread-safe via existing `INIT_LOCK` synchronization

**Files:**
- `io.vaultglue.transit.VaultEncryptConverter` (lazy lookup)
- `io.vaultglue.autoconfigure.VaultGlueTransitAutoConfiguration` (remove initializer, add context-setting bean)

---

### 4. extractBoolean() Consistency

**Problem:** `extractBoolean()` returns `false` on null/empty responses, while `extractString()` throws `VaultTransitException`. This hides server errors as "verification failed".

**Solution:** Align `extractBoolean()` with `extractString()`.

```java
// Before
private boolean extractBoolean(VaultResponse response, String key) {
    if (response == null || response.getData() == null) {
        return false;  // silent default
    }
    return toBoolean(response.getData().get(key));
}

// After
private boolean extractBoolean(VaultResponse response, String key) {
    if (response == null || response.getData() == null) {
        throw new VaultTransitException("Empty response from Vault transit");
    }
    Object value = response.getData().get(key);
    if (value == null) {
        throw new VaultTransitException("Missing '" + key + "' in transit response");
    }
    return toBoolean(value);
}
```

**Files:**
- `io.vaultglue.transit.DefaultVaultTransitOperations` (fix `extractBoolean()`)

---

### 5. FailureStrategy Applied to PKI, KV, AWS Engines

**Problem:** Only the Database engine uses `FailureStrategyHandler` for background task failures. PKI, KV, and AWS engines log errors but ignore the configured failure strategy.

**Solution:** Inject `FailureStrategyHandler` into each engine's background scheduler and call it on failures.

**Scope:** Only engines with periodic background tasks:
- **PKI** — `CertificateRenewalScheduler.checkAndRenew()` catch block
- **KV** — Watch polling failure path
- **AWS** — Credential renewal failure path

Transit and TOTP have no background tasks, so they are excluded.

**Implementation per engine:**

**PKI:**
- `VaultGluePkiAutoConfiguration`: inject `FailureStrategyHandler` into `CertificateRenewalScheduler`
- `CertificateRenewalScheduler`: add `FailureStrategyHandler` field, call `handler.handle()` in catch block of `checkAndRenew()`

**KV:**
- `VaultGlueKvAutoConfiguration`: inject `FailureStrategyHandler` into watch component
- Watch polling: call `handler.handle()` on poll failure

**AWS:**
- `VaultGlueAwsAutoConfiguration`: inject `FailureStrategyHandler` into `VaultAwsCredentialProvider`
- `VaultAwsCredentialProvider`: call `handler.handle()` on credential renewal failure

**Files:**
- `io.vaultglue.autoconfigure.VaultGluePkiAutoConfiguration`
- `io.vaultglue.pki.CertificateRenewalScheduler`
- `io.vaultglue.autoconfigure.VaultGlueKvAutoConfiguration`
- `io.vaultglue.kv.VaultKvWatcher` (or equivalent watch component)
- `io.vaultglue.autoconfigure.VaultGlueAwsAutoConfiguration`
- `io.vaultglue.aws.VaultAwsCredentialProvider`

---

## Breaking Changes Summary

| Item | Breaking? | Migration |
|------|-----------|-----------|
| #1 BatchResult | Yes | Change `List<String>` to `BatchResult<String>`, use `.successes()` for previous behavior |
| #2 @VaultEncrypt fields | Yes | Remove `key`/`context` arguments from annotation usage |
| #3 Lazy init | No | Transparent |
| #4 extractBoolean | No | Previously silent false becomes exception (correct behavior) |
| #5 FailureStrategy | No | Existing `on-failure` config now applies to more engines |

## Testing

- Unit tests for `BatchResult` / `BatchResultItem` record methods
- Unit test for `extractBoolean()` null response → exception
- Integration tests for FailureStrategy in PKI, KV, AWS schedulers
- Verify VaultEncryptConverter works without explicit `initialize()` call (lazy path)
- Compile-time verification that `@VaultEncrypt` no longer accepts arguments

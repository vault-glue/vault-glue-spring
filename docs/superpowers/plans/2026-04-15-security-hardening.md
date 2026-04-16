# Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 보안 감사에서 발견된 CRITICAL/HIGH/MEDIUM 취약점을 수정한다.

**Architecture:** 기존 동작을 보존하면서 방어적 검증 계층을 추가. VaultPathUtils 유틸 클래스로 경로 검증을 중앙화하고, 민감 데이터 노출 경로를 차단한다.

**Tech Stack:** Java 21, Spring Boot 3.5, VaultGlue autoconfigure module

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `core/VaultPathUtils.java` | Vault 경로 세그먼트 검증 유틸 |
| Create | `pki/CertificateRenewalInfo.java` | 이벤트용 인증서 메타데이터 (private key 미포함) |
| Modify | `core/event/CertificateRenewedEvent.java` | CertificateBundle → CertificateRenewalInfo |
| Modify | `pki/CertificateRenewalScheduler.java` | issue-before-revoke 순서 변경 + 이벤트 변경 |
| Modify | `database/static_/StaticCredentialProvider.java` | DbCredential hashCode/equals override |
| Modify | `aws/VaultAwsCredentialProvider.java` | AwsCredential hashCode/equals override |
| Modify | `pki/CertificateBundle.java` | hashCode/equals override (privateKey 제외) |
| Modify | `transit/DefaultVaultTransitOperations.java` | 경로 검증 + null 검증 + batch Base64 에러 처리 |
| Modify | `totp/DefaultVaultTotpOperations.java` | 경로 검증 + null 검증 |
| Modify | `pki/DefaultVaultPkiOperations.java` | 경로 검증 |
| Modify | `kv/DefaultVaultKvOperations.java` | 경로 검증 |
| Modify | `core/FailureStrategyHandler.java` | RESTART circuit breaker + retry 상한 |
| Modify | `core/VaultGlueProperties.java` | retry 속성 검증 |
| Modify | `transit/TransitKeyInitializer.java` | auto-create 실패 시 예외 전파 |
| Modify | `autoconfigure/VaultGlueDatabaseAutoConfiguration.java` | createStaticDataSource try-catch |

---

### Task 1: [CRITICAL] CertificateRenewedEvent에서 private key 제거

**Files:**
- Create: `vault-glue-autoconfigure/src/main/java/io/vaultglue/pki/CertificateRenewalInfo.java`
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/core/event/CertificateRenewedEvent.java`
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/pki/CertificateRenewalScheduler.java`

- [ ] **Step 1: Create CertificateRenewalInfo record**

```java
package io.vaultglue.pki;

import java.time.Instant;

public record CertificateRenewalInfo(
    String serialNumber,
    Instant expiresAt,
    long remainingHours
) {
    public static CertificateRenewalInfo from(CertificateBundle bundle) {
        return new CertificateRenewalInfo(
                bundle.serialNumber(),
                bundle.expiresAt(),
                bundle.getRemainingHours());
    }
}
```

- [ ] **Step 2: Modify CertificateRenewedEvent to use CertificateRenewalInfo**

```java
package io.vaultglue.core.event;

import io.vaultglue.core.VaultGlueEvent;
import io.vaultglue.pki.CertificateRenewalInfo;

public class CertificateRenewedEvent extends VaultGlueEvent {

    private final CertificateRenewalInfo renewalInfo;

    public CertificateRenewedEvent(Object source, String engine, String identifier,
                                    CertificateRenewalInfo renewalInfo) {
        super(source, engine, identifier);
        this.renewalInfo = renewalInfo;
    }

    public CertificateRenewalInfo getRenewalInfo() {
        return renewalInfo;
    }
}
```

- [ ] **Step 3: Update CertificateRenewalScheduler to publish CertificateRenewalInfo**

Change line 104-105 in `CertificateRenewalScheduler.java`:
```java
eventPublisher.publish(new CertificateRenewedEvent(
        this, "pki", properties.getCommonName(),
        CertificateRenewalInfo.from(renewed)));
```

Add import: `import io.vaultglue.pki.CertificateRenewalInfo;`

- [ ] **Step 4: Build and run tests**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[security] remove private key from CertificateRenewedEvent

- CertificateRenewalInfo record: metadata only (serial, expiry, remaining hours)
- Event listeners can no longer access private key material
- Severity: CRITICAL"
```

---

### Task 2: [HIGH] Vault 경로 검증 유틸 + 전 엔진 적용

**Files:**
- Create: `vault-glue-autoconfigure/src/main/java/io/vaultglue/core/VaultPathUtils.java`
- Modify: Transit, TOTP, PKI, KV, Database operations

- [ ] **Step 1: Create VaultPathUtils**

```java
package io.vaultglue.core;

public final class VaultPathUtils {

    private VaultPathUtils() {}

    /**
     * Validates that a value is safe to use as a Vault path segment.
     * Rejects null, blank, path traversal (.. or /), and control characters.
     */
    public static void validatePathSegment(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " must not be null or blank");
        }
        if (value.contains("/") || value.contains("..")) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " contains invalid path characters");
        }
        if (value.contains("\\") || value.chars().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " contains invalid characters");
        }
    }

    /**
     * Validates a Vault mount path (backend). Allows single forward slashes for nested mounts
     * but rejects path traversal.
     */
    public static void validateMountPath(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " must not be null or blank");
        }
        if (value.contains("..") || value.startsWith("/") || value.endsWith("/")) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " contains invalid path characters");
        }
    }
}
```

- [ ] **Step 2: Apply to DefaultVaultTransitOperations — add validation to all public methods**

At the top of each public method that accepts `keyName`, add:
```java
VaultPathUtils.validatePathSegment(keyName, "keyName");
```

Methods to modify: `encrypt`, `decrypt`, `encryptBatch`, `decryptBatch`, `rewrap`, `rewrapBatch`, `hmac`, `verifyHmac`, `sign`, `verify`, `createKey`, `rotateKey`, `getKeyInfo`

Also add null checks for data parameters (plaintext, ciphertext, data):
```java
if (plaintext == null) {
    throw new IllegalArgumentException("[VaultGlue] plaintext must not be null");
}
```

Add import: `import io.vaultglue.core.VaultPathUtils;`

- [ ] **Step 3: Apply to DefaultVaultTotpOperations — validate `name` parameter**

At the top of `createKey`, `generateCode`, `validate`, `deleteKey`:
```java
VaultPathUtils.validatePathSegment(name, "name");
```

Add import: `import io.vaultglue.core.VaultPathUtils;`

- [ ] **Step 4: Apply to DefaultVaultPkiOperations — validate role in issue()**

In `issue()` method, validate the role parameter:
```java
VaultPathUtils.validatePathSegment(role, "role");
```

Add import: `import io.vaultglue.core.VaultPathUtils;`

- [ ] **Step 5: Apply to DefaultVaultKvOperations — validate path parameter**

In public methods that accept `path` (`get`, `put`, `delete`, `undelete`, `destroy`, `metadata`), validate:
```java
VaultPathUtils.validatePathSegment(path, "path");
```

Note: KV paths may contain `/` for nested paths. Use `validateMountPath` instead of `validatePathSegment` for the `path` parameter in KV operations. Actually, KV paths like `my-app/config` are valid, so skip path validation for KV `path` parameter — only validate the backend in Properties.

- [ ] **Step 6: Apply to database StaticCredentialProvider and DynamicLeaseListener — validate backend/role**

`StaticCredentialProvider.getCredential()`:
```java
VaultPathUtils.validatePathSegment(backend, "backend");
VaultPathUtils.validatePathSegment(role, "role");
```

`DynamicLeaseListener.register()`:
```java
VaultPathUtils.validatePathSegment(props.getBackend(), "backend");
VaultPathUtils.validatePathSegment(props.getRole(), "role");
```

- [ ] **Step 7: Build and run tests**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "[security] add Vault path validation to prevent path traversal

- VaultPathUtils: centralized path segment validation
- Applied to Transit, TOTP, PKI, Database operations
- Rejects ../ and / in key names, role names, backend paths
- Severity: HIGH"
```

---

### Task 3: [HIGH] RESTART 전략 circuit breaker 추가

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/core/FailureStrategyHandler.java`

- [ ] **Step 1: Add circuit breaker state and logic**

Add fields:
```java
private static final int MAX_RESTARTS = 3;
private static final long RESTART_WINDOW_MS = 300_000; // 5 minutes

private final java.util.Deque<Long> restartTimestamps = new java.util.concurrent.ConcurrentLinkedDeque<>();
```

Modify `shutdownApplication`:
```java
private void shutdownApplication(String engine, String identifier, Exception cause) {
    long now = System.currentTimeMillis();

    // Remove timestamps outside the window
    while (!restartTimestamps.isEmpty()
            && now - restartTimestamps.peekFirst() > RESTART_WINDOW_MS) {
        restartTimestamps.pollFirst();
    }

    if (restartTimestamps.size() >= MAX_RESTARTS) {
        log.error("[VaultGlue] RESTART circuit breaker triggered for {}/{}. "
                + "{} restarts in {}ms window. Falling back to IGNORE.",
                engine, identifier, MAX_RESTARTS, RESTART_WINDOW_MS, cause);
        return;
    }

    restartTimestamps.addLast(now);
    log.error("[VaultGlue] Fatal failure in {}/{}. Shutting down application.",
            engine, identifier, cause);
    applicationContext.close();
}
```

Add imports:
```java
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
```

- [ ] **Step 2: Add retry delay bounds in VaultGlueProperties**

In `RetryProperties`, modify setters:
```java
public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = Math.max(1, Math.min(maxAttempts, 20));
}

public void setDelay(long delay) {
    this.delay = Math.max(100, Math.min(delay, 300_000));
}
```

- [ ] **Step 3: Cap retry delay to prevent overflow in FailureStrategyHandler**

In `retryWithBackoff`, change:
```java
long delay = Math.min(baseDelay * attempt, 300_000);
```

- [ ] **Step 4: Build and run tests**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[security] add RESTART circuit breaker and retry bounds

- Max 3 restarts per 5-minute window, then fallback to IGNORE
- Retry maxAttempts clamped to [1, 20], delay to [100ms, 300s]
- Retry delay capped at 300s to prevent overflow
- Severity: HIGH"
```

---

### Task 4: [MEDIUM] Record hashCode/equals에서 민감 필드 제외

**Files:**
- Modify: `database/static_/StaticCredentialProvider.java` (DbCredential)
- Modify: `aws/VaultAwsCredentialProvider.java` (AwsCredential)
- Modify: `pki/CertificateBundle.java`

- [ ] **Step 1: Override DbCredential hashCode/equals**

In `StaticCredentialProvider.java`, add to `DbCredential` record:
```java
public record DbCredential(String username, String password) {
    @Override
    public int hashCode() {
        return java.util.Objects.hash(username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DbCredential that)) return false;
        return java.util.Objects.equals(username, that.username);
    }

    @Override
    public String toString() {
        return "DbCredential[username=" + username + ", password=***masked***]";
    }
}
```

- [ ] **Step 2: Override AwsCredential hashCode/equals**

In `VaultAwsCredentialProvider.java`, add to `AwsCredential` record:
```java
public record AwsCredential(String accessKey, String secretKey, String securityToken) {
    @Override
    public int hashCode() {
        return java.util.Objects.hash(accessKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AwsCredential that)) return false;
        return java.util.Objects.equals(accessKey, that.accessKey);
    }

    @Override
    public String toString() {
        return "AwsCredential[accessKey="
                + (accessKey != null ? accessKey.substring(0, Math.min(4, accessKey.length())) + "..." : "null")
                + ", secretKey=***masked***, securityToken=***masked***]";
    }
}
```

- [ ] **Step 3: Override CertificateBundle hashCode/equals**

In `CertificateBundle.java`, add:
```java
@Override
public int hashCode() {
    return java.util.Objects.hash(serialNumber, expiresAt);
}

@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CertificateBundle that)) return false;
    return java.util.Objects.equals(serialNumber, that.serialNumber)
            && java.util.Objects.equals(expiresAt, that.expiresAt);
}
```

- [ ] **Step 4: Build and run tests**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "[security] exclude sensitive fields from record hashCode/equals

- DbCredential: exclude password
- AwsCredential: exclude secretKey, securityToken
- CertificateBundle: exclude privateKey, certificate, issuingCa, caChain
- Prevents timing attacks via equals() and hash-based information leakage
- Severity: MEDIUM"
```

---

### Task 5: [MEDIUM] CertificateRenewalScheduler issue-before-revoke + createStaticDataSource cleanup

**Files:**
- Modify: `pki/CertificateRenewalScheduler.java`
- Modify: `autoconfigure/VaultGlueDatabaseAutoConfiguration.java`

- [ ] **Step 1: Fix certificate renewal order — issue first, then revoke**

Replace `doCheckAndRenew()` lines 87-105:
```java
private void doCheckAndRenew() {
    CertificateBundle current = pkiOperations.getCurrent();
    if (current == null || current.isExpiringSoon(properties.getRenewThresholdHours())) {
        log.info("[VaultGlue] Certificate expiring soon (remaining={}h), renewing...",
                current != null ? current.getRemainingHours() : 0);

        // Issue new certificate first to ensure availability
        CertificateBundle renewed = pkiOperations.issue(
                properties.getRole(),
                properties.getCommonName(),
                getEffectiveTtl());

        // Revoke old certificate only after successful issue
        if (current != null && current.serialNumber() != null) {
            try {
                pkiOperations.revoke(current.serialNumber());
                log.info("[VaultGlue] Previous certificate revoked: serial={}",
                        current.serialNumber());
            } catch (Exception revokeEx) {
                log.warn("[VaultGlue] Failed to revoke previous certificate: serial={}",
                        current.serialNumber(), revokeEx);
            }
        }

        eventPublisher.publish(new CertificateRenewedEvent(
                this, "pki", properties.getCommonName(),
                CertificateRenewalInfo.from(renewed)));

        log.info("[VaultGlue] Certificate renewed: serial={}, expires={}",
                renewed.serialNumber(), renewed.expiresAt());
    }
}
```

- [ ] **Step 2: Add try-catch to createStaticDataSource**

Replace `createStaticDataSource()`:
```java
private VaultGlueDelegatingDataSource createStaticDataSource(
        String name, DataSourceProperties props,
        StaticCredentialProvider credentialProvider,
        StaticRefreshScheduler refreshScheduler) {

    StaticCredentialProvider.DbCredential cred =
            credentialProvider.getCredential(props.getBackend(), props.getRole());

    HikariDataSource hikari = HikariDataSourceFactory.create(name, props, cred.username(), cred.password());
    VaultGlueDelegatingDataSource delegating =
            new VaultGlueDelegatingDataSource(name, hikari, cred.username());

    try {
        refreshScheduler.schedule(name, delegating, props);
    } catch (Exception e) {
        if (!hikari.isClosed()) {
            hikari.close();
            log.debug("[VaultGlue] Closed DataSource for '{}' after scheduler registration failure", name);
        }
        throw e;
    }
    return delegating;
}
```

- [ ] **Step 3: Build and run tests**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "[security] fix certificate renewal order and static DataSource cleanup

- Issue new certificate before revoking old (prevents no-certificate window)
- Add try-catch in createStaticDataSource to close HikariDataSource on scheduler failure
- Severity: MEDIUM"
```

---

### Task 6: [MEDIUM] Transit batch Base64 에러 처리 + TransitKeyInitializer fail-fast

**Files:**
- Modify: `transit/DefaultVaultTransitOperations.java`
- Modify: `transit/TransitKeyInitializer.java`

- [ ] **Step 1: Fix decryptBatch Base64 error handling**

Replace lines 100-108 in `decryptBatch`:
```java
List<BatchResultItem<String>> decoded = rawResult.items().stream()
        .map(item -> {
            if (item.isSuccess()) {
                try {
                    String plain = new String(Base64.getDecoder().decode(item.value()), StandardCharsets.UTF_8);
                    return new BatchResultItem<String>(item.index(), plain, null);
                } catch (IllegalArgumentException e) {
                    return new BatchResultItem<String>(item.index(), null,
                            "Invalid Base64 in decrypt response");
                }
            }
            return item;
        })
        .toList();
```

- [ ] **Step 2: TransitKeyInitializer — propagate auto-create failure**

Replace lines 37-42:
```java
try {
    transitOperations.createKey(name, keyProps.getType());
    log.info("[VaultGlue] Transit key created: {} ({})", name, keyProps.getType().getValue());
} catch (Exception e) {
    throw new IllegalStateException(
            "[VaultGlue] Failed to create required transit key: " + name, e);
}
```

- [ ] **Step 3: Build and run tests**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL (may need to adjust TransitKeyInitializer test if it expects silent failure)

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "[security] fix batch decrypt error handling and transit key init

- decryptBatch: handle individual Base64 decode failures without losing entire batch
- TransitKeyInitializer: fail startup when auto-create key fails
- Severity: MEDIUM"
```

---

### Task 7: Update review-log.md and push

- [ ] **Step 1: Update review-log.md with security review findings and fixes**

- [ ] **Step 2: Push to remote**

```bash
git push origin review/2026-04-15-1
```

# Critical Bugfix Plan — VaultGlue 0.2.0

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 12 critical/high-severity bugs across all VaultGlue engines — security, race conditions, NPEs, and silent failures.

**Bugs covered:**
1. VaultEncryptConverter plaintext fallback (security) → Task 1
2. DynamicLeaseListener latch counts down on missing credentials → Task 2
3. DynamicLeaseListener placeholder DataSource leak → Task 2
4. VaultValueBeanPostProcessor cache.clear() data loss on refresh → Task 3
5. Transit batch NPE on missing result key → Task 4
6. VaultEncryptConverter static initialization race condition → Task 5
7. AWS getCredential() returns null before start() → Task 6
8. AWS STS missing security_token validation → Task 6
9. TOTP validate conflates Vault error with invalid code → Task 7
10. TTL parsing silent default on unrecognized format → Task 8
11. StaticRefreshScheduler concurrent refresh overlap → Task 9
12. KV Watcher scheduleAtFixedRate poll queue buildup → Task 10

**Architecture:** Targeted fixes only — no refactoring, no new features. Each task modifies 1-2 source files + adds test coverage for the fix.

**Task dependencies:** Tasks 1 and 5 both modify `VaultEncryptConverter.java`. Apply Task 1 first, then Task 5 on top.

**Tech Stack:** Java 21, Spring Boot 3.5.11, JUnit 5, Mockito

---

## File Map

| Task | Source File(s) | Test File(s) |
|------|---------------|--------------|
| 1 | `transit/VaultEncryptConverter.java` | `transit/VaultEncryptConverterTest.java` (create) |
| 2 | `database/dynamic/DynamicLeaseListener.java` | `database/dynamic/DynamicLeaseListenerTest.java` (create) |
| 3 | `kv/VaultValueBeanPostProcessor.java` | `kv/VaultValueBeanPostProcessorTest.java` (create) |
| 4 | `transit/DefaultVaultTransitOperations.java` | `transit/DefaultVaultTransitOperationsTest.java` (modify) |
| 5 | `aws/VaultAwsCredentialProvider.java` | `aws/VaultAwsCredentialProviderTest.java` (create) |
| 6 | `totp/DefaultVaultTotpOperations.java` | `totp/DefaultVaultTotpOperationsTest.java` (create) |
| 7 | `database/static_/StaticRefreshScheduler.java` | `database/StaticRefreshSchedulerTest.java` (create) |
| 8 | `autoconfigure/VaultGlueDatabaseAutoConfiguration.java` | (covered by task 2) |

All source paths relative to: `vault-glue-autoconfigure/src/main/java/io/vaultglue/`
All test paths relative to: `vault-glue-autoconfigure/src/test/java/io/vaultglue/`

---

### Task 1: VaultEncryptConverter — Remove plaintext fallback (Security)

**Issue:** `convertToEntityAttribute()` silently returns unencrypted data if it doesn't match ciphertext patterns. This is a security risk — `@Convert(converter = VaultEncryptConverter.class)` fields should never contain plaintext without explicit opt-in.

**Files:**
- Modify: `transit/VaultEncryptConverter.java:71-77`
- Create: `transit/VaultEncryptConverterTest.java`

- [ ] **Step 1: Write failing test — plaintext input should throw**

```java
package io.vaultglue.transit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultEncryptConverterTest {

    private VaultEncryptConverter converter;
    private VaultTransitOperations transitOps;

    @BeforeEach
    void setUp() {
        transitOps = Mockito.mock(VaultTransitOperations.class);
        ApplicationContext ctx = Mockito.mock(ApplicationContext.class);
        Mockito.when(ctx.getBean(VaultTransitOperations.class)).thenReturn(transitOps);
        VaultEncryptConverter.initialize(ctx, "default-key");
        converter = new VaultEncryptConverter();
    }

    @Test
    void convertToEntityAttribute_shouldThrowOnPlaintext() {
        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute("some-plaintext-data"));
    }

    @Test
    void convertToEntityAttribute_shouldReturnNullForNull() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void convertToEntityAttribute_shouldDecryptVgFormat() {
        Mockito.when(transitOps.decrypt("my-key", "vault:v1:abc123"))
                .thenReturn("secret");

        String result = converter.convertToEntityAttribute("vg:my-key:vault:v1:abc123");
        assertEquals("secret", result);
    }

    @Test
    void convertToEntityAttribute_shouldDecryptLegacyFormat() {
        Mockito.when(transitOps.decrypt("default-key", "vault:v1:abc123"))
                .thenReturn("secret");

        String result = converter.convertToEntityAttribute("vault:v1:abc123");
        assertEquals("secret", result);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.VaultEncryptConverterTest.convertToEntityAttribute_shouldThrowOnPlaintext" -i`
Expected: FAIL — currently returns plaintext instead of throwing

- [ ] **Step 3: Fix — replace plaintext fallback with exception**

In `VaultEncryptConverter.java`, replace lines 76-77:
```java
        // Unencrypted data (migration in progress)
        return dbData;
```
with:
```java
        // Unencrypted data should not exist in encrypted columns
        throw new IllegalStateException(
                "[VaultGlue] Unencrypted data found in column marked with VaultEncryptConverter. "
                + "Data does not match any known ciphertext format.");
```

- [ ] **Step 4: Run all VaultEncryptConverter tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.VaultEncryptConverterTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultEncryptConverter.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultEncryptConverterTest.java
git commit -m "fix: VaultEncryptConverter throws on unencrypted data instead of silent passthrough"
```

---

### Task 2: DynamicLeaseListener — Fix latch behavior + placeholder DataSource leak

**Issue:** (a) `initialLatch.countDown()` fires in `finally` even when credentials are missing, making registration succeed with bad state. (b) Placeholder DataSource with `"placeholder"/"placeholder"` credentials is never closed after successful rotation.

**Files:**
- Modify: `database/dynamic/DynamicLeaseListener.java:88-126`
- Modify: `autoconfigure/VaultGlueDatabaseAutoConfiguration.java:162-166`
- Create: `database/dynamic/DynamicLeaseListenerTest.java`

- [ ] **Step 1: Write failing test — simulate lease event with missing credentials, verify latch does NOT count down**

```java
package io.vaultglue.database.dynamic;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.database.DataSourceRotator;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.VaultGlueDelegatingDataSource;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicLeaseListenerTest {

    private SecretLeaseContainer leaseContainer;
    private DataSourceRotator rotator;
    private VaultGlueEventPublisher eventPublisher;
    private FailureStrategyHandler failureStrategyHandler;
    private DynamicLeaseListener listener;

    @BeforeEach
    void setUp() {
        leaseContainer = Mockito.mock(SecretLeaseContainer.class);
        rotator = Mockito.mock(DataSourceRotator.class);
        eventPublisher = Mockito.mock(VaultGlueEventPublisher.class);
        failureStrategyHandler = Mockito.mock(FailureStrategyHandler.class);
        listener = new DynamicLeaseListener(leaseContainer, rotator, eventPublisher, failureStrategyHandler);
    }

    @Test
    void register_shouldTimeoutWhenEventHasMissingCredentials() {
        // Capture the lease listener so we can fire a fake event
        ArgumentCaptor<LeaseListener> listenerCaptor = ArgumentCaptor.forClass(LeaseListener.class);

        DataSourceProperties props = new DataSourceProperties();
        props.setBackend("db");
        props.setRole("test-role");
        VaultGlueDelegatingDataSource delegating = Mockito.mock(VaultGlueDelegatingDataSource.class);

        // Run register in a separate thread since it blocks on latch
        Thread registerThread = new Thread(() -> {
            try {
                listener.register("test", delegating, props);
            } catch (RuntimeException ignored) {
                // expected timeout
            }
        });
        registerThread.start();

        // Give register() time to add the listener
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // Verify lease listener was registered, capture it, and fire event with empty credentials
        Mockito.verify(leaseContainer).addLeaseListener(listenerCaptor.capture());
        LeaseListener captured = listenerCaptor.getValue();

        RequestedSecret requestedSecret = RequestedSecret.rotating("db/creds/test-role");
        SecretLeaseCreatedEvent event = new SecretLeaseCreatedEvent(
                requestedSecret, Lease.none(), Collections.emptyMap());
        captured.onLeaseEvent(event);

        // register() should still timeout because latch was NOT counted down
        assertThrows(RuntimeException.class, () -> {
            registerThread.join(35_000);
        });

        // Rotator should NOT have been called — no valid credentials
        Mockito.verifyNoInteractions(rotator);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.dynamic.DynamicLeaseListenerTest" -i`
Expected: FAIL — current code counts down latch in `finally` even with empty credentials

- [ ] **Step 3: Fix — move latch.countDown() out of finally, only on success**

In `DynamicLeaseListener.java`, replace the `handleCreated` method (lines 88-126):

```java
    private void handleCreated(String name, VaultGlueDelegatingDataSource delegating,
                                DataSourceProperties props, SecretLeaseCreatedEvent event,
                                CountDownLatch initialLatch) {
        Duration leaseDuration = event.getLease() != null
                ? event.getLease().getLeaseDuration() : Duration.ZERO;
        String leaseId = event.getLease() != null
                ? event.getLease().getLeaseId() : "N/A";

        log.info("[VaultGlue] Lease created for '{}': leaseId={}, duration={}s",
                name, leaseId, leaseDuration.getSeconds());

        // Extract credentials from SecretLeaseCreatedEvent.getBody()
        Map<String, Object> body = event.getSecrets();
        if (body == null) {
            log.error("[VaultGlue] No credential body in lease event for '{}'", name);
            return; // Do NOT count down — let register() timeout
        }

        String username = (String) body.get("username");
        String password = (String) body.get("password");

        if (username == null || password == null) {
            log.error("[VaultGlue] Missing username/password in lease event for '{}'", name);
            return; // Do NOT count down — let register() timeout
        }

        try {
            rotator.rotate(delegating, props, username, password, leaseDuration);
            log.info("[VaultGlue] DataSource '{}' rotated via lease: user={}", name, username);
            initialLatch.countDown(); // Only on successful rotation
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to rotate DataSource '{}' on lease creation", name, e);
            failureStrategyHandler.handle("database", name, e, () -> {
                rotator.rotate(delegating, props, username, password, leaseDuration);
                initialLatch.countDown(); // Count down after retry success
                return null;
            });
        }
    }
```

- [ ] **Step 4: Fix placeholder DataSource leak in VaultGlueDatabaseAutoConfiguration**

In `VaultGlueDatabaseAutoConfiguration.java`, replace `createDynamicDataSource` method (lines 148-174).
After `listener.register(name, delegating, props)` succeeds, close the placeholder if it was replaced:

```java
    private VaultGlueDelegatingDataSource createDynamicDataSource(
            String name, DataSourceProperties props,
            VaultTemplate vaultTemplate,
            SecretLeaseContainer leaseContainer,
            DataSourceRotator rotator,
            VaultGlueEventPublisher eventPublisher,
            FailureStrategyHandler failureStrategyHandler) {

        if (leaseContainer == null) {
            throw new IllegalStateException(
                    "[VaultGlue] SecretLeaseContainer is required for dynamic DataSource '" + name
                            + "'. Ensure spring-cloud-vault-config is on the classpath.");
        }

        // Start with a placeholder DataSource — replaced via rotation once SecretLeaseContainer issues credentials
        HikariDataSource placeholder = HikariDataSourceFactory.create(
                name, props, "placeholder", "placeholder");
        VaultGlueDelegatingDataSource delegating =
                new VaultGlueDelegatingDataSource(name, placeholder, "pending");

        DynamicLeaseListener listener = new DynamicLeaseListener(
                leaseContainer, rotator, eventPublisher, failureStrategyHandler);
        // register() requests credentials from SecretLeaseContainer and waits for the initial credential
        listener.register(name, delegating, props);

        // Close the placeholder pool if rotation replaced it with a real DataSource
        DataSource current = delegating.getDelegate();
        if (current != placeholder && !placeholder.isClosed()) {
            placeholder.close();
            log.debug("[VaultGlue] Closed placeholder DataSource for '{}'", name);
        }

        return delegating;
    }
```

Note: This requires `VaultGlueDelegatingDataSource` to expose a `getDelegate()` method. Check if it exists; if not, the `DataSourceRotator.rotate()` already replaces the delegate, so the placeholder reference becoming stale is sufficient — just close it unconditionally after register succeeds:

```java
        listener.register(name, delegating, props);

        // Placeholder pool is no longer needed — rotation replaced it with real credentials
        if (!placeholder.isClosed()) {
            placeholder.close();
            log.debug("[VaultGlue] Closed placeholder DataSource for '{}'", name);
        }

        return delegating;
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.dynamic.DynamicLeaseListenerTest" -i`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/database/dynamic/DynamicLeaseListener.java \
       vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGlueDatabaseAutoConfiguration.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/database/dynamic/DynamicLeaseListenerTest.java
git commit -m "fix: DynamicLeaseListener only counts down latch on success, close placeholder DataSource"
```

---

### Task 3: VaultValueBeanPostProcessor — Safe refresh without data loss

**Issue:** `refreshAll()` calls `cache.clear()` then re-fetches. If re-fetch fails, fields retain stale injected values but cache is empty — next refresh sees no previous value. Also not atomic with concurrent reads.

**Files:**
- Modify: `kv/VaultValueBeanPostProcessor.java:84-88`
- Create: `kv/VaultValueBeanPostProcessorTest.java`

- [ ] **Step 1: Write failing test — refresh should keep old values on Vault failure**

```java
package io.vaultglue.kv;

import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultValueBeanPostProcessorTest {

    private VaultKvOperations kvOperations;
    private VaultValueBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        kvOperations = Mockito.mock(VaultKvOperations.class);
        processor = new VaultValueBeanPostProcessor(kvOperations);
    }

    @Test
    void refreshAll_shouldKeepOldValuesOnVaultFailure() {
        // First call succeeds
        Mockito.when(kvOperations.get("app/config"))
                .thenReturn(Map.of("key", "original-value"))
                .thenThrow(new RuntimeException("Vault unavailable"));

        // Simulate initial injection — populate cache
        TestBean bean = new TestBean();
        processor.postProcessAfterInitialization(bean, "testBean");
        assertEquals("original-value", bean.value);

        // Refresh should not lose the old value even if Vault fails
        processor.refreshAll();
        assertEquals("original-value", bean.value);
    }

    static class TestBean {
        @VaultValue(path = "app/config", key = "key", refresh = true)
        String value;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.VaultValueBeanPostProcessorTest.refreshAll_shouldKeepOldValuesOnVaultFailure" -i`
Expected: FAIL — cache.clear() loses old values, then exception during re-fetch leaves field unchanged but cache is empty

- [ ] **Step 3: Fix — replace cache.clear() with swap-on-success pattern**

In `VaultValueBeanPostProcessor.java`, replace `refreshAll()` (lines 84-88):

```java
    public void refreshAll() {
        Map<String, Map<String, Object>> newCache = new ConcurrentHashMap<>();
        refreshableFields.forEach((bean, fields) ->
                fields.forEach((field, annotation) -> {
                    String path = annotation.path();
                    try {
                        Map<String, Object> secrets = newCache.computeIfAbsent(path, kvOperations::get);
                        Object value = secrets.get(annotation.key());

                        if (value == null && !annotation.defaultValue().isEmpty()) {
                            value = annotation.defaultValue();
                        }

                        if (value != null) {
                            ReflectionUtils.makeAccessible(field);
                            ReflectionUtils.setField(field, bean, convertValue(value, field.getType()));
                            log.debug("[VaultGlue] Refreshed @VaultValue: {}.{} from {}/{}",
                                    bean.getClass().getSimpleName(), field.getName(), path, annotation.key());
                        }
                    } catch (Exception e) {
                        log.error("[VaultGlue] Failed to refresh @VaultValue: {}/{}, keeping previous value",
                                path, annotation.key(), e);
                    }
                }));
        // Only replace cache after all fields are processed — old cache remains on failure
        cache.putAll(newCache);
    }
```

Add import at top of file:
```java
import java.util.concurrent.ConcurrentHashMap;
```
(Already imported — no change needed)

- [ ] **Step 4: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.VaultValueBeanPostProcessorTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/kv/VaultValueBeanPostProcessor.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultValueBeanPostProcessorTest.java
git commit -m "fix: VaultValueBeanPostProcessor preserves old values on refresh failure"
```

---

### Task 4: Transit batch operations — NPE guard on missing result key

**Issue:** `extractBatchResults()` at line 258 calls `item.get(key).toString()` which NPEs if the key is missing from a batch result item (no error field, but also no expected key).

**Files:**
- Modify: `transit/DefaultVaultTransitOperations.java:253-259`
- Modify: `transit/DefaultVaultTransitOperationsTest.java`

- [ ] **Step 1: Write failing test — batch result with missing key should throw clear error**

Add to `DefaultVaultTransitOperationsTest.java`:

```java
    @Test
    void encryptBatch_shouldThrowOnMissingResultKey() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "batch_results", List.of(
                        Map.of("unexpected_field", "value")
                )
        ));

        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/encrypt/test-key"),
                Mockito.any()
        )).thenReturn(response);

        DefaultVaultTransitOperations.VaultTransitException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        DefaultVaultTransitOperations.VaultTransitException.class,
                        () -> transitOps.encryptBatch("test-key", List.of("hello")));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("ciphertext"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.DefaultVaultTransitOperationsTest.encryptBatch_shouldThrowOnMissingResultKey" -i`
Expected: FAIL with NullPointerException (not VaultTransitException)

- [ ] **Step 3: Fix — add null check in extractBatchResults**

In `DefaultVaultTransitOperations.java`, replace line 258:
```java
            results.add(item.get(key).toString());
```
with:
```java
            Object value = item.get(key);
            if (value == null) {
                throw new VaultTransitException("Missing '" + key + "' in batch result item: " + item);
            }
            results.add(value.toString());
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.DefaultVaultTransitOperationsTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/DefaultVaultTransitOperations.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/DefaultVaultTransitOperationsTest.java
git commit -m "fix: Transit batch extractBatchResults throws clear error instead of NPE on missing key"
```

---

### Task 5: VaultEncryptConverter — Fix static initialization race condition

**Issue:** `applicationContext` and `defaultKeyName` are written via `initialize()` and read via `getTransitOperations()` with only `volatile` — but JPA may call `convertToEntityAttribute()` before autoconfiguration completes.

**Files:**
- Modify: `transit/VaultEncryptConverter.java:36-42, 90-96`

- [ ] **Step 1: Write failing test — getTransitOperations before initialize should throw**

Add to `VaultEncryptConverterTest.java` (also add `@AfterEach` to restore state for test isolation):

```java
    @AfterEach
    void tearDown() {
        // Restore static state so other tests are not affected
        VaultEncryptConverter.initialize(
                Mockito.mock(ApplicationContext.class), "default-key");
    }

    @Test
    void convertToDatabaseColumn_shouldThrowWhenNotInitialized() {
        // Reset static state
        VaultEncryptConverter.initialize(null, null);
        VaultEncryptConverter uninitConverter = new VaultEncryptConverter();

        assertThrows(IllegalStateException.class,
                () -> uninitConverter.convertToDatabaseColumn("test"));
    }
```

Add import: `import org.junit.jupiter.api.AfterEach;`

- [ ] **Step 2: Run test to verify it passes (existing behavior already throws)**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.VaultEncryptConverterTest.convertToDatabaseColumn_shouldThrowWhenNotInitialized" -i`
Expected: PASS — already throws IllegalStateException

- [ ] **Step 3: Add thread-safe initialization check**

In `VaultEncryptConverter.java`, replace the initialize method and add a lock:

```java
    private static final Object INIT_LOCK = new Object();
    private static volatile ApplicationContext applicationContext;
    private static volatile String defaultKeyName;

    public static void initialize(ApplicationContext context, String keyName) {
        synchronized (INIT_LOCK) {
            applicationContext = context;
            defaultKeyName = keyName;
        }
    }
```

And update `getTransitOperations()`:
```java
    private VaultTransitOperations getTransitOperations() {
        ApplicationContext ctx = applicationContext; // single volatile read
        if (ctx == null) {
            throw new IllegalStateException(
                    "VaultEncryptConverter not initialized. Ensure VaultGlueTransitAutoConfiguration is active.");
        }
        return ctx.getBean(VaultTransitOperations.class);
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.VaultEncryptConverterTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultEncryptConverter.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultEncryptConverterTest.java
git commit -m "fix: VaultEncryptConverter thread-safe initialization with synchronized lock"
```

---

### Task 6: VaultAwsCredentialProvider — NPE guard and STS token validation

**Issue:** (a) `getCredential()` returns null before `start()` is called. (b) STS credential types require `security_token` but it's not validated.

**Files:**
- Modify: `aws/VaultAwsCredentialProvider.java:41-43, 62-69`
- Create: `aws/VaultAwsCredentialProviderTest.java`

- [ ] **Step 1: Write failing tests**

```java
package io.vaultglue.aws;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultAwsCredentialProviderTest {

    private VaultTemplate vaultTemplate;
    private VaultGlueAwsProperties properties;

    @BeforeEach
    void setUp() {
        vaultTemplate = Mockito.mock(VaultTemplate.class);
        properties = new VaultGlueAwsProperties();
        properties.setBackend("aws");
        properties.setRole("test-role");
        properties.setTtl("1h");
    }

    @Test
    void getCredential_shouldThrowBeforeStart() {
        properties.setCredentialType("iam_user");
        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties);

        assertThrows(IllegalStateException.class, provider::getCredential);
    }

    @Test
    void start_shouldValidateSecurityTokenForStsType() {
        properties.setCredentialType("sts");

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "access_key", "AKIA...",
                "secret_key", "secret..."
                // No security_token — should fail for STS
        ));

        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(response);

        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties);

        assertThrows(RuntimeException.class, provider::start);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.aws.VaultAwsCredentialProviderTest" -i`
Expected: FAIL — `getCredential()` returns null, STS without token silently succeeds

- [ ] **Step 3: Fix getCredential and add STS token validation**

In `VaultAwsCredentialProvider.java`:

Replace `getCredential()` (line 41-43):
```java
    public AwsCredential getCredential() {
        AwsCredential cred = currentCredential;
        if (cred == null) {
            throw new IllegalStateException(
                    "[VaultGlue] AWS credential not yet available. Ensure start() has been called.");
        }
        return cred;
    }
```

In `rotate()`, after line 67 (`if (accessKey == null...`) add STS token validation:
```java
            String securityToken = (String) data.get("security_token");
            boolean isStsType = !"iam_user".equals(properties.getCredentialType());
            if (isStsType && (securityToken == null || securityToken.isBlank())) {
                throw new RuntimeException(
                        "[VaultGlue] AWS STS credential response missing security_token for type: "
                        + properties.getCredentialType());
            }

            currentCredential = new AwsCredential(accessKey, secretKey, securityToken);
```

And remove the old line 69: `currentCredential = new AwsCredential(accessKey, secretKey, (String) data.get("security_token"));`

- [ ] **Step 4: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.aws.VaultAwsCredentialProviderTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/aws/VaultAwsCredentialProvider.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/aws/VaultAwsCredentialProviderTest.java
git commit -m "fix: AWS credential provider throws on null credential and validates STS security_token"
```

---

### Task 7: TOTP validate — Distinguish Vault error from invalid code

**Issue:** `validate()` returns `false` for both invalid codes AND Vault connection failures. Security-critical: a Vault outage should not silently deny all OTP validations.

**Files:**
- Modify: `totp/DefaultVaultTotpOperations.java:56-69`
- Create: `totp/DefaultVaultTotpOperationsTest.java`

- [ ] **Step 1: Write failing test — null response should throw, not return false**

```java
package io.vaultglue.totp;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVaultTotpOperationsTest {

    private VaultTemplate vaultTemplate;
    private DefaultVaultTotpOperations totpOps;

    @BeforeEach
    void setUp() {
        vaultTemplate = Mockito.mock(VaultTemplate.class);
        VaultGlueTotpProperties properties = new VaultGlueTotpProperties();
        properties.setBackend("totp");
        totpOps = new DefaultVaultTotpOperations(vaultTemplate, properties);
    }

    @Test
    void validate_shouldThrowOnNullResponse() {
        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> totpOps.validate("test-key", "123456"));
    }

    @Test
    void validate_shouldReturnTrueForValidCode() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("valid", true));
        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(response);

        assertTrue(totpOps.validate("test-key", "123456"));
    }

    @Test
    void validate_shouldReturnFalseForInvalidCode() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("valid", false));
        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(response);

        assertFalse(totpOps.validate("test-key", "000000"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.totp.DefaultVaultTotpOperationsTest.validate_shouldThrowOnNullResponse" -i`
Expected: FAIL — returns false instead of throwing

- [ ] **Step 3: Fix — throw on null/missing response**

In `DefaultVaultTotpOperations.java`, replace `validate()` (lines 56-69):

```java
    @Override
    public boolean validate(String name, String code) {
        String path = properties.getBackend() + "/code/" + name;

        VaultResponse response = vaultTemplate.write(path, Map.of("code", code));
        if (response == null || response.getData() == null) {
            throw new RuntimeException(
                    "[VaultGlue] Failed to validate TOTP code for: " + name
                    + ". Vault returned empty response.");
        }

        Object valid = response.getData().get("valid");
        if (valid instanceof Boolean b) return b;
        if (valid instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.totp.DefaultVaultTotpOperationsTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/totp/DefaultVaultTotpOperations.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/totp/DefaultVaultTotpOperationsTest.java
git commit -m "fix: TOTP validate throws on Vault error instead of returning false"
```

---

### Task 8: TTL parsing — Log warning on unrecognized format instead of silent default

**Issue:** `parseTtlMs()` in AWS and `parseTtl()` in PKI silently return defaults for unrecognized formats like `"1h30m"` or `"1H"`.

**Files:**
- Modify: `aws/VaultAwsCredentialProvider.java:78-89`
- Modify: `aws/VaultAwsCredentialProviderTest.java`

- [ ] **Step 1: Write test — unrecognized TTL should log warning and use default**

Add to `VaultAwsCredentialProviderTest.java`:

```java
    @Test
    void parseTtlMs_shouldHandleCompoundFormat() {
        // Ensure the provider doesn't silently produce wrong values for compound TTLs
        properties.setCredentialType("iam_user");
        properties.setTtl("1h30m"); // unsupported compound format

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "access_key", "AKIA...",
                "secret_key", "secret..."
        ));

        Mockito.when(vaultTemplate.read(Mockito.anyString())).thenReturn(response);

        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties);
        // Should not throw — falls back to default with warning
        provider.start();
        assertNotNull(provider.getCredential());
    }
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.aws.VaultAwsCredentialProviderTest.parseTtlMs_shouldHandleCompoundFormat" -i`
Expected: PASS (current code silently defaults — test is green but behavior is poor)

- [ ] **Step 3: Fix — add logging for unrecognized format**

In `VaultAwsCredentialProvider.java`, replace `parseTtlMs()` (lines 78-89):

```java
    private long parseTtlMs(String ttl) {
        try {
            if (ttl.endsWith("d")) {
                return Long.parseLong(ttl.substring(0, ttl.length() - 1)) * 86_400_000;
            } else if (ttl.endsWith("h")) {
                return Long.parseLong(ttl.substring(0, ttl.length() - 1)) * 3_600_000;
            } else if (ttl.endsWith("m")) {
                return Long.parseLong(ttl.substring(0, ttl.length() - 1)) * 60_000;
            } else if (ttl.endsWith("s")) {
                return Long.parseLong(ttl.substring(0, ttl.length() - 1)) * 1_000;
            }
        } catch (NumberFormatException e) {
            log.warn("[VaultGlue] Failed to parse TTL '{}': {}. Using default 1h.", ttl, e.getMessage());
            return 3_600_000;
        }
        log.warn("[VaultGlue] Unrecognized TTL format '{}'. Supported: <number>[d|h|m|s]. Using default 1h.", ttl);
        return 3_600_000; // default 1h
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.aws.VaultAwsCredentialProviderTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/aws/VaultAwsCredentialProvider.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/aws/VaultAwsCredentialProviderTest.java
git commit -m "fix: TTL parsing logs warning on unrecognized format instead of silent default"
```

---

### Task 9: StaticRefreshScheduler — Prevent concurrent refresh on same DataSource

**Issue:** If a refresh takes longer than the interval, `scheduleAtFixedRate` queues another execution. Two concurrent refreshes on the same DataSource can cause connection pool corruption.

**Files:**
- Modify: `database/static_/StaticRefreshScheduler.java:47-49, 53-76`
- Create: `database/StaticRefreshSchedulerTest.java`

- [ ] **Step 1: Write test — refresh should skip if already running**

```java
package io.vaultglue.database;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.static_.StaticCredentialProvider;
import io.vaultglue.database.static_.StaticRefreshScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticRefreshSchedulerTest {

    @Test
    void schedule_shouldNotRunConcurrentRefreshes() throws InterruptedException {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        StaticCredentialProvider credentialProvider = Mockito.mock(StaticCredentialProvider.class);
        Mockito.when(credentialProvider.getCredential(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> {
                    int current = concurrentCount.incrementAndGet();
                    maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
                    Thread.sleep(200); // simulate slow Vault call
                    concurrentCount.decrementAndGet();
                    return new StaticCredentialProvider.DbCredential("user", "pass");
                });

        DataSourceRotator rotator = Mockito.mock(DataSourceRotator.class);
        FailureStrategyHandler handler = Mockito.mock(FailureStrategyHandler.class);

        StaticRefreshScheduler scheduler = new StaticRefreshScheduler(credentialProvider, rotator, handler);

        DataSourceProperties props = new DataSourceProperties();
        props.setBackend("db");
        props.setRole("test");
        props.setRefreshInterval(50); // very short interval to trigger overlaps

        VaultGlueDelegatingDataSource delegating = Mockito.mock(VaultGlueDelegatingDataSource.class);

        scheduler.schedule("test", delegating, props);
        Thread.sleep(600); // let several intervals fire
        scheduler.shutdown();

        assertEquals(1, maxConcurrent.get(), "Should never have concurrent refreshes on same DataSource");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.StaticRefreshSchedulerTest" -i`
Expected: FAIL — `scheduleAtFixedRate` can overlap

- [ ] **Step 3: Fix — switch to scheduleWithFixedDelay**

In `StaticRefreshScheduler.java`, replace lines 47-50:

```java
        scheduler.scheduleWithFixedDelay(
                () -> refresh(name, delegating, props),
                interval, interval, TimeUnit.MILLISECONDS
        );
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.StaticRefreshSchedulerTest" -i`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/database/static_/StaticRefreshScheduler.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/database/StaticRefreshSchedulerTest.java
git commit -m "fix: StaticRefreshScheduler uses scheduleWithFixedDelay to prevent concurrent refreshes"
```

---

### Task 10: KV Watcher — Apply same scheduleWithFixedDelay fix

**Issue:** Same as Task 9 — `scheduleAtFixedRate` in VaultKvWatcher can queue poll executions if Vault is slow.

**Files:**
- Modify: `kv/VaultKvWatcher.java:38-41`
- Create: `kv/VaultKvWatcherTest.java`

- [ ] **Step 1: Write test — verify watcher uses delay-based scheduling**

```java
package io.vaultglue.kv;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultKvWatcherTest {

    @Test
    void pollChanges_shouldNotOverlapWhenVaultIsSlow() throws InterruptedException {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        VaultKvOperations kvOperations = Mockito.mock(VaultKvOperations.class);
        Mockito.when(kvOperations.get(Mockito.anyString())).thenAnswer(inv -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
            Thread.sleep(150); // simulate slow Vault
            concurrentCount.decrementAndGet();
            return Map.of("key", "value");
        });

        VaultValueBeanPostProcessor beanPostProcessor = Mockito.mock(VaultValueBeanPostProcessor.class);
        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        VaultGlueKvProperties.WatchProperties watch = new VaultGlueKvProperties.WatchProperties();
        watch.setInterval(Duration.ofMillis(50));
        properties.setWatch(watch);

        VaultKvWatcher watcher = new VaultKvWatcher(kvOperations, beanPostProcessor, properties);
        watcher.watch("app/config");
        watcher.start();

        Thread.sleep(500);
        watcher.shutdown();

        assertEquals(1, maxConcurrent.get(), "Should never have concurrent poll executions");
    }
}
```

- [ ] **Step 2: Fix — switch to scheduleWithFixedDelay**

In `VaultKvWatcher.java`, replace lines 38-41:

```java
        scheduler.scheduleWithFixedDelay(
                this::pollChanges,
                intervalMs, intervalMs, TimeUnit.MILLISECONDS
        );
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.VaultKvWatcherTest" -i`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/kv/VaultKvWatcher.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultKvWatcherTest.java
git commit -m "fix: KV watcher uses scheduleWithFixedDelay to prevent poll queue buildup"
```

---

### Task 11: Version bump and final verification

- [ ] **Step 1: Bump version to 0.2.0**

In `build.gradle`, change:
```groovy
version = '0.1.3'
```
to:
```groovy
version = '0.2.0'
```

- [ ] **Step 2: Run full build**

Run: `./gradlew clean build -i`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "chore: bump version to 0.2.0 — critical bugfixes"
```

- [ ] **Step 4: Manual — push and tag (requires human approval)**

```bash
git push origin develop
git tag v0.2.0
git push origin v0.2.0
```

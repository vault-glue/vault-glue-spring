# v0.4.0 Deferred Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 5 deferred items from v0.3.0 code review — batch partial failure, unused annotation fields, converter init timing, extractBoolean inconsistency, and FailureStrategy coverage.

**Architecture:** Each fix is independent. Items 1-2 are breaking changes (return type / annotation API). Items 3-5 are non-breaking behavioral fixes. All changes are in vault-glue-autoconfigure module.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Vault, JUnit 5, Mockito

---

### Task 1: Transit BatchResult Records

**Files:**
- Create: `vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/BatchResultItem.java`
- Create: `vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/BatchResult.java`
- Test: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/BatchResultTest.java`

- [ ] **Step 1: Write failing tests for BatchResultItem and BatchResult**

```java
package io.vaultglue.transit;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchResultTest {

    @Test
    void batchResultItem_success() {
        BatchResultItem<String> item = new BatchResultItem<>(0, "encrypted", null);
        assertTrue(item.isSuccess());
        assertEquals("encrypted", item.value());
        assertNull(item.error());
    }

    @Test
    void batchResultItem_failure() {
        BatchResultItem<String> item = new BatchResultItem<>(1, null, "key not found");
        assertFalse(item.isSuccess());
        assertNull(item.value());
        assertEquals("key not found", item.error());
    }

    @Test
    void batchResult_successes() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, "a", null),
                new BatchResultItem<>(1, null, "error"),
                new BatchResultItem<>(2, "c", null)
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertEquals(List.of("a", "c"), result.successes());
    }

    @Test
    void batchResult_failures() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, "a", null),
                new BatchResultItem<>(1, null, "error")
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertEquals(1, result.failures().size());
        assertEquals("error", result.failures().get(0).error());
    }

    @Test
    void batchResult_hasFailures() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, "a", null)
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertFalse(result.hasFailures());
    }

    @Test
    void batchResult_allFailed() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, null, "err1"),
                new BatchResultItem<>(1, null, "err2")
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertTrue(result.hasFailures());
        assertTrue(result.successes().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.BatchResultTest" --info`
Expected: Compilation error — BatchResultItem and BatchResult don't exist yet.

- [ ] **Step 3: Implement BatchResultItem record**

```java
package io.vaultglue.transit;

/**
 * Represents a single item result in a batch transit operation.
 */
public record BatchResultItem<T>(int index, T value, String error) {

    public boolean isSuccess() {
        return error == null;
    }
}
```

- [ ] **Step 4: Implement BatchResult record**

```java
package io.vaultglue.transit;

import java.util.List;

/**
 * Contains results from a batch transit operation, supporting partial success.
 */
public record BatchResult<T>(List<BatchResultItem<T>> items) {

    public List<T> successes() {
        return items.stream()
                .filter(BatchResultItem::isSuccess)
                .map(BatchResultItem::value)
                .toList();
    }

    public List<BatchResultItem<T>> failures() {
        return items.stream()
                .filter(item -> !item.isSuccess())
                .toList();
    }

    public boolean hasFailures() {
        return items.stream().anyMatch(item -> !item.isSuccess());
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.BatchResultTest" --info`
Expected: All 6 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/BatchResultItem.java \
       vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/BatchResult.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/BatchResultTest.java
git commit -m "feat: add BatchResult and BatchResultItem records for partial batch success"
```

---

### Task 2: Update Transit Batch Methods to Return BatchResult

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultTransitOperations.java` (lines 17-19, 23)
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/DefaultVaultTransitOperations.java` (lines 75-130, 252-274)
- Modify: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/DefaultVaultTransitOperationsTest.java`

- [ ] **Step 1: Write failing test for batch partial failure**

Add to `DefaultVaultTransitOperationsTest.java`:

```java
@Test
void encryptBatch_shouldReturnPartialResults() {
    // Simulate Vault response with mixed success/error
    List<Map<String, Object>> batchResults = List.of(
            Map.of("ciphertext", "vault:v1:abc"),
            Map.of("error", "key not found"),
            Map.of("ciphertext", "vault:v1:def")
    );
    Map<String, Object> data = Map.of("batch_results", batchResults);

    VaultResponse response = new VaultResponse();
    response.setData(data);

    when(transitOperations.write(eq("transit/encrypt/test-key"), any()))
            .thenReturn(response);

    BatchResult<String> result = operations.encryptBatch("test-key", List.of("a", "b", "c"));

    assertEquals(2, result.successes().size());
    assertEquals(1, result.failures().size());
    assertEquals("vault:v1:abc", result.successes().get(0));
    assertEquals("vault:v1:def", result.successes().get(1));
    assertEquals("key not found", result.failures().get(0).error());
    assertEquals(1, result.failures().get(0).index());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.DefaultVaultTransitOperationsTest.encryptBatch_shouldReturnPartialResults" --info`
Expected: Compilation error — `encryptBatch` still returns `List<String>`.

- [ ] **Step 3: Update VaultTransitOperations interface**

Change the three batch method signatures:

```java
// Before
List<String> encryptBatch(String keyName, List<String> plaintexts);
List<String> decryptBatch(String keyName, List<String> ciphertexts);
List<String> rewrapBatch(String keyName, List<String> ciphertexts);

// After
BatchResult<String> encryptBatch(String keyName, List<String> plaintexts);
BatchResult<String> decryptBatch(String keyName, List<String> ciphertexts);
BatchResult<String> rewrapBatch(String keyName, List<String> ciphertexts);
```

- [ ] **Step 4: Update extractBatchResults() in DefaultVaultTransitOperations**

Replace the existing `extractBatchResults()` method (lines 252-274):

```java
private BatchResult<String> extractBatchResults(VaultResponse response, String key) {
    if (response == null || response.getData() == null) {
        throw new VaultTransitException("Empty response from Vault transit batch operation");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> batchResults =
            (List<Map<String, Object>>) response.getData().get("batch_results");

    if (batchResults == null) {
        throw new VaultTransitException("Missing 'batch_results' in transit response");
    }

    List<BatchResultItem<String>> items = new java.util.ArrayList<>();
    for (int i = 0; i < batchResults.size(); i++) {
        Map<String, Object> item = batchResults.get(i);
        if (item.containsKey("error")) {
            items.add(new BatchResultItem<>(i, null, item.get("error").toString()));
        } else {
            Object value = item.get(key);
            if (value == null) {
                items.add(new BatchResultItem<>(i, null,
                        "Missing '" + key + "' in batch result item"));
            } else {
                items.add(new BatchResultItem<>(i, value.toString(), null));
            }
        }
    }
    return new BatchResult<>(items);
}
```

- [ ] **Step 5: Update encryptBatch() method**

Change return type and call (lines 75-86):

```java
@Override
public BatchResult<String> encryptBatch(String keyName, List<String> plaintexts) {
    List<Map<String, String>> batchInput = plaintexts.stream()
            .map(p -> Map.of("plaintext", Base64.getEncoder().encodeToString(p.getBytes())))
            .toList();

    VaultResponse response = transitOperations.write(
            backend + "/encrypt/" + keyName,
            Map.of("batch_input", batchInput));

    return extractBatchResults(response, "ciphertext");
}
```

- [ ] **Step 6: Update decryptBatch() method**

Change return type and handle Base64 decoding per item (lines 89-107):

```java
@Override
public BatchResult<String> decryptBatch(String keyName, List<String> ciphertexts) {
    List<Map<String, String>> batchInput = ciphertexts.stream()
            .map(c -> Map.of("ciphertext", c))
            .toList();

    VaultResponse response = transitOperations.write(
            backend + "/decrypt/" + keyName,
            Map.of("batch_input", batchInput));

    BatchResult<String> rawResult = extractBatchResults(response, "plaintext");

    // Decode Base64 for successful items
    List<BatchResultItem<String>> decoded = rawResult.items().stream()
            .map(item -> {
                if (item.isSuccess()) {
                    String plain = new String(Base64.getDecoder().decode(item.value()));
                    return new BatchResultItem<String>(item.index(), plain, null);
                }
                return item;
            })
            .toList();
    return new BatchResult<>(decoded);
}
```

- [ ] **Step 7: Update rewrapBatch() method**

Change return type (lines 120-130):

```java
@Override
public BatchResult<String> rewrapBatch(String keyName, List<String> ciphertexts) {
    List<Map<String, String>> batchInput = ciphertexts.stream()
            .map(c -> Map.of("ciphertext", c))
            .toList();

    VaultResponse response = transitOperations.write(
            backend + "/rewrap/" + keyName,
            Map.of("batch_input", batchInput));

    return extractBatchResults(response, "ciphertext");
}
```

- [ ] **Step 8: Update existing batch tests to use BatchResult**

In `DefaultVaultTransitOperationsTest.java`, update `encryptBatch_shouldReturnCiphertexts` and `encryptBatch_shouldThrowOnMissingResultKey`:

For `encryptBatch_shouldReturnCiphertexts`: change assertions from `List<String>` to `BatchResult<String>`:
```java
BatchResult<String> result = operations.encryptBatch("test-key", List.of("hello", "world"));
assertEquals(2, result.successes().size());
assertEquals("vault:v1:abc", result.successes().get(0));
assertEquals("vault:v1:def", result.successes().get(1));
assertFalse(result.hasFailures());
```

For `encryptBatch_shouldThrowOnMissingResultKey`: missing key items now become failures instead of exceptions:
```java
BatchResult<String> result = operations.encryptBatch("test-key", List.of("hello"));
assertTrue(result.hasFailures());
assertEquals("Missing 'ciphertext' in batch result item", result.failures().get(0).error());
```

- [ ] **Step 9: Add import for java.util.Base64 and ArrayList if needed**

Ensure `DefaultVaultTransitOperations.java` has:
```java
import java.util.ArrayList;
import java.util.Base64;
```
(Base64 should already be imported; ArrayList may be new.)

- [ ] **Step 10: Run all transit tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.*" --info`
Expected: All tests PASS.

- [ ] **Step 11: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultTransitOperations.java \
       vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/DefaultVaultTransitOperations.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/DefaultVaultTransitOperationsTest.java
git commit -m "feat(transit): return BatchResult from batch methods for partial failure support

BREAKING CHANGE: encryptBatch/decryptBatch/rewrapBatch now return BatchResult<String> instead of List<String>"
```

---

### Task 3: Remove @VaultEncrypt key/context Fields

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultEncrypt.java`
- Modify: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultEncryptConverterTest.java` (if annotation is used with arguments in tests)

- [ ] **Step 1: Check if any test or code uses @VaultEncrypt with arguments**

Run: `grep -rn "VaultEncrypt(" vault-glue-autoconfigure/src/`
Fix any usages found — remove the arguments.

- [ ] **Step 2: Remove key() and context() from VaultEncrypt**

Replace the full annotation file:

```java
package io.vaultglue.transit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation indicating a field should be encrypted via Vault Transit.
 * Use with @Convert(converter = VaultEncryptConverter.class).
 * Encryption uses the configured default transit key.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VaultEncrypt {
}
```

- [ ] **Step 3: Run full build to verify no compile errors**

Run: `./gradlew :vault-glue-autoconfigure:compileJava :vault-glue-autoconfigure:compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests**

Run: `./gradlew :vault-glue-autoconfigure:test --info`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultEncrypt.java
git commit -m "feat(transit): remove unused key/context fields from @VaultEncrypt

BREAKING CHANGE: @VaultEncrypt no longer accepts key() or context() arguments"
```

---

### Task 4: VaultEncryptConverter Lazy Initialization

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultEncryptConverter.java`
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGlueTransitAutoConfiguration.java`
- Modify: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultEncryptConverterTest.java`

- [ ] **Step 1: Write failing test for lazy initialization**

Add to `VaultEncryptConverterTest.java`:

```java
@Test
void convertToDatabaseColumn_shouldLazyResolveOperations() {
    // Reset any prior static state
    VaultEncryptConverter.reset();

    // Set up ApplicationContext with lazy lookup
    ApplicationContext context = mock(ApplicationContext.class);
    DefaultVaultTransitOperations ops = mock(DefaultVaultTransitOperations.class);
    when(context.getBean(VaultTransitOperations.class)).thenReturn(ops);
    when(ops.encrypt("default-key", "plaintext")).thenReturn("vault:v1:encrypted");

    VaultEncryptConverter.setApplicationContext(context);
    VaultEncryptConverter.setDefaultKeyName("default-key");

    VaultEncryptConverter converter = new VaultEncryptConverter();
    // First call triggers lazy lookup
    String result = converter.convertToDatabaseColumn("plaintext");

    assertEquals("vg:default-key:vault:v1:encrypted", result);
    verify(context).getBean(VaultTransitOperations.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.VaultEncryptConverterTest.convertToDatabaseColumn_shouldLazyResolveOperations" --info`
Expected: FAIL — `setApplicationContext` / `setDefaultKeyName` methods don't exist yet.

- [ ] **Step 3: Refactor VaultEncryptConverter to lazy initialization**

Replace the static initialization approach. Key changes:
- Remove `initialize(ApplicationContext, String, boolean)` method
- Remove `initialize(ApplicationContext, String)` method
- Add `setApplicationContext(ApplicationContext)` and `setDefaultKeyName(String)` static setters
- Add `setAllowPlaintextRead(boolean)` static setter
- Change `getOperations()` to lazily look up `VaultTransitOperations` from the context on first call
- Keep `INIT_LOCK`, `volatile` fields, and `reset()` for test cleanup

```java
package io.vaultglue.transit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * JPA AttributeConverter that encrypts/decrypts via Vault Transit.
 * Dependencies are resolved lazily on first use, avoiding init-order issues with JPA.
 */
@Converter
public class VaultEncryptConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(VaultEncryptConverter.class);
    private static final String VG_PREFIX = "vg:";

    private static final Object INIT_LOCK = new Object();
    private static volatile ApplicationContext applicationContext;
    private static volatile String defaultKeyName;
    private static volatile boolean allowPlaintextRead = false;
    private static volatile VaultTransitOperations cachedOperations;

    // ─── Static Configuration ───

    public static void setApplicationContext(ApplicationContext context) {
        synchronized (INIT_LOCK) {
            applicationContext = context;
            cachedOperations = null;
        }
    }

    public static void setDefaultKeyName(String keyName) {
        synchronized (INIT_LOCK) {
            defaultKeyName = keyName;
        }
    }

    public static void setAllowPlaintextRead(boolean allow) {
        synchronized (INIT_LOCK) {
            allowPlaintextRead = allow;
        }
    }

    public static void reset() {
        synchronized (INIT_LOCK) {
            applicationContext = null;
            defaultKeyName = null;
            allowPlaintextRead = false;
            cachedOperations = null;
        }
    }

    // ─── Lazy Operations Lookup ───

    private VaultTransitOperations getOperations() {
        VaultTransitOperations ops = cachedOperations;
        if (ops != null) {
            return ops;
        }
        synchronized (INIT_LOCK) {
            if (cachedOperations != null) {
                return cachedOperations;
            }
            ApplicationContext ctx = applicationContext;
            if (ctx == null) {
                throw new IllegalStateException(
                        "VaultEncryptConverter not initialized. "
                        + "Ensure VaultGlueTransitAutoConfiguration is active.");
            }
            cachedOperations = ctx.getBean(VaultTransitOperations.class);
            return cachedOperations;
        }
    }

    // ─── AttributeConverter ───

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        String keyName = defaultKeyName;
        String encrypted = getOperations().encrypt(keyName, attribute);
        return VG_PREFIX + keyName + ":" + encrypted;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        // New VG format: vg:{keyName}:vault:v1:xxx
        if (dbData.startsWith(VG_PREFIX)) {
            String withoutPrefix = dbData.substring(VG_PREFIX.length());
            int colonIndex = withoutPrefix.indexOf(':');
            if (colonIndex > 0) {
                String keyName = withoutPrefix.substring(0, colonIndex);
                String ciphertext = withoutPrefix.substring(colonIndex + 1);
                return getOperations().decrypt(keyName, ciphertext);
            }
        }

        // Legacy format: vault:v1:xxx
        if (dbData.startsWith("vault:")) {
            return getOperations().decrypt(defaultKeyName, dbData);
        }

        // Plaintext fallback
        if (allowPlaintextRead) {
            log.warn("[VaultGlue] Reading plaintext value — enable encryption for this field");
            return dbData;
        }

        throw new IllegalStateException(
                "VaultEncryptConverter: unrecognized format and plaintext read is disabled");
    }
}
```

- [ ] **Step 4: Update VaultGlueTransitAutoConfiguration — remove initializer, use setters**

Replace the `VaultEncryptConverterInitializer` approach:

```java
@Bean
public VaultEncryptConverterInitializer vaultEncryptConverterInitializer(
        ApplicationContext applicationContext,
        VaultGlueTransitProperties properties) {
    return new VaultEncryptConverterInitializer(applicationContext, properties);
}
```

With direct setter calls in a simpler bean:

```java
@Bean
public VaultEncryptConverterConfigurer vaultEncryptConverterConfigurer(
        ApplicationContext applicationContext,
        VaultGlueTransitProperties properties) {

    String defaultKey = properties.getDefaultKey();
    if (defaultKey == null || defaultKey.isBlank()) {
        Map<String, VaultGlueTransitProperties.KeyProperties> keys = properties.getKeys();
        if (keys != null && !keys.isEmpty()) {
            defaultKey = keys.keySet().iterator().next();
        }
    }

    VaultEncryptConverter.setApplicationContext(applicationContext);
    if (defaultKey != null) {
        VaultEncryptConverter.setDefaultKeyName(defaultKey);
    }
    VaultEncryptConverter.setAllowPlaintextRead(properties.isAllowPlaintextRead());

    return new VaultEncryptConverterConfigurer();
}
```

Add the configurer class (can be an inner static class):

```java
static class VaultEncryptConverterConfigurer implements DisposableBean {
    @Override
    public void destroy() {
        VaultEncryptConverter.reset();
    }
}
```

Remove the old `VaultEncryptConverterInitializer` inner class entirely.

- [ ] **Step 5: Update existing VaultEncryptConverterTest**

Update the test setup to use the new setter methods instead of `initialize()`:
- Replace `VaultEncryptConverter.initialize(context, "test-key", false)` with:
  ```java
  VaultEncryptConverter.setApplicationContext(context);
  VaultEncryptConverter.setDefaultKeyName("test-key");
  VaultEncryptConverter.setAllowPlaintextRead(false);
  ```
- Keep `VaultEncryptConverter.reset()` in `@AfterEach`.

- [ ] **Step 6: Run all tests**

Run: `./gradlew :vault-glue-autoconfigure:test --info`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/VaultEncryptConverter.java \
       vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGlueTransitAutoConfiguration.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultEncryptConverterTest.java
git commit -m "fix(transit): lazy-init VaultEncryptConverter to avoid JPA ordering issues"
```

---

### Task 5: Fix extractBoolean() Consistency

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/DefaultVaultTransitOperations.java` (lines 244-249)
- Modify: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/DefaultVaultTransitOperationsTest.java`

- [ ] **Step 1: Write failing test for extractBoolean null response**

Add to `DefaultVaultTransitOperationsTest.java`:

```java
@Test
void verifyHmac_shouldThrowOnNullResponse() {
    when(transitOperations.write(eq("transit/verify/test-key"), any()))
            .thenReturn(null);

    assertThrows(VaultTransitException.class,
            () -> operations.verifyHmac("test-key", "data", "hmac"));
}

@Test
void verifyHmac_shouldThrowOnEmptyData() {
    VaultResponse response = new VaultResponse();
    response.setData(null);

    when(transitOperations.write(eq("transit/verify/test-key"), any()))
            .thenReturn(response);

    assertThrows(VaultTransitException.class,
            () -> operations.verifyHmac("test-key", "data", "hmac"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.DefaultVaultTransitOperationsTest.verifyHmac_shouldThrowOnNullResponse" --info`
Expected: FAIL — currently returns `false` instead of throwing.

- [ ] **Step 3: Fix extractBoolean()**

Replace the method (lines 244-249):

```java
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

- [ ] **Step 4: Run all transit tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.*" --info`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/transit/DefaultVaultTransitOperations.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/DefaultVaultTransitOperationsTest.java
git commit -m "fix(transit): extractBoolean throws on null response instead of silent false"
```

---

### Task 6: FailureStrategy in PKI Engine

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/pki/CertificateRenewalScheduler.java`
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGluePkiAutoConfiguration.java`
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/pki/CertificateRenewalSchedulerTest.java`

- [ ] **Step 1: Write failing test for FailureStrategy in PKI**

```java
package io.vaultglue.pki;

import io.vaultglue.core.FailureStrategyHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateRenewalSchedulerTest {

    @Mock
    private VaultPkiOperations pkiOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FailureStrategyHandler failureStrategyHandler;

    @Test
    void checkAndRenew_shouldCallFailureStrategyOnError() {
        // Create scheduler that will fail on renewal check
        when(pkiOperations.isExpiringSoon(any(), any())).thenThrow(
                new RuntimeException("Vault connection refused"));

        CertificateRenewalScheduler scheduler = new CertificateRenewalScheduler(
                pkiOperations, eventPublisher, failureStrategyHandler,
                "pki", "my-role", "app.example.com", "72h", 3600000, 24);

        scheduler.checkAndRenew();

        verify(failureStrategyHandler).handle(eq("PKI certificate renewal"), any(), any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.pki.CertificateRenewalSchedulerTest" --info`
Expected: Compilation error — constructor doesn't accept FailureStrategyHandler yet.

- [ ] **Step 3: Add FailureStrategyHandler to CertificateRenewalScheduler**

Add field and constructor parameter:

```java
// Add to imports
import io.vaultglue.core.FailureStrategyHandler;

// Add field (after existing fields, before constructor)
private final FailureStrategyHandler failureStrategyHandler;
```

Update constructor to accept `FailureStrategyHandler` as a new parameter (add after `eventPublisher`):

```java
public CertificateRenewalScheduler(VaultPkiOperations pkiOperations,
                                    ApplicationEventPublisher eventPublisher,
                                    FailureStrategyHandler failureStrategyHandler,
                                    String backend, String role, String commonName,
                                    String ttl, long checkInterval, int renewThresholdHours) {
    // existing assignments...
    this.failureStrategyHandler = failureStrategyHandler;
}
```

Update `checkAndRenew()` catch block — replace the existing log-only error handling:

```java
// Before (in catch block):
log.error("[VaultGlue] PKI certificate renewal failed", e);

// After:
log.error("[VaultGlue] PKI certificate renewal failed", e);
failureStrategyHandler.handle("PKI certificate renewal", e, () -> {
    checkAndRenew();
    return null;
});
```

- [ ] **Step 4: Update VaultGluePkiAutoConfiguration**

Add `FailureStrategyHandler` parameter to the `certificateRenewalScheduler` bean method:

```java
@Bean(initMethod = "start", destroyMethod = "shutdown")
@ConditionalOnProperty(prefix = "vault-glue.pki", name = "auto-renew", matchIfMissing = true)
public CertificateRenewalScheduler certificateRenewalScheduler(
        VaultPkiOperations pkiOperations,
        ApplicationEventPublisher eventPublisher,
        FailureStrategyHandler failureStrategyHandler,
        VaultGluePkiProperties properties) {
    return new CertificateRenewalScheduler(
            pkiOperations, eventPublisher, failureStrategyHandler,
            properties.getBackend(), properties.getRole(),
            properties.getCommonName(), properties.getTtl(),
            properties.getCheckInterval(), properties.getRenewThresholdHours());
}
```

Add import for `FailureStrategyHandler`.

- [ ] **Step 5: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.pki.*" --info`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/pki/CertificateRenewalScheduler.java \
       vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGluePkiAutoConfiguration.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/pki/CertificateRenewalSchedulerTest.java
git commit -m "fix(pki): apply FailureStrategy to certificate renewal failures"
```

---

### Task 7: FailureStrategy in KV Engine

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/kv/VaultKvWatcher.java`
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGlueKvAutoConfiguration.java`
- Modify: `vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultKvWatcherTest.java`

- [ ] **Step 1: Write failing test for FailureStrategy in KV watcher**

Add to `VaultKvWatcherTest.java`:

```java
@Test
void pollChanges_shouldCallFailureStrategyOnError() {
    VaultKvOperations kvOperations = mock(VaultKvOperations.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    FailureStrategyHandler failureStrategyHandler = mock(FailureStrategyHandler.class);
    VaultValueBeanPostProcessor beanPostProcessor = mock(VaultValueBeanPostProcessor.class);

    VaultKvWatcher watcher = new VaultKvWatcher(
            kvOperations, eventPublisher, failureStrategyHandler, 30000);
    watcher.setBeanPostProcessor(beanPostProcessor);

    // Register a path to watch
    watcher.watch("app/config");

    // Make kvOperations throw on next poll
    when(kvOperations.get("app/config")).thenThrow(new RuntimeException("Vault unavailable"));

    watcher.pollChanges();

    verify(failureStrategyHandler).handle(eq("KV watch polling"), any(), any());
}
```

Add import for `FailureStrategyHandler`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.VaultKvWatcherTest.pollChanges_shouldCallFailureStrategyOnError" --info`
Expected: Compilation error — constructor doesn't accept FailureStrategyHandler.

- [ ] **Step 3: Add FailureStrategyHandler to VaultKvWatcher**

Add field and update constructor:

```java
// Add import
import io.vaultglue.core.FailureStrategyHandler;

// Add field
private final FailureStrategyHandler failureStrategyHandler;

// Update constructor
public VaultKvWatcher(VaultKvOperations kvOperations,
                      ApplicationEventPublisher eventPublisher,
                      FailureStrategyHandler failureStrategyHandler,
                      long interval) {
    // existing assignments...
    this.failureStrategyHandler = failureStrategyHandler;
}
```

Update `pollChanges()` catch block:

```java
// Before:
log.error("[VaultGlue] KV watch polling failed", e);

// After:
log.error("[VaultGlue] KV watch polling failed", e);
failureStrategyHandler.handle("KV watch polling", e, () -> {
    pollChanges();
    return null;
});
```

- [ ] **Step 4: Update VaultGlueKvAutoConfiguration**

Add `FailureStrategyHandler` parameter to the watcher bean:

```java
@Bean(initMethod = "start", destroyMethod = "shutdown")
@ConditionalOnProperty(prefix = "vault-glue.kv.watch", name = "enabled", havingValue = "true")
public VaultKvWatcher vaultKvWatcher(
        VaultKvOperations kvOperations,
        ApplicationEventPublisher eventPublisher,
        FailureStrategyHandler failureStrategyHandler,
        VaultValueBeanPostProcessor beanPostProcessor,
        VaultGlueKvProperties properties) {
    VaultKvWatcher watcher = new VaultKvWatcher(
            kvOperations, eventPublisher, failureStrategyHandler,
            properties.getWatch().getInterval());
    watcher.setBeanPostProcessor(beanPostProcessor);
    return watcher;
}
```

Add import for `FailureStrategyHandler`.

- [ ] **Step 5: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.*" --info`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/kv/VaultKvWatcher.java \
       vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGlueKvAutoConfiguration.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultKvWatcherTest.java
git commit -m "fix(kv): apply FailureStrategy to watch polling failures"
```

---

### Task 8: FailureStrategy in AWS Engine

**Files:**
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/aws/VaultAwsCredentialProvider.java`
- Modify: `vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGlueAwsAutoConfiguration.java`
- Modify: `vault-glue-autoconfigure/src/test/java/io/vaultglue/aws/VaultAwsCredentialProviderTest.java`

- [ ] **Step 1: Write failing test for FailureStrategy in AWS**

Add to `VaultAwsCredentialProviderTest.java`:

```java
@Test
void rotate_shouldCallFailureStrategyOnError() {
    VaultOperations vaultOperations = mock(VaultOperations.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    FailureStrategyHandler failureStrategyHandler = mock(FailureStrategyHandler.class);

    when(vaultOperations.read(any())).thenThrow(new RuntimeException("Vault unavailable"));

    VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(
            vaultOperations, eventPublisher, failureStrategyHandler,
            "aws", "my-role", "iam_user", "1h");

    // rotate() is called by start(), which will fail
    try {
        provider.start();
    } catch (Exception ignored) {
        // start() may propagate
    }

    verify(failureStrategyHandler).handle(eq("AWS credential rotation"), any(), any());
}
```

Add import for `FailureStrategyHandler`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.aws.VaultAwsCredentialProviderTest.rotate_shouldCallFailureStrategyOnError" --info`
Expected: Compilation error — constructor doesn't accept FailureStrategyHandler.

- [ ] **Step 3: Add FailureStrategyHandler to VaultAwsCredentialProvider**

Add field and update constructor:

```java
// Add import
import io.vaultglue.core.FailureStrategyHandler;

// Add field
private final FailureStrategyHandler failureStrategyHandler;

// Update constructor to accept FailureStrategyHandler (add after eventPublisher)
public VaultAwsCredentialProvider(VaultOperations vaultOperations,
                                   ApplicationEventPublisher eventPublisher,
                                   FailureStrategyHandler failureStrategyHandler,
                                   String backend, String role,
                                   String credentialType, String ttl) {
    // existing assignments...
    this.failureStrategyHandler = failureStrategyHandler;
}
```

Update `rotate()` catch block:

```java
// Before:
log.error("[VaultGlue] AWS credential rotation failed", e);

// After:
log.error("[VaultGlue] AWS credential rotation failed", e);
failureStrategyHandler.handle("AWS credential rotation", e, () -> {
    rotate();
    return null;
});
```

- [ ] **Step 4: Update VaultGlueAwsAutoConfiguration**

Add `FailureStrategyHandler` parameter:

```java
@Bean(initMethod = "start", destroyMethod = "shutdown")
public VaultAwsCredentialProvider vaultAwsCredentialProvider(
        VaultOperations vaultOperations,
        ApplicationEventPublisher eventPublisher,
        FailureStrategyHandler failureStrategyHandler,
        VaultGlueAwsProperties properties) {
    return new VaultAwsCredentialProvider(
            vaultOperations, eventPublisher, failureStrategyHandler,
            properties.getBackend(), properties.getRole(),
            properties.getCredentialType(), properties.getTtl());
}
```

Add import for `FailureStrategyHandler`.

- [ ] **Step 5: Update existing AWS tests**

Update any existing tests that construct `VaultAwsCredentialProvider` to include the new `FailureStrategyHandler` parameter:

```java
FailureStrategyHandler failureStrategyHandler = mock(FailureStrategyHandler.class);
// Add failureStrategyHandler as third constructor argument
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.aws.*" --info`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add vault-glue-autoconfigure/src/main/java/io/vaultglue/aws/VaultAwsCredentialProvider.java \
       vault-glue-autoconfigure/src/main/java/io/vaultglue/autoconfigure/VaultGlueAwsAutoConfiguration.java \
       vault-glue-autoconfigure/src/test/java/io/vaultglue/aws/VaultAwsCredentialProviderTest.java
git commit -m "fix(aws): apply FailureStrategy to credential rotation failures"
```

---

### Task 9: Full Build Verification and Version Bump

**Files:**
- Modify: `build.gradle` or `gradle.properties` (version bump to 0.4.0)
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Run full build**

Run: `./gradlew clean build --info`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Bump version to 0.4.0**

Update the version property in the root build file from `0.3.0` to `0.4.0`.

- [ ] **Step 3: Update CHANGELOG.md**

Add v0.4.0 section at the top:

```markdown
## [0.4.0] - 2026-03-31

### Breaking Changes
- Transit batch methods (`encryptBatch`, `decryptBatch`, `rewrapBatch`) now return `BatchResult<String>` instead of `List<String>` — supports partial failure handling
- `@VaultEncrypt` annotation no longer accepts `key()` or `context()` arguments — these fields were never used by the converter

### Fixed
- `VaultEncryptConverter` now uses lazy initialization, resolving JPA bean ordering issues
- `extractBoolean()` in Transit engine now throws `VaultTransitException` on null/empty responses instead of silently returning `false`
- `FailureStrategy` (restart/retry/ignore) now applied to PKI certificate renewal, KV watch polling, and AWS credential rotation — previously only Database engine used it
```

- [ ] **Step 4: Run full build again**

Run: `./gradlew clean build --info`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add build.gradle CHANGELOG.md
git commit -m "chore: bump version to 0.4.0, update CHANGELOG"
```

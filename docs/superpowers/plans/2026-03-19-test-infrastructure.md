# VaultGlue Test Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build 42 test cases covering KV, Database, and Transit engines with TestContainers infrastructure.

**Architecture:** Tests in `vault-glue-autoconfigure/src/test/` using singleton TestContainers (Vault + MySQL + PostgreSQL). Unit tests mock VaultTemplate. E2E tests hit real Vault. Common container setup in `support/` package, later extracted to `vault-glue-test` module.

**Tech Stack:** JUnit 5, Mockito, TestContainers (Vault 1.17, MySQL 8.0, PostgreSQL 16), Awaitility, ApplicationContextRunner, Spring Boot Test

---

## File Map

### New Files — Support

| File | Responsibility |
|---|---|
| `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/VaultContainerSupport.java` | Singleton Vault container, engine initialization |
| `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/MySqlContainerSupport.java` | Singleton MySQL container |
| `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/PostgresContainerSupport.java` | Singleton PostgreSQL container |
| `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/VaultInitializer.java` | Vault HTTP API calls for engine/role/key setup |

### New Files — Unit Tests

| File | Responsibility |
|---|---|
| `src/test/.../core/FailureStrategyHandlerTest.java` | 4 cases: RETRY, RESTART, IGNORE, RETRY→RESTART fallback |
| `src/test/.../core/VaultGlueEventPublisherTest.java` | 1 case: event delivery |
| `src/test/.../kv/DefaultVaultKvOperationsTest.java` | 3 cases: v2 get/put, non-existent path, v1 UnsupportedOp |
| `src/test/.../database/VaultGlueDelegatingDataSourceTest.java` | 1 case: thread-safe delegate swap |
| `src/test/.../database/DataSourceRotatorTest.java` | 1 case: rotation flow + event |
| `src/test/.../database/GracefulShutdownTest.java` | 1 case: wait loop |
| `src/test/.../database/VaultGlueDataSourcesTest.java` | 1 case: unknown name throws |
| `src/test/.../transit/DefaultVaultTransitOperationsTest.java` | 3 cases: encrypt/decrypt mock, converter, batch |

### New Files — E2E Tests

| File | Responsibility |
|---|---|
| `src/test/.../kv/VaultKvOperationsE2ETest.java` | 6 cases: CRUD, version, @VaultValue, watch |
| `src/test/.../transit/VaultTransitOperationsE2ETest.java` | 6 cases: encrypt, rewrap, hmac, sign, converter, auto-key |
| `src/test/.../database/StaticRoleMySqlE2ETest.java` | 2 cases: credential + rotation |
| `src/test/.../database/StaticRolePostgresE2ETest.java` | 2 cases: credential + rotation |
| `src/test/.../database/DynamicRoleMySqlE2ETest.java` | 1 case: lease-based credential |
| `src/test/.../database/DynamicRolePostgresE2ETest.java` | 1 case: lease-based credential |
| `src/test/.../database/MultiDataSourceE2ETest.java` | 1 case: primary + replica |

### New Files — Integration Tests

| File | Responsibility |
|---|---|
| `src/test/.../kv/VaultGlueKvAutoConfigurationTest.java` | KV beans registered |
| `src/test/.../transit/VaultGlueTransitAutoConfigurationTest.java` | Transit beans registered |
| `src/test/.../database/VaultGlueDatabaseAutoConfigurationTest.java` | DB beans registered |
| `src/test/.../autoconfigure/DisabledAutoConfigurationTest.java` | No beans when disabled |

### Modified Files

| File | Change |
|---|---|
| `vault-glue-autoconfigure/build.gradle` | Add test dependencies |

All test file paths below are relative to: `vault-glue-autoconfigure/src/test/java/io/vaultglue/`

---

## Task 1: Add Test Dependencies

**Files:**
- Modify: `vault-glue-autoconfigure/build.gradle`

- [ ] **Step 1: Add test dependencies to build.gradle**

```groovy
dependencies {
    // Core
    implementation 'org.springframework.boot:spring-boot-autoconfigure'
    implementation 'org.springframework.cloud:spring-cloud-vault-config'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // Optional - engine-specific
    compileOnly 'com.zaxxer:HikariCP'
    compileOnly 'org.springframework.boot:spring-boot-starter-actuator'
    compileOnly 'org.springframework.boot:spring-boot-starter-data-jpa'
    compileOnly 'software.amazon.awssdk:auth:2.29.45'
    compileOnly 'org.springframework:spring-jdbc'

    // Logging
    compileOnly 'org.slf4j:slf4j-api'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.vault:spring-vault-core'
    testImplementation 'org.testcontainers:vault:1.20.4'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:mysql'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'com.mysql:mysql-connector-j'
    testImplementation 'org.postgresql:postgresql'
    testImplementation 'com.zaxxer:HikariCP'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    testImplementation 'org.awaitility:awaitility'
    testImplementation 'com.h2database:h2'
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :vault-glue-autoconfigure:compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/build.gradle
git commit -m "test: add test dependencies for TestContainers, Awaitility, DB drivers"
```

---

## Task 2: Vault Container Support

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/VaultContainerSupport.java`

- [ ] **Step 1: Create VaultContainerSupport**

```java
package io.vaultglue.support;

import org.testcontainers.vault.VaultContainer;

public final class VaultContainerSupport {

    public static final String VAULT_TOKEN = "test-root-token";
    public static final String VAULT_IMAGE = "hashicorp/vault:1.17";

    private static final VaultContainer<?> VAULT = new VaultContainer<>(VAULT_IMAGE)
            .withVaultToken(VAULT_TOKEN);

    static {
        VAULT.start();
    }

    private VaultContainerSupport() {
    }

    public static VaultContainer<?> getContainer() {
        return VAULT;
    }

    public static String getAddress() {
        return "http://" + VAULT.getHost() + ":" + VAULT.getFirstMappedPort();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :vault-glue-autoconfigure:compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/support/VaultContainerSupport.java
git commit -m "test: add singleton Vault container support"
```

---

## Task 3: VaultInitializer

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/VaultInitializer.java`

- [ ] **Step 1: Create VaultInitializer**

This class uses Vault's HTTP API (not CLI) to enable engines and create roles/keys.

```java
package io.vaultglue.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class VaultInitializer {

    private final String vaultAddr;
    private final String vaultToken;
    private final HttpClient httpClient;

    public VaultInitializer(String vaultAddr, String vaultToken) {
        this.vaultAddr = vaultAddr;
        this.vaultToken = vaultToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ─── Engine Enable ───

    public void enableKvV2(String path) {
        enableEngine(path, "{\"type\":\"kv\",\"options\":{\"version\":\"2\"}}");
    }

    public void enableTransit(String path) {
        enableEngine(path, "{\"type\":\"transit\"}");
    }

    public void enableDatabase(String path) {
        enableEngine(path, "{\"type\":\"database\"}");
    }

    private void enableEngine(String path, String body) {
        try {
            post("/v1/sys/mounts/" + path, body);
        } catch (RuntimeException e) {
            if (!e.getMessage().contains("400")) {
                throw e;
            }
            // Engine already mounted — ignore
        }
    }

    // ─── KV Operations ───

    public void kvPut(String mount, String path, String jsonData) {
        post("/v1/" + mount + "/data/" + path, "{\"data\":" + jsonData + "}");
    }

    // ─── Transit Operations ───

    public void createTransitKey(String mount, String keyName, String type) {
        post("/v1/" + mount + "/keys/" + keyName, "{\"type\":\"" + type + "\"}");
    }

    // ─── Database Operations ───

    public void configureDatabaseConnection(String mount, String name, String plugin, String connectionUrl,
                                             String allowedRoles, String username, String password) {
        String body = String.format(
                "{\"plugin_name\":\"%s\",\"connection_url\":\"%s\",\"allowed_roles\":\"%s\","
                        + "\"username\":\"%s\",\"password\":\"%s\"}",
                plugin, connectionUrl, allowedRoles, username, password);
        post("/v1/" + mount + "/config/" + name, body);
    }

    public void createStaticRole(String mount, String roleName, String dbName, String dbUsername,
                                  int rotationPeriod) {
        String body = String.format(
                "{\"db_name\":\"%s\",\"username\":\"%s\",\"rotation_period\":%d}",
                dbName, dbUsername, rotationPeriod);
        post("/v1/" + mount + "/static-roles/" + roleName, body);
    }

    public void createDynamicRole(String mount, String roleName, String dbName,
                                   String creationStatements, String defaultTtl, String maxTtl) {
        String body = String.format(
                "{\"db_name\":\"%s\",\"creation_statements\":[\"%s\"],\"default_ttl\":\"%s\",\"max_ttl\":\"%s\"}",
                dbName, creationStatements, defaultTtl, maxTtl);
        post("/v1/" + mount + "/roles/" + roleName, body);
    }

    // ─── HTTP ───

    private void post(String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + path))
                    .header("X-Vault-Token", vaultToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Vault API error " + response.statusCode()
                        + " for " + path + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Vault API call interrupted", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Vault API call failed: " + path, e);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :vault-glue-autoconfigure:compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/support/VaultInitializer.java
git commit -m "test: add VaultInitializer for HTTP API engine/role/key setup"
```

---

## Task 4: MySQL Container Support

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/MySqlContainerSupport.java`

- [ ] **Step 1: Create MySqlContainerSupport**

```java
package io.vaultglue.support;

import org.testcontainers.containers.MySQLContainer;

public final class MySqlContainerSupport {

    public static final String DB_NAME = "vaultglue_test";
    public static final String USERNAME = "root";
    public static final String PASSWORD = "test-password";
    public static final String STATIC_USERNAME = "static_user";
    public static final String STATIC_PASSWORD = "static_password";

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName(DB_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withInitScript("db/mysql-init.sql");

    static {
        MYSQL.start();
    }

    private MySqlContainerSupport() {
    }

    public static MySQLContainer<?> getContainer() {
        return MYSQL;
    }

    public static String getJdbcUrl() {
        return MYSQL.getJdbcUrl();
    }

    public static String getConnectionUrl() {
        return "{{username}}:{{password}}@tcp(" + MYSQL.getHost() + ":"
                + MYSQL.getFirstMappedPort() + ")/" + DB_NAME;
    }

    public static void initVaultRoles(VaultInitializer vault) {
        vault.configureDatabaseConnection("db", "mysql-test", "mysql-database-plugin",
                getConnectionUrl(), "mysql-static-role,mysql-dynamic-role", USERNAME, PASSWORD);

        vault.createStaticRole("db", "mysql-static-role", "mysql-test", STATIC_USERNAME, 86400);

        vault.createDynamicRole("db", "mysql-dynamic-role", "mysql-test",
                "CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON " + DB_NAME + ".* TO '{{name}}'@'%';",
                "1h", "24h");
    }
}
```

- [ ] **Step 2: Create MySQL init script**

Create file: `vault-glue-autoconfigure/src/test/resources/db/mysql-init.sql`

```sql
CREATE USER IF NOT EXISTS 'static_user'@'%' IDENTIFIED BY 'static_password';
GRANT ALL PRIVILEGES ON vaultglue_test.* TO 'static_user'@'%';
GRANT ALL PRIVILEGES ON vaultglue_test.* TO 'root'@'%';
FLUSH PRIVILEGES;

CREATE TABLE IF NOT EXISTS test_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255)
);
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :vault-glue-autoconfigure:compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/support/MySqlContainerSupport.java
git add vault-glue-autoconfigure/src/test/resources/db/mysql-init.sql
git commit -m "test: add MySQL container support with Vault static/dynamic roles"
```

---

## Task 5: PostgreSQL Container Support

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/support/PostgresContainerSupport.java`

- [ ] **Step 1: Create PostgresContainerSupport**

```java
package io.vaultglue.support;

import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresContainerSupport {

    public static final String DB_NAME = "vaultglue_test";
    public static final String USERNAME = "postgres";
    public static final String PASSWORD = "test-password";
    public static final String STATIC_USERNAME = "static_user";
    public static final String STATIC_PASSWORD = "static_password";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName(DB_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withInitScript("db/postgres-init.sql");

    static {
        POSTGRES.start();
    }

    private PostgresContainerSupport() {
    }

    public static PostgreSQLContainer<?> getContainer() {
        return POSTGRES;
    }

    public static String getJdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    public static String getConnectionUrl() {
        return "postgresql://{{username}}:{{password}}@" + POSTGRES.getHost() + ":"
                + POSTGRES.getFirstMappedPort() + "/" + DB_NAME + "?sslmode=disable";
    }

    public static void initVaultRoles(VaultInitializer vault) {
        vault.configureDatabaseConnection("db", "postgres-test", "postgresql-database-plugin",
                getConnectionUrl(), "pg-static-role,pg-dynamic-role", USERNAME, PASSWORD);

        vault.createStaticRole("db", "pg-static-role", "postgres-test", STATIC_USERNAME, 86400);

        vault.createDynamicRole("db", "pg-dynamic-role", "postgres-test",
                "CREATE ROLE \\\"{{name}}\\\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \\\"{{name}}\\\";",
                "1h", "24h");
    }
}
```

- [ ] **Step 2: Create PostgreSQL init script**

Create file: `vault-glue-autoconfigure/src/test/resources/db/postgres-init.sql`

```sql
CREATE USER static_user WITH PASSWORD 'static_password';
GRANT ALL PRIVILEGES ON DATABASE vaultglue_test TO static_user;
GRANT ALL PRIVILEGES ON SCHEMA public TO static_user;

CREATE TABLE IF NOT EXISTS test_table (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255)
);
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :vault-glue-autoconfigure:compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/support/PostgresContainerSupport.java
git add vault-glue-autoconfigure/src/test/resources/db/postgres-init.sql
git commit -m "test: add PostgreSQL container support with Vault static/dynamic roles"
```

---

## Task 6: Core Unit Tests — FailureStrategyHandler

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/core/FailureStrategyHandlerTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.vaultglue.core.event.CredentialRotationFailedEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailureStrategyHandlerTest {

    private VaultGlueProperties properties;
    private VaultGlueEventPublisher eventPublisher;
    private ApplicationEventPublisher springPublisher;
    private ConfigurableApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        properties = new VaultGlueProperties();
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setDelay(100);
        springPublisher = mock(ApplicationEventPublisher.class);
        eventPublisher = new VaultGlueEventPublisher(springPublisher);
        applicationContext = mock(ConfigurableApplicationContext.class);
    }

    @Test
    void retryStrategy_shouldRetryAndSucceed() {
        properties.setOnFailure(FailureStrategy.RETRY);
        FailureStrategyHandler handler = new FailureStrategyHandler(
                properties, eventPublisher, applicationContext);

        AtomicInteger attempts = new AtomicInteger(0);
        Supplier<Void> retryAction = () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("fail");
            }
            return null;
        };

        handler.handle("database", "primary", new RuntimeException("initial"), retryAction);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
        });

        verify(applicationContext, never()).close();
    }

    @Test
    void restartStrategy_shouldCloseContext() {
        properties.setOnFailure(FailureStrategy.RESTART);
        FailureStrategyHandler handler = new FailureStrategyHandler(
                properties, eventPublisher, applicationContext);

        handler.handle("database", "primary", new RuntimeException("fail"), () -> null);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(applicationContext).close();
        });
    }

    @Test
    void ignoreStrategy_shouldNotCloseContext() {
        properties.setOnFailure(FailureStrategy.IGNORE);
        FailureStrategyHandler handler = new FailureStrategyHandler(
                properties, eventPublisher, applicationContext);

        handler.handle("database", "primary", new RuntimeException("fail"), () -> null);

        verify(applicationContext, never()).close();
    }

    @Test
    void retryExhausted_shouldFallbackToRestart() {
        properties.setOnFailure(FailureStrategy.RETRY);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setDelay(50);
        FailureStrategyHandler handler = new FailureStrategyHandler(
                properties, eventPublisher, applicationContext);

        Supplier<Void> alwaysFails = () -> {
            throw new RuntimeException("always fail");
        };

        handler.handle("database", "primary", new RuntimeException("initial"), alwaysFails);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(applicationContext).close();
        });
    }
}
```

- [ ] **Step 2: Run test to verify**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.core.FailureStrategyHandlerTest"`
Expected: 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/core/FailureStrategyHandlerTest.java
git commit -m "test: add FailureStrategyHandler unit tests (RETRY, RESTART, IGNORE, fallback)"
```

---

## Task 7: Core Unit Tests — EventPublisher

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/core/VaultGlueEventPublisherTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.core;

import java.time.Duration;

import io.vaultglue.core.event.CredentialRotatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VaultGlueEventPublisherTest {

    @Test
    void publish_shouldDelegateToSpringPublisher() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        VaultGlueEventPublisher publisher = new VaultGlueEventPublisher(springPublisher);

        CredentialRotatedEvent event = new CredentialRotatedEvent(
                this, "database", "primary", "old-user", "new-user", Duration.ofHours(1));

        publisher.publish(event);

        ArgumentCaptor<CredentialRotatedEvent> captor = ArgumentCaptor.forClass(CredentialRotatedEvent.class);
        verify(springPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEngine()).isEqualTo("database");
        assertThat(captor.getValue().getNewUsername()).isEqualTo("new-user");
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.core.VaultGlueEventPublisherTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/core/VaultGlueEventPublisherTest.java
git commit -m "test: add VaultGlueEventPublisher unit test"
```

---

## Task 8: KV Unit Tests

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/DefaultVaultKvOperationsTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.kv;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultVaultKvOperationsTest {

    @Test
    void get_v2_shouldReturnData() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        when(vaultTemplate.opsForKeyValue(eq("app"),
                eq(VaultKeyValueOperationsSupport.KeyValueBackend.KV_2)))
                .thenReturn(kvOps);

        VaultResponse response = new VaultResponse();
        response.setData(Map.of("username", "admin"));
        when(kvOps.get("test-path")).thenReturn(response);

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        properties.setBackend("app");
        properties.setVersion(2);

        DefaultVaultKvOperations ops = new DefaultVaultKvOperations(
                vaultTemplate, properties, new ObjectMapper());

        Map<String, Object> result = ops.get("test-path");
        assertThat(result).containsEntry("username", "admin");
    }

    @Test
    void get_nonExistentPath_shouldReturnEmptyMap() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        when(vaultTemplate.opsForKeyValue(anyString(),
                eq(VaultKeyValueOperationsSupport.KeyValueBackend.KV_2)))
                .thenReturn(kvOps);
        when(kvOps.get("non-existent")).thenReturn(null);

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        properties.setBackend("app");
        properties.setVersion(2);

        DefaultVaultKvOperations ops = new DefaultVaultKvOperations(
                vaultTemplate, properties, new ObjectMapper());

        Map<String, Object> result = ops.get("non-existent");
        assertThat(result).isEmpty();
    }

    @Test
    void getByVersion_onV1_shouldThrowUnsupportedOperation() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        VaultKeyValueOperations kvOps = mock(VaultKeyValueOperations.class);
        when(vaultTemplate.opsForKeyValue(anyString(),
                eq(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1)))
                .thenReturn(kvOps);

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        properties.setBackend("app");
        properties.setVersion(1);

        DefaultVaultKvOperations ops = new DefaultVaultKvOperations(
                vaultTemplate, properties, new ObjectMapper());

        assertThatThrownBy(() -> ops.get("path", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.DefaultVaultKvOperationsTest"`
Expected: 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/DefaultVaultKvOperationsTest.java
git commit -m "test: add DefaultVaultKvOperations unit tests (v2, empty path, v1 unsupported)"
```

---

## Task 9: KV E2E Tests

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultKvOperationsE2ETest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.kv;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vaultglue.support.VaultContainerSupport;
import io.vaultglue.support.VaultInitializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class VaultKvOperationsE2ETest {

    private static VaultTemplate vaultTemplate;
    private DefaultVaultKvOperations kvOps;

    @BeforeAll
    static void startContainers() {
        String addr = VaultContainerSupport.getAddress();
        VaultInitializer initializer = new VaultInitializer(addr, VaultContainerSupport.VAULT_TOKEN);
        initializer.enableKvV2("app");

        VaultEndpoint endpoint = VaultEndpoint.from(java.net.URI.create(addr));
        vaultTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication(VaultContainerSupport.VAULT_TOKEN));
    }

    @BeforeEach
    void setUp() {
        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        properties.setBackend("app");
        properties.setVersion(2);
        properties.setApplicationName("test-app");
        kvOps = new DefaultVaultKvOperations(vaultTemplate, properties, new ObjectMapper());
    }

    @Test
    void putAndGet_shouldRoundTrip() {
        kvOps.put("e2e/secret1", Map.of("username", "admin", "password", "s3cret"));

        Map<String, Object> result = kvOps.get("e2e/secret1");
        assertThat(result).containsEntry("username", "admin");
        assertThat(result).containsEntry("password", "s3cret");
    }

    @Test
    void delete_shouldRemoveSecret() {
        kvOps.put("e2e/to-delete", Map.of("key", "value"));
        kvOps.delete("e2e/to-delete");

        Map<String, Object> result = kvOps.get("e2e/to-delete");
        assertThat(result).isEmpty();
    }

    @Test
    void list_shouldReturnKeys() {
        kvOps.put("e2e/list/key1", Map.of("a", "1"));
        kvOps.put("e2e/list/key2", Map.of("b", "2"));

        List<String> keys = kvOps.list("e2e/list");
        assertThat(keys).contains("key1", "key2");
    }

    @Test
    void versioning_shouldReadSpecificVersion() {
        kvOps.put("e2e/versioned", Map.of("val", "v1"));
        kvOps.put("e2e/versioned", Map.of("val", "v2"));

        Map<String, Object> v1 = kvOps.get("e2e/versioned", 1);
        Map<String, Object> v2 = kvOps.get("e2e/versioned", 2);

        assertThat(v1).containsEntry("val", "v1");
        assertThat(v2).containsEntry("val", "v2");
    }

    @Test
    void vaultValue_shouldInjectField() {
        kvOps.put("e2e/inject-test", Map.of("db-password", "injected-value"));

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        properties.setBackend("app");
        properties.setVersion(2);

        VaultValueBeanPostProcessor processor = new VaultValueBeanPostProcessor(kvOps);

        TestBean bean = new TestBean();
        processor.postProcessAfterInitialization(bean, "testBean");
        assertThat(bean.dbPassword).isEqualTo("injected-value");
    }

    @Test
    void watch_shouldDetectChange() {
        kvOps.put("e2e/watched", Map.of("val", "original"));

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        properties.setBackend("app");
        properties.setVersion(2);
        properties.getWatch().setEnabled(true);
        properties.getWatch().setInterval(java.time.Duration.ofSeconds(1));

        VaultValueBeanPostProcessor processor = new VaultValueBeanPostProcessor(kvOps);
        VaultKvWatcher watcher = new VaultKvWatcher(kvOps, processor, properties);
        watcher.watch("e2e/watched");
        watcher.start();

        try {
            kvOps.put("e2e/watched", Map.of("val", "changed"));

            Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        Map<String, Object> current = kvOps.get("e2e/watched");
                        assertThat(current).containsEntry("val", "changed");
                    });
        } finally {
            watcher.shutdown();
        }
    }

    // ─── Test Helper ───

    static class TestBean {
        @VaultValue(path = "e2e/inject-test", key = "db-password")
        String dbPassword;
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.VaultKvOperationsE2ETest"`
Expected: 6 tests PASS (requires Docker running)

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultKvOperationsE2ETest.java
git commit -m "test: add KV engine E2E tests (CRUD, versioning, @VaultValue, watch)"
```

---

## Task 10: KV Integration Test

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultGlueKvAutoConfigurationTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.kv;

import io.vaultglue.autoconfigure.VaultGlueCoreAutoConfiguration;
import io.vaultglue.autoconfigure.VaultGlueKvAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VaultGlueKvAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueKvAutoConfiguration.class))
            .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class));

    @Test
    void kvEnabled_shouldRegisterBeans() {
        contextRunner
                .withPropertyValues("vault-glue.kv.enabled=true",
                        "vault-glue.kv.backend=app")
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultKvOperations.class);
                    assertThat(context).hasSingleBean(VaultValueBeanPostProcessor.class);
                });
    }

    @Test
    void kvDisabled_shouldNotRegisterBeans() {
        contextRunner
                .withPropertyValues("vault-glue.kv.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultKvOperations.class);
                });
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.kv.VaultGlueKvAutoConfigurationTest"`
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/kv/VaultGlueKvAutoConfigurationTest.java
git commit -m "test: add KV AutoConfiguration integration tests"
```

---

## Task 11: Transit Unit Tests

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/DefaultVaultTransitOperationsTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.transit;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultVaultTransitOperationsTest {

    private VaultTemplate vaultTemplate;
    private DefaultVaultTransitOperations transitOps;

    @BeforeEach
    void setUp() {
        vaultTemplate = mock(VaultTemplate.class);
        VaultGlueTransitProperties properties = new VaultGlueTransitProperties();
        properties.setBackend("transit");
        transitOps = new DefaultVaultTransitOperations(vaultTemplate, properties);
    }

    @Test
    void encrypt_shouldReturnCiphertext() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("ciphertext", "vault:v1:abc123"));
        when(vaultTemplate.write(eq("transit/encrypt/test-key"), any()))
                .thenReturn(response);

        String result = transitOps.encrypt("test-key", "hello");
        assertThat(result).isEqualTo("vault:v1:abc123");
    }

    @Test
    void decrypt_shouldReturnPlaintext() {
        String base64Hello = Base64.getEncoder().encodeToString("hello".getBytes());
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("plaintext", base64Hello));
        when(vaultTemplate.write(eq("transit/decrypt/test-key"), any()))
                .thenReturn(response);

        String result = transitOps.decrypt("test-key", "vault:v1:abc123");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void encryptBatch_shouldReturnCiphertexts() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("batch_results",
                List.of(Map.of("ciphertext", "vault:v1:aaa"),
                        Map.of("ciphertext", "vault:v1:bbb"))));
        when(vaultTemplate.write(eq("transit/encrypt/test-key"), any()))
                .thenReturn(response);

        List<String> results = transitOps.encryptBatch("test-key", List.of("one", "two"));
        assertThat(results).containsExactly("vault:v1:aaa", "vault:v1:bbb");
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.DefaultVaultTransitOperationsTest"`
Expected: 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/DefaultVaultTransitOperationsTest.java
git commit -m "test: add Transit unit tests (encrypt, decrypt, batch)"
```

---

## Task 12: Transit E2E Tests

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultTransitOperationsE2ETest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.transit;

import java.util.LinkedHashMap;
import java.util.Map;

import io.vaultglue.support.VaultContainerSupport;
import io.vaultglue.support.VaultInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class VaultTransitOperationsE2ETest {

    private static VaultTemplate vaultTemplate;
    private DefaultVaultTransitOperations transitOps;

    @BeforeAll
    static void startContainers() {
        String addr = VaultContainerSupport.getAddress();
        VaultInitializer initializer = new VaultInitializer(addr, VaultContainerSupport.VAULT_TOKEN);
        initializer.enableTransit("transit");
        initializer.createTransitKey("transit", "test-aes", "aes256-gcm96");
        initializer.createTransitKey("transit", "test-ed25519", "ed25519");

        VaultEndpoint endpoint = VaultEndpoint.from(java.net.URI.create(addr));
        vaultTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication(VaultContainerSupport.VAULT_TOKEN));
    }

    @BeforeEach
    void setUp() {
        VaultGlueTransitProperties properties = new VaultGlueTransitProperties();
        properties.setBackend("transit");
        transitOps = new DefaultVaultTransitOperations(vaultTemplate, properties);
    }

    @Test
    void encryptAndDecrypt_shouldRoundTrip() {
        String ciphertext = transitOps.encrypt("test-aes", "sensitive-data");
        assertThat(ciphertext).startsWith("vault:v1:");

        String plaintext = transitOps.decrypt("test-aes", ciphertext);
        assertThat(plaintext).isEqualTo("sensitive-data");
    }

    @Test
    void rewrap_shouldReEncryptWithLatestVersion() {
        String ciphertext = transitOps.encrypt("test-aes", "rewrap-test");
        transitOps.rotateKey("test-aes");

        String rewrapped = transitOps.rewrap("test-aes", ciphertext);
        assertThat(rewrapped).startsWith("vault:v2:");

        String plaintext = transitOps.decrypt("test-aes", rewrapped);
        assertThat(plaintext).isEqualTo("rewrap-test");
    }

    @Test
    void hmac_shouldGenerateAndVerify() {
        String hmac = transitOps.hmac("test-aes", "hmac-data");
        assertThat(hmac).startsWith("vault:v1:");

        boolean valid = transitOps.verifyHmac("test-aes", "hmac-data", hmac);
        assertThat(valid).isTrue();

        boolean invalid = transitOps.verifyHmac("test-aes", "tampered-data", hmac);
        assertThat(invalid).isFalse();
    }

    @Test
    void signAndVerify_shouldUseEd25519() {
        String signature = transitOps.sign("test-ed25519", "sign-data");
        assertThat(signature).startsWith("vault:v1:");

        boolean valid = transitOps.verify("test-ed25519", "sign-data", signature);
        assertThat(valid).isTrue();
    }

    @Test
    void vaultEncryptConverter_shouldEncryptAndDecrypt() {
        org.springframework.context.ApplicationContext mockCtx =
                org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);
        org.mockito.Mockito.when(mockCtx.getBean(VaultTransitOperations.class)).thenReturn(transitOps);
        VaultEncryptConverter.initialize(mockCtx, "test-aes");

        VaultEncryptConverter converter = new VaultEncryptConverter();
        String encrypted = converter.convertToDatabaseColumn("jpa-field-data");
        assertThat(encrypted).startsWith("vg:test-aes:vault:v1:");

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo("jpa-field-data");
    }

    @Test
    void autoKeyCreation_shouldCreateKeyOnStartup() {
        VaultGlueTransitProperties properties = new VaultGlueTransitProperties();
        properties.setBackend("transit");
        Map<String, VaultGlueTransitProperties.TransitKeyProperties> keys = new LinkedHashMap<>();
        VaultGlueTransitProperties.TransitKeyProperties keyProps = new VaultGlueTransitProperties.TransitKeyProperties();
        keyProps.setType(TransitKeyType.AES256_GCM96);
        keyProps.setAutoCreate(true);
        keys.put("auto-created-key", keyProps);
        properties.setKeys(keys);

        TransitKeyInitializer initializer = new TransitKeyInitializer(transitOps, properties);
        initializer.run(null);

        TransitKeyInfo info = transitOps.getKeyInfo("auto-created-key");
        assertThat(info.name()).isEqualTo("auto-created-key");
        assertThat(info.latestVersion()).isGreaterThanOrEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.VaultTransitOperationsE2ETest"`
Expected: 6 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultTransitOperationsE2ETest.java
git commit -m "test: add Transit engine E2E tests (encrypt, rewrap, hmac, sign, converter, auto-key)"
```

---

## Task 13: Transit Integration Test

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultGlueTransitAutoConfigurationTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.transit;

import io.vaultglue.autoconfigure.VaultGlueCoreAutoConfiguration;
import io.vaultglue.autoconfigure.VaultGlueTransitAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VaultGlueTransitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueTransitAutoConfiguration.class))
            .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class));

    @Test
    void transitEnabled_shouldRegisterBeans() {
        contextRunner
                .withPropertyValues("vault-glue.transit.enabled=true",
                        "vault-glue.transit.backend=transit")
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultTransitOperations.class);
                    assertThat(context).hasSingleBean(TransitKeyInitializer.class);
                });
    }

    @Test
    void transitDisabled_shouldNotRegisterBeans() {
        contextRunner
                .withPropertyValues("vault-glue.transit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultTransitOperations.class);
                });
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.transit.VaultGlueTransitAutoConfigurationTest"`
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/transit/VaultGlueTransitAutoConfigurationTest.java
git commit -m "test: add Transit AutoConfiguration integration tests"
```

---

## Task 14: Database Unit Tests

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/VaultGlueDelegatingDataSourceTest.java`
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/DataSourceRotatorTest.java`
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/GracefulShutdownTest.java`
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/VaultGlueDataSourcesTest.java`

- [ ] **Step 1: Write VaultGlueDelegatingDataSourceTest**

```java
package io.vaultglue.database;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultGlueDelegatingDataSourceTest {

    @Test
    void setDelegate_shouldSwapAndRouteConnections() throws SQLException {
        DataSource initial = mock(DataSource.class);
        DataSource replacement = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        when(replacement.getConnection()).thenReturn(mockConn);

        VaultGlueDelegatingDataSource delegating = new VaultGlueDelegatingDataSource(
                "test", initial, "old-user");

        assertThat(delegating.getCurrentUsername()).isEqualTo("old-user");

        delegating.setDelegate(replacement, "new-user");

        assertThat(delegating.getCurrentUsername()).isEqualTo("new-user");
        assertThat(delegating.getLastRotationTime()).isNotNull();

        Connection conn = delegating.getConnection();
        verify(replacement).getConnection();
    }
}
```

- [ ] **Step 2: Write DataSourceRotatorTest**

```java
package io.vaultglue.database;

import java.time.Duration;

import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.event.CredentialRotatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataSourceRotatorTest {

    @Test
    void rotate_shouldSwapDelegateAndPublishEvent() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        VaultGlueEventPublisher eventPublisher = new VaultGlueEventPublisher(springPublisher);
        DataSourceRotator rotator = new DataSourceRotator(eventPublisher);

        javax.sql.DataSource initial = mock(javax.sql.DataSource.class);
        VaultGlueDelegatingDataSource delegating = new VaultGlueDelegatingDataSource(
                "primary", initial, "old-user");

        VaultGlueDatabaseProperties.DataSourceProperties props = new VaultGlueDatabaseProperties.DataSourceProperties();
        props.setJdbcUrl("jdbc:h2:mem:test");
        props.setDriverClassName("org.h2.Driver");

        rotator.rotate(delegating, props, "new-user", "new-pass", Duration.ofHours(1));

        assertThat(delegating.getCurrentUsername()).isEqualTo("new-user");

        ArgumentCaptor<CredentialRotatedEvent> captor = ArgumentCaptor.forClass(CredentialRotatedEvent.class);
        verify(springPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getNewUsername()).isEqualTo("new-user");
    }
}
```

- [ ] **Step 3: Write GracefulShutdownTest**

```java
package io.vaultglue.database;

import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GracefulShutdownTest {

    @Test
    void execute_shouldCloseAfterActiveConnectionsDrain() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:shutdown_test;DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        HikariDataSource ds = new HikariDataSource(config);

        GracefulShutdown.execute(ds, 5);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ds.isClosed()).isTrue();
        });
    }
}
```

- [ ] **Step 4: Write VaultGlueDataSourcesTest**

```java
package io.vaultglue.database;

import java.util.Map;

import io.vaultglue.autoconfigure.VaultGlueDatabaseAutoConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class VaultGlueDataSourcesTest {

    @Test
    void get_unknownName_shouldThrowIllegalArgument() {
        javax.sql.DataSource ds = mock(javax.sql.DataSource.class);
        VaultGlueDelegatingDataSource delegating = new VaultGlueDelegatingDataSource(
                "primary", ds, "user");

        VaultGlueDatabaseAutoConfiguration.VaultGlueDataSources sources =
                new VaultGlueDatabaseAutoConfiguration.VaultGlueDataSources(
                        Map.of("primary", delegating));

        assertThatThrownBy(() -> sources.get("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 5: Run all database unit tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.*Test" --tests "!io.vaultglue.database.*E2E*"`
Expected: 4 tests PASS

- [ ] **Step 6: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/VaultGlueDelegatingDataSourceTest.java
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/DataSourceRotatorTest.java
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/GracefulShutdownTest.java
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/VaultGlueDataSourcesTest.java
git commit -m "test: add Database engine unit tests (delegating, rotator, shutdown, datasources)"
```

---

## Task 15: Database E2E Tests — Static Role MySQL

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/StaticRoleMySqlE2ETest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.database.static_.StaticCredentialProvider;
import io.vaultglue.database.static_.StaticCredentialProvider.DbCredential;
import io.vaultglue.support.MySqlContainerSupport;
import io.vaultglue.support.VaultContainerSupport;
import io.vaultglue.support.VaultInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StaticRoleMySqlE2ETest {

    private static VaultTemplate vaultTemplate;

    @BeforeAll
    static void startContainers() {
        String addr = VaultContainerSupport.getAddress();
        VaultInitializer initializer = new VaultInitializer(addr, VaultContainerSupport.VAULT_TOKEN);
        initializer.enableDatabase("db");
        MySqlContainerSupport.initVaultRoles(initializer);

        VaultEndpoint endpoint = VaultEndpoint.from(java.net.URI.create(addr));
        vaultTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication(VaultContainerSupport.VAULT_TOKEN));
    }

    @Test
    void staticRole_shouldProvideWorkingCredential() throws Exception {
        StaticCredentialProvider provider = new StaticCredentialProvider(vaultTemplate);
        DbCredential credential = provider.getCredential("db", "mysql-static-role");

        assertThat(credential.username()).isNotBlank();
        assertThat(credential.password()).isNotBlank();

        VaultGlueDatabaseProperties.DataSourceProperties props = new VaultGlueDatabaseProperties.DataSourceProperties();
        props.setJdbcUrl(MySqlContainerSupport.getJdbcUrl());
        props.setDriverClassName("com.mysql.cj.jdbc.Driver");

        com.zaxxer.hikari.HikariDataSource ds = HikariDataSourceFactory.create(
                "mysql-static", props, credential.username(), credential.password());

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        } finally {
            ds.close();
        }
    }

    @Test
    void staticRole_shouldRotateCredentials() throws Exception {
        StaticCredentialProvider provider = new StaticCredentialProvider(vaultTemplate);
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        VaultGlueEventPublisher eventPublisher = new VaultGlueEventPublisher(springPublisher);
        DataSourceRotator rotator = new DataSourceRotator(eventPublisher);

        DbCredential initial = provider.getCredential("db", "mysql-static-role");

        VaultGlueDatabaseProperties.DataSourceProperties props = new VaultGlueDatabaseProperties.DataSourceProperties();
        props.setJdbcUrl(MySqlContainerSupport.getJdbcUrl());
        props.setDriverClassName("com.mysql.cj.jdbc.Driver");

        com.zaxxer.hikari.HikariDataSource initialDs = HikariDataSourceFactory.create(
                "mysql-rotate", props, initial.username(), initial.password());
        VaultGlueDelegatingDataSource delegating = new VaultGlueDelegatingDataSource(
                "primary", initialDs, initial.username());

        DbCredential rotated = provider.getCredential("db", "mysql-static-role");
        rotator.rotate(delegating, props, rotated.username(), rotated.password(),
                java.time.Duration.ofHours(5));

        assertThat(delegating.getCurrentUsername()).isEqualTo(rotated.username());

        try (Connection conn = delegating.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        }
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.StaticRoleMySqlE2ETest"`
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/StaticRoleMySqlE2ETest.java
git commit -m "test: add Static role MySQL E2E tests (credential, rotation)"
```

---

## Task 16: Database E2E Tests — Static Role PostgreSQL

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/StaticRolePostgresE2ETest.java`

- [ ] **Step 1: Write the test**

Same structure as Task 15 but using `PostgresContainerSupport` instead of `MySqlContainerSupport`, `org.postgresql.Driver`, and `pg-static-role`.

```java
package io.vaultglue.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.database.static_.StaticCredentialProvider;
import io.vaultglue.database.static_.StaticCredentialProvider.DbCredential;
import io.vaultglue.support.PostgresContainerSupport;
import io.vaultglue.support.VaultContainerSupport;
import io.vaultglue.support.VaultInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StaticRolePostgresE2ETest {

    private static VaultTemplate vaultTemplate;

    @BeforeAll
    static void startContainers() {
        String addr = VaultContainerSupport.getAddress();
        VaultInitializer initializer = new VaultInitializer(addr, VaultContainerSupport.VAULT_TOKEN);
        PostgresContainerSupport.initVaultRoles(initializer);

        VaultEndpoint endpoint = VaultEndpoint.from(java.net.URI.create(addr));
        vaultTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication(VaultContainerSupport.VAULT_TOKEN));
    }

    @Test
    void staticRole_shouldProvideWorkingCredential() throws Exception {
        StaticCredentialProvider provider = new StaticCredentialProvider(vaultTemplate);
        DbCredential credential = provider.getCredential("db", "pg-static-role");

        assertThat(credential.username()).isNotBlank();
        assertThat(credential.password()).isNotBlank();

        VaultGlueDatabaseProperties.DataSourceProperties props = new VaultGlueDatabaseProperties.DataSourceProperties();
        props.setJdbcUrl(PostgresContainerSupport.getJdbcUrl());
        props.setDriverClassName("org.postgresql.Driver");

        com.zaxxer.hikari.HikariDataSource ds = HikariDataSourceFactory.create(
                "pg-static", props, credential.username(), credential.password());

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        } finally {
            ds.close();
        }
    }

    @Test
    void staticRole_shouldRotateCredentials() throws Exception {
        StaticCredentialProvider provider = new StaticCredentialProvider(vaultTemplate);
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        VaultGlueEventPublisher eventPublisher = new VaultGlueEventPublisher(springPublisher);
        DataSourceRotator rotator = new DataSourceRotator(eventPublisher);

        DbCredential initial = provider.getCredential("db", "pg-static-role");

        VaultGlueDatabaseProperties.DataSourceProperties props = new VaultGlueDatabaseProperties.DataSourceProperties();
        props.setJdbcUrl(PostgresContainerSupport.getJdbcUrl());
        props.setDriverClassName("org.postgresql.Driver");

        com.zaxxer.hikari.HikariDataSource initialDs = HikariDataSourceFactory.create(
                "pg-rotate", props, initial.username(), initial.password());
        VaultGlueDelegatingDataSource delegating = new VaultGlueDelegatingDataSource(
                "primary", initialDs, initial.username());

        DbCredential rotated = provider.getCredential("db", "pg-static-role");
        rotator.rotate(delegating, props, rotated.username(), rotated.password(),
                java.time.Duration.ofHours(5));

        try (Connection conn = delegating.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        }
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.StaticRolePostgresE2ETest"`
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/StaticRolePostgresE2ETest.java
git commit -m "test: add Static role PostgreSQL E2E tests"
```

---

## Task 17: Database E2E Tests — Dynamic Role

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/DynamicRoleMySqlE2ETest.java`
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/DynamicRolePostgresE2ETest.java`

- [ ] **Step 1: Write DynamicRoleMySqlE2ETest**

```java
package io.vaultglue.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import io.vaultglue.support.MySqlContainerSupport;
import io.vaultglue.support.VaultContainerSupport;
import io.vaultglue.support.VaultInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicRoleMySqlE2ETest {

    private static VaultTemplate vaultTemplate;

    @BeforeAll
    static void startContainers() {
        String addr = VaultContainerSupport.getAddress();
        VaultEndpoint endpoint = VaultEndpoint.from(java.net.URI.create(addr));
        vaultTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication(VaultContainerSupport.VAULT_TOKEN));
    }

    @Test
    void dynamicRole_shouldIssueLeasedCredential() throws Exception {
        VaultResponse response = vaultTemplate.read("db/creds/mysql-dynamic-role");
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNotNull();

        String username = (String) response.getData().get("username");
        String password = (String) response.getData().get("password");
        assertThat(username).isNotBlank();
        assertThat(password).isNotBlank();

        VaultGlueDatabaseProperties.DataSourceProperties props = new VaultGlueDatabaseProperties.DataSourceProperties();
        props.setJdbcUrl(MySqlContainerSupport.getJdbcUrl());
        props.setDriverClassName("com.mysql.cj.jdbc.Driver");

        com.zaxxer.hikari.HikariDataSource ds = HikariDataSourceFactory.create(
                "mysql-dynamic", props, username, password);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        } finally {
            ds.close();
        }
    }
}
```

- [ ] **Step 2: Write DynamicRolePostgresE2ETest**

```java
package io.vaultglue.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import io.vaultglue.support.PostgresContainerSupport;
import io.vaultglue.support.VaultContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicRolePostgresE2ETest {

    private static VaultTemplate vaultTemplate;

    @BeforeAll
    static void startContainers() {
        String addr = VaultContainerSupport.getAddress();
        VaultEndpoint endpoint = VaultEndpoint.from(java.net.URI.create(addr));
        vaultTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication(VaultContainerSupport.VAULT_TOKEN));
    }

    @Test
    void dynamicRole_shouldIssueLeasedCredential() throws Exception {
        VaultResponse response = vaultTemplate.read("db/creds/pg-dynamic-role");
        assertThat(response).isNotNull();

        String username = (String) response.getData().get("username");
        String password = (String) response.getData().get("password");

        VaultGlueDatabaseProperties.DataSourceProperties props = new VaultGlueDatabaseProperties.DataSourceProperties();
        props.setJdbcUrl(PostgresContainerSupport.getJdbcUrl());
        props.setDriverClassName("org.postgresql.Driver");

        com.zaxxer.hikari.HikariDataSource ds = HikariDataSourceFactory.create(
                "pg-dynamic", props, username, password);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        } finally {
            ds.close();
        }
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.DynamicRole*"`
Expected: 2 tests PASS

- [ ] **Step 4: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/DynamicRoleMySqlE2ETest.java
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/DynamicRolePostgresE2ETest.java
git commit -m "test: add Dynamic role E2E tests (MySQL, PostgreSQL)"
```

---

## Task 18: Database E2E Tests — MultiDataSource

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/MultiDataSourceE2ETest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import io.vaultglue.database.static_.StaticCredentialProvider;
import io.vaultglue.database.static_.StaticCredentialProvider.DbCredential;
import io.vaultglue.support.MySqlContainerSupport;
import io.vaultglue.support.PostgresContainerSupport;
import io.vaultglue.support.VaultContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MultiDataSourceE2ETest {

    private static VaultTemplate vaultTemplate;

    @BeforeAll
    static void startContainers() {
        String addr = VaultContainerSupport.getAddress();
        VaultEndpoint endpoint = VaultEndpoint.from(java.net.URI.create(addr));
        vaultTemplate = new VaultTemplate(endpoint,
                new TokenAuthentication(VaultContainerSupport.VAULT_TOKEN));
    }

    @Test
    void multiDataSource_shouldManageBothSources() throws Exception {
        StaticCredentialProvider provider = new StaticCredentialProvider(vaultTemplate);

        // Primary: MySQL
        DbCredential mysqlCred = provider.getCredential("db", "mysql-static-role");
        VaultGlueDatabaseProperties.DataSourceProperties mysqlProps = new VaultGlueDatabaseProperties.DataSourceProperties();
        mysqlProps.setJdbcUrl(MySqlContainerSupport.getJdbcUrl());
        mysqlProps.setDriverClassName("com.mysql.cj.jdbc.Driver");

        com.zaxxer.hikari.HikariDataSource mysqlDs = HikariDataSourceFactory.create(
                "primary", mysqlProps, mysqlCred.username(), mysqlCred.password());
        VaultGlueDelegatingDataSource primaryDelegating = new VaultGlueDelegatingDataSource(
                "primary", mysqlDs, mysqlCred.username());

        // Replica: PostgreSQL
        DbCredential pgCred = provider.getCredential("db", "pg-static-role");
        VaultGlueDatabaseProperties.DataSourceProperties pgProps = new VaultGlueDatabaseProperties.DataSourceProperties();
        pgProps.setJdbcUrl(PostgresContainerSupport.getJdbcUrl());
        pgProps.setDriverClassName("org.postgresql.Driver");

        com.zaxxer.hikari.HikariDataSource pgDs = HikariDataSourceFactory.create(
                "replica", pgProps, pgCred.username(), pgCred.password());
        VaultGlueDelegatingDataSource replicaDelegating = new VaultGlueDelegatingDataSource(
                "replica", pgDs, pgCred.username());

        // Verify both work
        try (Connection conn = primaryDelegating.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        }

        try (Connection conn = replicaDelegating.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        }

        mysqlDs.close();
        pgDs.close();
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.MultiDataSourceE2ETest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/MultiDataSourceE2ETest.java
git commit -m "test: add MultiDataSource E2E test (MySQL + PostgreSQL)"
```

---

## Task 19: Database Integration Test

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/database/VaultGlueDatabaseAutoConfigurationTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.database;

import com.zaxxer.hikari.HikariDataSource;
import io.vaultglue.autoconfigure.VaultGlueCoreAutoConfiguration;
import io.vaultglue.autoconfigure.VaultGlueDatabaseAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VaultGlueDatabaseAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueDatabaseAutoConfiguration.class))
            .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class));

    @Test
    void withHikariAndVaultTemplate_shouldRegisterRotator() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSourceRotator.class);
                });
    }

    @Test
    void withoutHikari_shouldNotRegisterBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        VaultGlueCoreAutoConfiguration.class,
                        VaultGlueDatabaseAutoConfiguration.class))
                .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class))
                .withClassLoader(new org.springframework.boot.test.context.FilteredClassLoader(
                        HikariDataSource.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DataSourceRotator.class);
                });
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.database.VaultGlueDatabaseAutoConfigurationTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/database/VaultGlueDatabaseAutoConfigurationTest.java
git commit -m "test: add Database AutoConfiguration integration test"
```

---

## Task 20: Disabled AutoConfiguration Test

**Files:**
- Create: `vault-glue-autoconfigure/src/test/java/io/vaultglue/autoconfigure/DisabledAutoConfigurationTest.java`

- [ ] **Step 1: Write the test**

```java
package io.vaultglue.autoconfigure;

import io.vaultglue.kv.VaultKvOperations;
import io.vaultglue.transit.VaultTransitOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DisabledAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueKvAutoConfiguration.class,
                    VaultGlueTransitAutoConfiguration.class))
            .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class));

    @Test
    void allDisabled_shouldNotRegisterAnyEngineBeans() {
        contextRunner
                .withPropertyValues(
                        "vault-glue.kv.enabled=false",
                        "vault-glue.transit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultKvOperations.class);
                    assertThat(context).doesNotHaveBean(VaultTransitOperations.class);
                });
    }

    @Test
    void noVaultTemplate_shouldNotRegisterAnyBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        VaultGlueCoreAutoConfiguration.class,
                        VaultGlueKvAutoConfiguration.class,
                        VaultGlueTransitAutoConfiguration.class))
                .withPropertyValues(
                        "vault-glue.kv.enabled=true",
                        "vault-glue.transit.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultKvOperations.class);
                    assertThat(context).doesNotHaveBean(VaultTransitOperations.class);
                });
    }
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :vault-glue-autoconfigure:test --tests "io.vaultglue.autoconfigure.DisabledAutoConfigurationTest"`
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add vault-glue-autoconfigure/src/test/java/io/vaultglue/autoconfigure/DisabledAutoConfigurationTest.java
git commit -m "test: add disabled AutoConfiguration integration tests"
```

---

## Task 21: Run All Tests & Final Verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :vault-glue-autoconfigure:test`
Expected: All 42 tests PASS

- [ ] **Step 2: Verify test report**

Run: `./gradlew :vault-glue-autoconfigure:test --info 2>&1 | grep -E "(PASS|FAIL|tests)"`

- [ ] **Step 3: Commit any fixes**

If any tests fail, fix and commit individually.

---

## Task 22: Extract to vault-glue-test (Phase 2)

This task is deferred until all autoconfigure tests are stable. When ready:

- [ ] **Step 1: Copy `support/` package to `vault-glue-test/src/main/java/io/vaultglue/test/`**
- [ ] **Step 2: Add `@VaultGlueTest` annotation**
- [ ] **Step 3: Update `vault-glue-test/build.gradle` dependencies**
- [ ] **Step 4: Update autoconfigure tests to use `vault-glue-test` dependency**
- [ ] **Step 5: Run all tests to verify extraction didn't break anything**
- [ ] **Step 6: Commit**

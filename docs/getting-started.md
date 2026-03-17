# Getting Started

## Prerequisites

- Java 21+
- Spring Boot 3.5+
- A running HashiCorp Vault instance
- Vault engines mounted and configured (e.g., `vault secrets enable transit`)

## Installation

### Gradle (Kotlin DSL)

```kotlin
implementation("io.github.vault-glue:vault-glue-spring-boot-starter:0.1.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'io.github.vault-glue:vault-glue-spring-boot-starter:0.1.0'
```

### Maven

```xml
<dependency>
    <groupId>io.github.vault-glue</groupId>
    <artifactId>vault-glue-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The starter transitively brings in `spring-cloud-starter-vault-config` and the VaultGlue autoconfiguration module.

## Vault Connection

VaultGlue uses [Spring Cloud Vault](https://spring.io/projects/spring-cloud-vault) for Vault connectivity.

### Local Development &mdash; Token Auth

The simplest option for local development. Use a dev Vault instance with a root or dev-only token:

```yaml
spring:
  cloud:
    vault:
      uri: http://localhost:8200
      authentication: TOKEN
      token: hvs.xxxxx
```

> **Tip:** To avoid accidentally committing tokens, you can store them in a `.env` file (add to `.gitignore`) and reference via `${VAULT_TOKEN}`, or use Spring Boot's `spring.config.import` with a local-only profile.

### Deployed Environments &mdash; AppRole (Recommended)

For any deployed environment (dev, staging, production), **AppRole** is the standard authentication method for applications. The role-id acts as a username and the secret-id acts as a one-time password.

```yaml
spring:
  cloud:
    vault:
      uri: https://vault.example.com
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
```

The `${VAULT_ROLE_ID}` and `${VAULT_SECRET_ID}` placeholders are resolved from environment variables at runtime. How you inject them depends on your deployment platform:

**CI/CD Pipeline (Jenkins, GitHub Actions, GitLab CI)**

The most common pattern. The pipeline fetches AppRole credentials from Vault and passes them as environment variables when running the container:

```groovy
// Jenkinsfile example
docker.image('my-app:latest').run(
    '-e VAULT_ROLE_ID=${vault_role_id} -e VAULT_SECRET_ID=${vault_secret_id}'
)
```

**Docker Compose**

Define environment variables in `docker-compose.yml` and provide values via a `.env` file or host environment:

```yaml
# docker-compose.yml
services:
  my-app:
    image: my-app:latest
    environment:
      VAULT_ROLE_ID: ${VAULT_ROLE_ID}
      VAULT_SECRET_ID: ${VAULT_SECRET_ID}
```

```bash
# .env (gitignored)
VAULT_ROLE_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
VAULT_SECRET_ID=yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy
```

### Other Authentication Methods

Spring Cloud Vault also supports Kubernetes, AWS IAM, Azure MSI, GCP, and other authentication backends. See the [Spring Cloud Vault documentation](https://docs.spring.io/spring-cloud-vault/reference/) for details.

### Security: Never Bake Secrets into Images

A common mistake is passing credentials as Docker `--build-arg` values. Build arguments are stored in image layers and can be inspected with `docker history --no-trunc <image>`.

| Practice | Risk |
|----------|------|
| `--build-arg VAULT_SECRET_ID=xxx` | Secret visible in image layers |
| Hard-coded in `application.yml` | Secret in source control |
| `docker run -e VAULT_SECRET_ID=xxx` | **Safe** &mdash; runtime only, not persisted |
| Docker Compose `environment:` + `.env` | **Safe** &mdash; `.env` is gitignored |

The application image should contain only the `${PLACEHOLDER}` references. Actual values are injected when the container starts.

## Enabling Engines

Each VaultGlue engine is disabled by default. Enable only the engines you need:

```yaml
vault-glue:
  kv:
    enabled: true
    backend: secret
  transit:
    enabled: true
    backend: transit
```

The Database engine is the exception &mdash; it activates automatically when `vault-glue.database.sources` is defined.

## Minimal Example

Here's a complete `application.yml` that connects to Vault and uses the KV engine:

```yaml
spring:
  cloud:
    vault:
      uri: http://localhost:8200
      authentication: TOKEN
      token: hvs.xxxxx

vault-glue:
  kv:
    enabled: true
    backend: secret
    version: 2
```

```java
@Service
public class ConfigService {

    private final VaultKvOperations kv;

    public ConfigService(VaultKvOperations kv) {
        this.kv = kv;
    }

    public String getApiKey() {
        Map<String, Object> secret = kv.get("my-app/config");
        return (String) secret.get("api-key");
    }
}
```

## Global Configuration

VaultGlue provides global settings that apply across all engines:

```yaml
vault-glue:
  on-failure: retry        # restart | retry | ignore
  retry:
    max-attempts: 3
    delay: 5000            # milliseconds between retries
  actuator:
    enabled: true          # expose health indicator at /actuator/health
```

| Strategy | Behavior |
|----------|----------|
| `restart` | Triggers application context restart on failure |
| `retry` | Retries the failed operation up to `max-attempts` times |
| `ignore` | Logs the error and continues |

## Events

VaultGlue publishes Spring application events for key lifecycle moments. Listen to them with standard `@EventListener`:

```java
@Component
public class VaultEventListener {

    @EventListener
    public void onCredentialRotated(CredentialRotatedEvent event) {
        log.info("Credentials rotated for {}: {} -> {}",
            event.getIdentifier(),
            event.getOldUsername(),
            event.getNewUsername());
    }
}
```

Available events:

| Event | Fired when |
|-------|-----------|
| `CredentialRotatedEvent` | Database credentials are rotated |
| `CredentialRotationFailedEvent` | Credential rotation fails |
| `LeaseRenewedEvent` | Dynamic database lease is renewed |
| `LeaseExpiredEvent` | Dynamic database lease expires |
| `CertificateRenewedEvent` | PKI certificate is renewed |

## Next Steps

- [Database Engine](database.md) &mdash; Vault-managed database credentials
- [KV Engine](kv.md) &mdash; Secret storage and injection
- [Transit Engine](transit.md) &mdash; Encryption as a service
- [PKI Engine](pki.md) &mdash; Certificate management
- [TOTP Engine](totp.md) &mdash; One-time passwords
- [AWS Engine](aws.md) &mdash; AWS credential provisioning
- [Configuration Reference](configuration-reference.md) &mdash; All properties at a glance

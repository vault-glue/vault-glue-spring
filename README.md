# VaultGlue

[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-green)](https://spring.io/projects/spring-boot)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Spring Boot AutoConfiguration for HashiCorp Vault secret engines.
Add a starter dependency, write YAML, and you're done.

## Why VaultGlue?

[Spring Cloud Vault](https://spring.io/projects/spring-cloud-vault) handles Vault connectivity and basic KV injection well, but stops there. If you need database credential rotation, Transit encryption, PKI certificate management, TOTP, or AWS credential provisioning, you're on your own.

VaultGlue fills that gap. It builds on top of Spring Cloud Vault and turns every major Vault engine into a Spring Boot autoconfiguration &mdash; no boilerplate, no manual bean wiring.

## Features

| Engine | What it does |
|--------|-------------|
| [**Database**](docs/database.md) | Static & dynamic credential rotation, multi-DataSource, HikariCP integration |
| [**KV**](docs/kv.md) | CRUD operations, `@VaultValue` field injection, change detection polling |
| [**Transit**](docs/transit.md) | Encrypt/decrypt, batch operations, HMAC, sign/verify, JPA `VaultEncryptConverter` |
| [**PKI**](docs/pki.md) | Certificate issuance, automatic renewal scheduling, SSL context configuration |
| [**TOTP**](docs/totp.md) | OTP key creation, code generation, validation |
| [**AWS**](docs/aws.md) | STS/IAM credential provisioning with automatic rotation |

**Plus:** Event system (5 lifecycle events), failure strategy (restart/retry/ignore), Actuator health indicator.

## Quick Start

### 1. Add the dependency

**Gradle (Kotlin DSL)**
```kotlin
implementation("io.github.vault-glue:vault-glue-spring-boot-starter:0.1.1")
```

**Maven**
```xml
<dependency>
    <groupId>io.github.vault-glue</groupId>
    <artifactId>vault-glue-spring-boot-starter</artifactId>
    <version>0.1.1</version>
</dependency>
```

### 2. Configure Vault connection

VaultGlue uses Spring Cloud Vault for connectivity.

**Local development** &mdash; Token auth:
```yaml
spring:
  cloud:
    vault:
      uri: http://localhost:8200
      authentication: TOKEN
      token: hvs.xxxxx   # or use .env file (gitignored)
```

**Deployed environments (dev / staging / prod)** &mdash; AppRole with environment variables:
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

> **Warning:** Never hard-code secrets in source-controlled files or bake them into Docker images via `--build-arg`. Always inject credentials as environment variables at runtime (`docker run -e`, CI/CD pipeline secrets, etc.).

See the [Getting Started Guide](docs/getting-started.md) for detailed authentication setup and deployment examples.

### 3. Enable an engine

```yaml
vault-glue:
  transit:
    enabled: true
    backend: transit
    keys:
      user-pii:
        type: aes256-gcm96
        auto-create: true
```

### 4. Use it

```java
@Service
public class UserService {

    private final VaultTransitOperations transit;

    public UserService(VaultTransitOperations transit) {
        this.transit = transit;
    }

    public String encryptSsn(String ssn) {
        return transit.encrypt("user-pii", ssn);
    }
}
```

That's it. No `@Configuration` classes, no bean definitions.

See the [Getting Started Guide](docs/getting-started.md) for a complete walkthrough.

## Documentation

- [Getting Started](docs/getting-started.md)
- [Database Engine](docs/database.md)
- [KV Engine](docs/kv.md)
- [Transit Engine](docs/transit.md)
- [PKI Engine](docs/pki.md)
- [TOTP Engine](docs/totp.md)
- [AWS Engine](docs/aws.md)
- [Configuration Reference](docs/configuration-reference.md)

## Requirements

- Java 21+
- Spring Boot 3.5+
- Spring Cloud 2025.0+
- A running HashiCorp Vault instance

## License

[Apache License 2.0](LICENSE)

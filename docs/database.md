# Database Engine

VaultGlue manages database credentials through Vault's [Database secret engine](https://developer.hashicorp.com/vault/docs/secrets/databases). It creates HikariCP DataSources with Vault-issued credentials and handles rotation automatically.

## How It Works

1. On application startup, VaultGlue reads credentials from Vault for each configured source
2. Creates a HikariCP `DataSource` with those credentials
3. Handles credential rotation in the background (static: scheduled refresh, dynamic: lease-based)
4. Wraps each DataSource in a `VaultGlueDelegatingDataSource` that transparently swaps connections during rotation

## Prerequisites

- Vault Database secret engine enabled: `vault secrets enable database`
- Database connection and role configured in Vault
- For dynamic sources: `spring-cloud-vault-config` on the classpath (included with the starter)

## Static vs Dynamic

| | Static Role | Dynamic Role |
|---|-----------|-------------|
| Vault path | `database/static-creds/{role}` | `database/creds/{role}` |
| Credentials | Vault rotates a fixed database user's password | Vault creates a new database user per lease |
| Rotation | VaultGlue polls at `refresh-interval` | Automatic via `SecretLeaseContainer` |
| Use case | Long-lived connections, connection pooling | Short-lived, high-security environments |

## Configuration

### Single Static DataSource

The simplest setup. VaultGlue periodically refreshes the credentials from Vault:

```yaml
vault-glue:
  database:
    sources:
      primary:
        type: static
        role: my-service-static-dev
        backend: db
        jdbc-url: jdbc:mysql://db:3306/mydb
        driver-class-name: com.mysql.cj.jdbc.Driver
        refresh-interval: 18000000   # 5 hours
```

### Single Dynamic DataSource

Vault manages the full credential lifecycle through leases:

```yaml
vault-glue:
  database:
    sources:
      primary:
        type: dynamic
        role: my-service-dynamic-dev
        backend: db
        jdbc-url: jdbc:mysql://db:3306/mydb
        driver-class-name: com.mysql.cj.jdbc.Driver
```

### Multi-DataSource

Define multiple sources under `sources`. Each key name is arbitrary. Mark one as `primary: true` to register it as the default `DataSource` bean:

```yaml
vault-glue:
  database:
    sources:
      primary:
        primary: true
        type: static
        role: primary-role
        backend: db
        jdbc-url: jdbc:mysql://primary:3306/mydb
        driver-class-name: com.mysql.cj.jdbc.Driver
        refresh-interval: 18000000
      replica:
        type: dynamic
        role: replica-role
        backend: db
        jdbc-url: jdbc:mysql://replica:3306/mydb
        driver-class-name: com.mysql.cj.jdbc.Driver
      analytics:
        type: static
        role: analytics-role
        backend: db
        jdbc-url: jdbc:postgresql://analytics:5432/warehouse
        driver-class-name: org.postgresql.Driver
```

## Usage

### Single DataSource

When only one source is configured, inject `DataSource` as usual. It works transparently with JPA, MyBatis, JdbcTemplate, etc.:

```java
@Service
public class OrderService {

    private final JdbcTemplate jdbc;

    public OrderService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }
}
```

### Multiple DataSources

Inject `VaultGlueDataSources` to access named sources:

```java
@Service
public class ReportService {

    private final DataSource primary;
    private final DataSource analytics;

    public ReportService(VaultGlueDataSources dataSources) {
        this.primary = dataSources.get("primary");
        this.analytics = dataSources.get("analytics");
    }
}
```

`VaultGlueDataSources` API:

| Method | Description |
|--------|-------------|
| `get(String name)` | Returns the DataSource for the given name. Throws if not found. |
| `contains(String name)` | Returns `true` if a source with that name exists. |
| `getAll()` | Returns an unmodifiable map of all DataSources. |

### Events

Listen for credential lifecycle events:

```java
@EventListener
public void onRotation(CredentialRotatedEvent event) {
    log.info("Rotated {}: {} -> {}",
        event.getIdentifier(),
        event.getOldUsername(),
        event.getNewUsername());
}

@EventListener
public void onRotationFailed(CredentialRotationFailedEvent event) {
    log.error("Rotation failed for {}, attempt {}",
        event.getIdentifier(),
        event.getAttemptCount(),
        event.getCause());
}
```

## HikariCP Tuning

Each source supports HikariCP pool configuration:

```yaml
vault-glue:
  database:
    sources:
      primary:
        # ... role, jdbc-url, etc.
        hikari:
          maximum-pool-size: 10
          minimum-idle: 2
          max-lifetime: 1800000        # 30 minutes
          idle-timeout: 600000         # 10 minutes
          connection-timeout: 30000    # 30 seconds
          validation-timeout: 5000     # 5 seconds
          leak-detection-threshold: 0  # disabled
```

All HikariCP properties have sensible defaults. You only need to override them if your workload requires it.

## Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `sources.{name}.enabled` | boolean | `true` | Enable/disable this source |
| `sources.{name}.primary` | boolean | `false` | Register as default DataSource bean |
| `sources.{name}.type` | enum | &mdash; | `static` or `dynamic` (required) |
| `sources.{name}.role` | String | &mdash; | Vault database role name (required) |
| `sources.{name}.backend` | String | `db` | Vault mount path |
| `sources.{name}.jdbc-url` | String | &mdash; | JDBC connection URL (required) |
| `sources.{name}.driver-class-name` | String | &mdash; | JDBC driver class |
| `sources.{name}.refresh-interval` | long | `18000000` | Credential refresh interval in ms (static only) |

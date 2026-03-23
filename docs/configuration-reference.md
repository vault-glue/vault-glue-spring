# Configuration Reference

All VaultGlue properties are under the `vault-glue` prefix.

## Global

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `on-failure` | enum | `retry` | Failure strategy: `restart`, `retry`, `ignore` |
| `retry.max-attempts` | int | `3` | Maximum retry attempts |
| `retry.delay` | long | `5000` | Delay between retries (ms) |
| `actuator.enabled` | boolean | `true` | Enable Actuator health indicator |

## KV Engine

Prefix: `vault-glue.kv`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable KV engine |
| `backend` | String | `secret` | Vault mount path |
| `version` | int | `2` | KV engine version (1 or 2) |
| `application-name` | String | &mdash; | Optional path prefix |
| `watch.enabled` | boolean | `false` | Enable change detection polling |
| `watch.interval` | Duration | `30s` | Polling interval |

## Database Engine

Prefix: `vault-glue.database.sources.{name}`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable this source |
| `primary` | boolean | `false` | Register as default DataSource bean |
| `type` | enum | &mdash; | `static` or `dynamic` **(required)** |
| `role` | String | &mdash; | Vault database role name **(required)** |
| `backend` | String | `db` | Vault mount path |
| `jdbc-url` | String | &mdash; | JDBC connection URL **(required)** |
| `driver-class-name` | String | &mdash; | JDBC driver class name |
| `refresh-interval` | long | `18000000` | Credential refresh interval in ms (static only) |

### HikariCP

Prefix: `vault-glue.database.sources.{name}.hikari`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `maximum-pool-size` | int | `10` | Maximum pool size |
| `minimum-idle` | int | `2` | Minimum idle connections |
| `max-lifetime` | long | `1800000` | Max connection lifetime (ms) |
| `idle-timeout` | long | `600000` | Idle connection timeout (ms) |
| `connection-timeout` | long | `30000` | Connection timeout (ms) |
| `validation-timeout` | long | `5000` | Validation timeout (ms) |
| `leak-detection-threshold` | long | `0` | Leak detection threshold (ms, 0 = disabled) |

## Transit Engine

Prefix: `vault-glue.transit`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable Transit engine |
| `backend` | String | `transit` | Vault mount path |

### Transit Keys

Prefix: `vault-glue.transit.keys.{name}`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | String | &mdash; | Key type (see [Transit Engine](transit.md) for supported types) |
| `auto-create` | boolean | `false` | Create key on startup if it doesn't exist |

## PKI Engine

Prefix: `vault-glue.pki`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable PKI engine |
| `backend` | String | `pki` | Vault mount path |
| `role` | String | &mdash; | Vault PKI role name |
| `common-name` | String | &mdash; | Certificate common name (CN) |
| `ttl` | String | `72h` | Certificate TTL |
| `auto-renew` | boolean | `true` | Enable automatic certificate renewal |
| `configure-ssl` | boolean | `false` | Auto-configure SSL context with issued cert |
| `check-interval` | long | `3600000` | Renewal check interval (ms) |
| `renew-threshold-hours` | long | `24` | Renew when remaining hours falls below this |

## TOTP Engine

Prefix: `vault-glue.totp`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable TOTP engine |
| `backend` | String | `totp` | Vault mount path |

## AWS Engine

Prefix: `vault-glue.aws`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable AWS engine |
| `backend` | String | `aws` | Vault mount path |
| `role` | String | &mdash; | Vault AWS role name |
| `credential-type` | String | `sts` | Credential type |
| `ttl` | String | `1h` | Credential TTL |

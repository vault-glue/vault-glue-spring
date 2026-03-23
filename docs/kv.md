# KV Engine

VaultGlue provides full CRUD access to Vault's [KV secret engine](https://developer.hashicorp.com/vault/docs/secrets/kv) with annotation-based field injection and change detection.

## Prerequisites

- Vault KV secret engine enabled: `vault secrets enable -version=2 -path=secret kv`

## Configuration

```yaml
vault-glue:
  kv:
    enabled: true
    backend: secret           # Vault mount path
    version: 2                # KV version (1 or 2)
    application-name: my-app  # optional prefix for paths
    watch:
      enabled: true           # enable change detection
      interval: 30s           # polling interval
```

## Usage

### VaultKvOperations

Inject `VaultKvOperations` for programmatic access:

```java
@Service
public class SecretService {

    private final VaultKvOperations kv;

    public SecretService(VaultKvOperations kv) {
        this.kv = kv;
    }

    // Read
    public String getApiKey() {
        Map<String, Object> data = kv.get("config/api");
        return (String) data.get("key");
    }

    // Read with type mapping
    public ApiConfig getConfig() {
        return kv.get("config/api", ApiConfig.class);
    }

    // Read specific version
    public Map<String, Object> getVersion(int version) {
        return kv.get("config/api", version);
    }

    // Write
    public void saveConfig(String key, String value) {
        kv.put("config/api", Map.of("key", key, "secret", value));
    }

    // Delete (soft delete in KV v2)
    public void deleteConfig() {
        kv.delete("config/api");
    }

    // Delete specific versions
    public void deleteVersions() {
        kv.delete("config/api", 1, 2, 3);
    }

    // Restore soft-deleted versions
    public void restore() {
        kv.undelete("config/api", 1, 2);
    }

    // Permanent delete
    public void destroy() {
        kv.destroy("config/api", 1, 2);
    }

    // Metadata
    public VaultKvMetadata metadata() {
        return kv.metadata("config/api");
    }

    // List paths
    public List<String> listSecrets() {
        return kv.list("config/");
    }
}
```

### @VaultValue

Inject secret values directly into bean fields:

```java
@Component
public class AppConfig {

    @VaultValue(path = "config/api", key = "api-key")
    private String apiKey;

    @VaultValue(path = "config/api", key = "timeout", defaultValue = "5000")
    private String timeout;

    @VaultValue(path = "config/api", key = "api-key", refresh = true)
    private String refreshableApiKey;  // updated when watch detects changes
}
```

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | String | yes | KV path to read |
| `key` | String | yes | Key within the secret data |
| `defaultValue` | String | no | Fallback if key is not found (default: `""`) |
| `refresh` | boolean | no | Auto-update when value changes (default: `false`) |

### Watch Mode

When watch is enabled, VaultGlue polls Vault for changes at the configured interval. Fields marked with `@VaultValue(refresh = true)` are automatically updated when their backing secrets change.

```yaml
vault-glue:
  kv:
    enabled: true
    backend: secret
    watch:
      enabled: true
      interval: 30s
```

No additional code is needed. The watcher compares current values against the last known state and refreshes all `@VaultValue` fields when changes are detected.

## API Reference

### VaultKvOperations

| Method | Description |
|--------|-------------|
| `get(String path)` | Read secret as `Map<String, Object>` |
| `get(String path, Class<T> type)` | Read and map to a typed object |
| `get(String path, int version)` | Read a specific version |
| `put(String path, Map<String, Object> data)` | Write secret data |
| `put(String path, Object data)` | Write a typed object |
| `delete(String path)` | Soft delete (latest version) |
| `delete(String path, int... versions)` | Soft delete specific versions |
| `undelete(String path, int... versions)` | Restore soft-deleted versions |
| `destroy(String path, int... versions)` | Permanently delete versions |
| `metadata(String path)` | Get version metadata |
| `list(String path)` | List secret paths |

### VaultKvMetadata

| Field | Type | Description |
|-------|------|-------------|
| `currentVersion` | int | Latest version number |
| `oldestVersion` | int | Oldest available version |
| `createdTime` | Instant | Creation timestamp |
| `updatedTime` | Instant | Last update timestamp |
| `customMetadata` | Map | User-defined metadata |

## Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable KV engine |
| `backend` | String | `secret` | Vault mount path |
| `version` | int | `2` | KV engine version (1 or 2) |
| `application-name` | String | &mdash; | Optional path prefix |
| `watch.enabled` | boolean | `false` | Enable change detection polling |
| `watch.interval` | Duration | `30s` | Polling interval |

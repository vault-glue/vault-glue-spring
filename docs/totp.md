# TOTP Engine

VaultGlue integrates with Vault's [TOTP secret engine](https://developer.hashicorp.com/vault/docs/secrets/totp) for generating and validating time-based one-time passwords.

## Prerequisites

- Vault TOTP secret engine enabled: `vault secrets enable totp`

## Configuration

```yaml
vault-glue:
  totp:
    enabled: true
    backend: totp
```

## Usage

### VaultTotpOperations

```java
@Service
public class TwoFactorService {

    private final VaultTotpOperations totp;

    public TwoFactorService(VaultTotpOperations totp) {
        this.totp = totp;
    }

    // Create a TOTP key for a user (returns QR code for authenticator app)
    public TotpKey setupTotp(String userId) {
        return totp.createKey(userId, "MyApp", userId + "@example.com");
    }

    // Generate a code (server-side, for testing or verification)
    public String generateCode(String userId) {
        return totp.generateCode(userId);
    }

    // Validate a code submitted by the user
    public boolean verifyCode(String userId, String code) {
        return totp.validate(userId, code);
    }

    // Remove a user's TOTP key
    public void removeTotp(String userId) {
        totp.deleteKey(userId);
    }
}
```

### TotpKey

Returned by `createKey()`:

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Key name (user identifier) |
| `barcode` | String | Base64-encoded QR code image |
| `url` | String | `otpauth://` URL for manual entry |

The `barcode` can be displayed to the user for scanning with an authenticator app (Google Authenticator, Authy, etc.).

## API Reference

### VaultTotpOperations

| Method | Description |
|--------|-------------|
| `createKey(name, issuer, accountName)` | Create a new TOTP key |
| `generateCode(name)` | Generate a TOTP code |
| `validate(name, code)` | Validate a TOTP code |
| `deleteKey(name)` | Delete a TOTP key |

## Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable TOTP engine |
| `backend` | String | `totp` | Vault mount path |

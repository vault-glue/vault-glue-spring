# Transit Engine

VaultGlue wraps Vault's [Transit secret engine](https://developer.hashicorp.com/vault/docs/secrets/transit) for encryption-as-a-service. Encrypt, decrypt, sign, and verify data without managing keys yourself.

## Prerequisites

- Vault Transit secret engine enabled: `vault secrets enable transit`

## Configuration

```yaml
vault-glue:
  transit:
    enabled: true
    backend: transit
    keys:
      user-pii:
        type: aes256-gcm96
        auto-create: true
      signing-key:
        type: ed25519
        auto-create: true
```

When `auto-create: true`, VaultGlue creates the key in Vault on application startup if it doesn't already exist.

### Supported Key Types

| Type | Value in YAML | Use case |
|------|--------------|----------|
| AES-128-GCM96 | `aes128-gcm96` | Symmetric encryption |
| AES-256-GCM96 | `aes256-gcm96` | Symmetric encryption (default) |
| ChaCha20-Poly1305 | `chacha20-poly1305` | Symmetric encryption |
| Ed25519 | `ed25519` | Signing/verification |
| ECDSA-P256 | `ecdsa-p256` | Signing/verification |
| ECDSA-P384 | `ecdsa-p384` | Signing/verification |
| ECDSA-P521 | `ecdsa-p521` | Signing/verification |
| RSA-2048 | `rsa-2048` | Encryption or signing |
| RSA-3072 | `rsa-3072` | Encryption or signing |
| RSA-4096 | `rsa-4096` | Encryption or signing |

## Usage

### VaultTransitOperations

```java
@Service
public class EncryptionService {

    private final VaultTransitOperations transit;

    public EncryptionService(VaultTransitOperations transit) {
        this.transit = transit;
    }

    // Encrypt / Decrypt
    public String protect(String plaintext) {
        return transit.encrypt("user-pii", plaintext);
    }

    public String reveal(String ciphertext) {
        return transit.decrypt("user-pii", ciphertext);
    }

    // With context (key derivation)
    public String encryptWithContext(String plaintext, String userId) {
        return transit.encrypt("user-pii", plaintext, userId);
    }

    // Batch operations
    public List<String> encryptAll(List<String> values) {
        return transit.encryptBatch("user-pii", values);
    }

    public List<String> decryptAll(List<String> ciphertexts) {
        return transit.decryptBatch("user-pii", ciphertexts);
    }

    // Rewrap (re-encrypt with latest key version without exposing plaintext)
    public String rewrap(String ciphertext) {
        return transit.rewrap("user-pii", ciphertext);
    }

    // HMAC
    public String hmac(String data) {
        return transit.hmac("user-pii", data);
    }

    public boolean verifyHmac(String data, String hmac) {
        return transit.verifyHmac("user-pii", data, hmac);
    }

    // Sign / Verify (requires ed25519, ecdsa, or rsa key)
    public String sign(String data) {
        return transit.sign("signing-key", data);
    }

    public boolean verify(String data, String signature) {
        return transit.verify("signing-key", data, signature);
    }

    // Key management
    public void rotateKey() {
        transit.rotateKey("user-pii");
    }

    public TransitKeyInfo keyInfo() {
        return transit.getKeyInfo("user-pii");
    }
}
```

### JPA Integration (VaultEncryptConverter)

Automatically encrypt and decrypt JPA entity fields at the persistence layer using `@Convert`:

```java
@Entity
public class User {

    @Id
    private Long id;

    private String name;

    @Convert(converter = VaultEncryptConverter.class)
    private String ssn;           // encrypted before INSERT, decrypted after SELECT

    @Convert(converter = VaultEncryptConverter.class)
    private String email;         // uses the configured default transit key
}
```

The converter stores data in the format `vg:{keyName}:vault:v1:{ciphertext}` so VaultGlue knows which key was used for encryption. The default key is configured via `vault-glue.transit.default-key`.

No additional JPA configuration is needed. VaultGlue registers the converter automatically.

## API Reference

### VaultTransitOperations

| Method | Description |
|--------|-------------|
| `encrypt(keyName, plaintext)` | Encrypt a string |
| `encrypt(keyName, plaintext, context)` | Encrypt with key derivation context |
| `decrypt(keyName, ciphertext)` | Decrypt a string |
| `decrypt(keyName, ciphertext, context)` | Decrypt with context |
| `encryptBatch(keyName, plaintexts)` | Batch encrypt |
| `decryptBatch(keyName, ciphertexts)` | Batch decrypt |
| `rewrap(keyName, ciphertext)` | Re-encrypt with latest key version |
| `rewrapBatch(keyName, ciphertexts)` | Batch rewrap |
| `hmac(keyName, data)` | Generate HMAC |
| `hmac(keyName, data, algorithm)` | Generate HMAC with specific algorithm |
| `verifyHmac(keyName, data, hmac)` | Verify HMAC |
| `sign(keyName, data)` | Sign data |
| `sign(keyName, data, hashAlgorithm, signatureAlgorithm)` | Sign with specific algorithms |
| `verify(keyName, data, signature)` | Verify signature |
| `createKey(keyName, type)` | Create a new transit key |
| `rotateKey(keyName)` | Rotate key to new version |
| `getKeyInfo(keyName)` | Get key metadata |

## Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable Transit engine |
| `backend` | String | `transit` | Vault mount path |
| `keys.{name}.type` | String | &mdash; | Key type (see supported types above) |
| `keys.{name}.auto-create` | boolean | `false` | Create key on startup if missing |

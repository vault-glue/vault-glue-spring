# PKI Engine

VaultGlue integrates with Vault's [PKI secret engine](https://developer.hashicorp.com/vault/docs/secrets/pki) for X.509 certificate issuance and automatic renewal.

## Prerequisites

- Vault PKI secret engine enabled: `vault secrets enable pki`
- Root or intermediate CA configured in Vault
- PKI role created: `vault write pki/roles/my-role ...`

## Configuration

```yaml
vault-glue:
  pki:
    enabled: true
    backend: pki
    role: my-pki-role
    common-name: app.example.com
    ttl: 72h
    auto-renew: true
    configure-ssl: false
    check-interval: 3600000       # 1 hour
    renew-threshold-hours: 24     # renew when < 24h remaining
```

## Usage

### VaultPkiOperations

```java
@Service
public class CertificateService {

    private final VaultPkiOperations pki;

    public CertificateService(VaultPkiOperations pki) {
        this.pki = pki;
    }

    // Issue a certificate using configured role and common name
    public CertificateBundle issue() {
        return pki.issue("my-pki-role", "app.example.com");
    }

    // Issue with custom TTL
    public CertificateBundle issueWithTtl() {
        return pki.issue("my-pki-role", "app.example.com", Duration.ofHours(48));
    }

    // Issue with full options (SANs, IP SANs)
    public CertificateBundle issueWithSans() {
        PkiIssueRequest request = PkiIssueRequest
            .builder("my-pki-role", "app.example.com")
            .altNames(List.of("api.example.com", "admin.example.com"))
            .ipSans(List.of("10.0.0.1"))
            .ttl(Duration.ofHours(72))
            .build();
        return pki.issue(request);
    }

    // Revoke a certificate
    public void revoke(String serialNumber) {
        pki.revoke(serialNumber);
    }

    // Get the current certificate (issued on startup or last renewal)
    public CertificateBundle current() {
        return pki.getCurrent();
    }
}
```

### Automatic Renewal

When `auto-renew: true`, VaultGlue runs a background scheduler that:

1. Checks the current certificate at every `check-interval`
2. If remaining validity is less than `renew-threshold-hours`, issues a new certificate
3. Publishes a `CertificateRenewedEvent` on successful renewal

```java
@EventListener
public void onRenewal(CertificateRenewedEvent event) {
    CertificateBundle cert = event.getCertificateBundle();
    log.info("Certificate renewed, serial={}, expires={}",
        cert.serialNumber(), cert.expiresAt());
}
```

### CertificateBundle

| Field | Type | Description |
|-------|------|-------------|
| `certificate` | String | PEM-encoded certificate |
| `privateKey` | String | PEM-encoded private key |
| `issuingCa` | String | PEM-encoded issuing CA |
| `caChain` | List\<String\> | Full CA chain |
| `serialNumber` | String | Certificate serial number |
| `expiresAt` | Instant | Expiration timestamp |

Utility methods:
- `getRemainingHours()` &mdash; hours until expiration
- `isExpiringSoon(long thresholdHours)` &mdash; `true` if remaining hours < threshold

## Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable PKI engine |
| `backend` | String | `pki` | Vault mount path |
| `role` | String | &mdash; | Vault PKI role name |
| `common-name` | String | &mdash; | Certificate CN |
| `ttl` | String | `72h` | Certificate TTL |
| `auto-renew` | boolean | `true` | Enable automatic renewal |
| `configure-ssl` | boolean | `false` | Auto-configure SSL context with issued cert |
| `check-interval` | long | `3600000` | Renewal check interval in ms |
| `renew-threshold-hours` | long | `24` | Renew when remaining hours falls below this |

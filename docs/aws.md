# AWS Engine

VaultGlue integrates with Vault's [AWS secret engine](https://developer.hashicorp.com/vault/docs/secrets/aws) to provision and automatically rotate AWS credentials.

## Prerequisites

- Vault AWS secret engine enabled: `vault secrets enable aws`
- AWS root credentials configured in Vault
- AWS role created: `vault write aws/roles/my-role ...`
- AWS SDK auth module on the classpath: `software.amazon.awssdk:auth`

## Configuration

```yaml
vault-glue:
  aws:
    enabled: true
    backend: aws
    role: my-aws-role
    credential-type: sts
    ttl: 1h
```

## Usage

### VaultAwsCredentialProvider

```java
@Service
public class S3Service {

    private final VaultAwsCredentialProvider awsCredentials;

    public S3Service(VaultAwsCredentialProvider awsCredentials) {
        this.awsCredentials = awsCredentials;
    }

    public void upload(String bucket, String key, byte[] data) {
        VaultAwsCredentialProvider.AwsCredential cred = awsCredentials.getCredential();

        // Use with AWS SDK
        AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
            cred.accessKey(),
            cred.secretKey(),
            cred.securityToken()
        );

        // Build S3 client with Vault-issued credentials
        S3Client s3 = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
            .build();

        s3.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).build(),
            RequestBody.fromBytes(data)
        );
    }
}
```

### AwsCredential

| Field | Type | Description |
|-------|------|-------------|
| `accessKey` | String | AWS access key ID |
| `secretKey` | String | AWS secret access key |
| `securityToken` | String | STS session token (for STS credential type) |

### Automatic Rotation

VaultGlue automatically rotates AWS credentials before they expire. The rotation lifecycle is managed internally:

1. On startup, VaultGlue requests credentials from Vault
2. Schedules renewal before the TTL expires
3. `getCredential()` always returns valid, current credentials

No manual intervention or event handling is required.

## API Reference

### VaultAwsCredentialProvider

| Method | Description |
|--------|-------------|
| `getCredential()` | Returns the current AWS credential |
| `start()` | Starts the credential rotation scheduler |
| `shutdown()` | Stops the scheduler gracefully |

`start()` and `shutdown()` are called automatically by Spring's bean lifecycle. You only need `getCredential()`.

## Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable AWS engine |
| `backend` | String | `aws` | Vault mount path |
| `role` | String | &mdash; | Vault AWS role name |
| `credential-type` | String | `sts` | Credential type (sts, iam_user, etc.) |
| `ttl` | String | `1h` | Credential TTL |

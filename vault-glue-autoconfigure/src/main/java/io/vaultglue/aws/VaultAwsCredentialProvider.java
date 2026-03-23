package io.vaultglue.aws;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

public class VaultAwsCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultAwsCredentialProvider.class);

    private final VaultTemplate vaultTemplate;
    private final VaultGlueAwsProperties properties;
    private final ScheduledExecutorService scheduler;
    private volatile AwsCredential currentCredential;

    public VaultAwsCredentialProvider(VaultTemplate vaultTemplate, VaultGlueAwsProperties properties) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vault-glue-aws-credential");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        rotate();

        long ttlMs = parseTtlMs(properties.getTtl());
        long renewalMs = (long) (ttlMs * 0.8);
        log.info("[VaultGlue] AWS credential rotation scheduled every {}ms", renewalMs);

        scheduler.scheduleAtFixedRate(this::rotate, renewalMs, renewalMs, TimeUnit.MILLISECONDS);
    }

    public AwsCredential getCredential() {
        AwsCredential cred = currentCredential;
        if (cred == null) {
            throw new IllegalStateException(
                    "[VaultGlue] AWS credential not yet available. Ensure start() has been called.");
        }
        return cred;
    }

    private void rotate() {
        try {
            String credentialType = properties.getCredentialType();
            // Only iam_user uses /creds/ (GET); others (assumed_role, federation_token, sts) use /sts/ (POST)
            boolean useStdEndpoint = "iam_user".equals(credentialType);
            String credType = useStdEndpoint ? "creds" : "sts";
            String path = properties.getBackend() + "/" + credType + "/" + properties.getRole();

            log.debug("[VaultGlue] Fetching AWS credential from: {} (type={})", path, credentialType);
            VaultResponse response = useStdEndpoint
                    ? vaultTemplate.read(path)
                    : vaultTemplate.write(path, Map.of("ttl", properties.getTtl()));

            if (response == null || response.getData() == null) {
                throw new RuntimeException("[VaultGlue] Failed to fetch AWS credential from: " + path);
            }

            Map<String, Object> data = response.getData();
            String accessKey = (String) data.get("access_key");
            String secretKey = (String) data.get("secret_key");
            if (accessKey == null || secretKey == null) {
                throw new RuntimeException("[VaultGlue] AWS credential response missing access_key or secret_key");
            }

            String securityToken = (String) data.get("security_token");
            boolean isStsType = !"iam_user".equals(properties.getCredentialType());
            if (isStsType && (securityToken == null || securityToken.isBlank())) {
                throw new RuntimeException(
                        "[VaultGlue] AWS STS credential response missing security_token for type: "
                        + properties.getCredentialType());
            }

            currentCredential = new AwsCredential(accessKey, secretKey, securityToken);

            log.info("[VaultGlue] AWS credential rotated: accessKey={}...",
                    accessKey.substring(0, Math.min(8, accessKey.length())));
        } catch (RuntimeException e) {
            log.error("[VaultGlue] AWS credential rotation failed", e);
            throw e;
        }
    }

    private long parseTtlMs(String ttl) {
        if (ttl.endsWith("d")) {
            return Long.parseLong(ttl.replace("d", "")) * 86_400_000;
        } else if (ttl.endsWith("h")) {
            return Long.parseLong(ttl.replace("h", "")) * 3_600_000;
        } else if (ttl.endsWith("m")) {
            return Long.parseLong(ttl.replace("m", "")) * 60_000;
        } else if (ttl.endsWith("s")) {
            return Long.parseLong(ttl.replace("s", "")) * 1_000;
        }
        return 3_600_000; // default 1h
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    public record AwsCredential(String accessKey, String secretKey, String securityToken) {}
}

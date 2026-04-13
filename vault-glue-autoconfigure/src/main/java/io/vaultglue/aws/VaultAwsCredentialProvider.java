package io.vaultglue.aws;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueTimeUtils;

public class VaultAwsCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultAwsCredentialProvider.class);
    private static final double RENEWAL_THRESHOLD_RATIO = 0.8;

    private final VaultTemplate vaultTemplate;
    private final VaultGlueAwsProperties properties;
    private final FailureStrategyHandler failureStrategyHandler;
    private final ScheduledExecutorService scheduler;
    private volatile AwsCredential currentCredential;

    public VaultAwsCredentialProvider(VaultTemplate vaultTemplate,
                                       VaultGlueAwsProperties properties,
                                       FailureStrategyHandler failureStrategyHandler) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
        this.failureStrategyHandler = failureStrategyHandler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vault-glue-aws-credential");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (properties.getRole() == null || properties.getRole().isBlank()) {
            throw new IllegalStateException(
                    "[VaultGlue] AWS 'vault-glue.aws.role' is required");
        }

        try {
            rotate();
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to fetch initial AWS credential. Scheduler will retry on next cycle.", e);
        }

        long ttlMs = VaultGlueTimeUtils.parseTtlMs(properties.getTtl(), 3_600_000);
        long renewalMs = (long) (ttlMs * RENEWAL_THRESHOLD_RATIO);
        log.info("[VaultGlue] AWS credential rotation scheduled every {}ms", renewalMs);

        scheduler.scheduleWithFixedDelay(this::scheduledRotate, renewalMs, renewalMs, TimeUnit.MILLISECONDS);
    }

    private void scheduledRotate() {
        try {
            rotate();
        } catch (Exception e) {
            log.error("[VaultGlue] AWS credential rotation failed", e);
            failureStrategyHandler.handle("aws", properties.getRole(), e, () -> {
                rotate();
                return null;
            });
        }
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
                throw new VaultAwsException("[VaultGlue] Failed to fetch AWS credential from: " + path);
            }

            Map<String, Object> data = response.getData();
            String accessKey = (String) data.get("access_key");
            String secretKey = (String) data.get("secret_key");
            if (accessKey == null || secretKey == null) {
                throw new VaultAwsException("[VaultGlue] AWS credential response missing access_key or secret_key");
            }

            String securityToken = (String) data.get("security_token");
            boolean isStsType = !"iam_user".equals(properties.getCredentialType());
            if (isStsType && (securityToken == null || securityToken.isBlank())) {
                throw new VaultAwsException(
                        "[VaultGlue] AWS STS credential response missing security_token for type: "
                        + properties.getCredentialType());
            }

            currentCredential = new AwsCredential(accessKey, secretKey, securityToken);

            log.info("[VaultGlue] AWS credential rotated: accessKey={}...",
                    accessKey.substring(0, Math.min(4, accessKey.length())));
        } catch (RuntimeException e) {
            log.error("[VaultGlue] AWS credential rotation failed", e);
            throw e;
        }
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

    public record AwsCredential(String accessKey, String secretKey, String securityToken) {
        @Override
        public String toString() {
            return "AwsCredential[accessKey="
                    + accessKey.substring(0, Math.min(4, accessKey.length()))
                    + "..., secretKey=***masked***, securityToken=***masked***]";
        }
    }
}

package io.vaultglue.database.dynamic;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.event.LeaseExpiredEvent;
import io.vaultglue.core.event.LeaseRenewedEvent;
import io.vaultglue.database.DataSourceRotator;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.VaultGlueDelegatingDataSource;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.AfterSecretLeaseRenewedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

public class DynamicLeaseListener {

    private static final Logger log = LoggerFactory.getLogger(DynamicLeaseListener.class);

    private final SecretLeaseContainer leaseContainer;
    private final DataSourceRotator rotator;
    private final VaultGlueEventPublisher eventPublisher;
    private final FailureStrategyHandler failureStrategyHandler;
    private final VaultTemplate vaultTemplate;

    public DynamicLeaseListener(SecretLeaseContainer leaseContainer,
                                 DataSourceRotator rotator,
                                 VaultGlueEventPublisher eventPublisher,
                                 FailureStrategyHandler failureStrategyHandler,
                                 VaultTemplate vaultTemplate) {
        this.leaseContainer = leaseContainer;
        this.rotator = rotator;
        this.eventPublisher = eventPublisher;
        this.failureStrategyHandler = failureStrategyHandler;
        this.vaultTemplate = vaultTemplate;
    }

    public void register(String name, VaultGlueDelegatingDataSource delegating,
                         DataSourceProperties props) {
        String credPath = props.getBackend() + "/creds/" + props.getRole();
        log.info("[VaultGlue] Registering dynamic lease listener for '{}' at path: {}", name, credPath);

        leaseContainer.addLeaseListener(event -> {
            String path = event.getSource().getPath();
            if (!path.equals(credPath)) return;

            if (event instanceof SecretLeaseCreatedEvent created) {
                handleCreated(name, created);
            } else if (event instanceof AfterSecretLeaseRenewedEvent renewed) {
                handleRenewed(name, renewed);
            } else if (event instanceof SecretLeaseExpiredEvent expired) {
                handleExpired(name, delegating, props, expired);
            }
        });

        leaseContainer.addErrorListener((requestedSecret, exception) -> {
            String path = requestedSecret.getSource().getPath();
            if (!path.equals(credPath)) return;

            handleError(name, delegating, props, exception);
        });

        leaseContainer.addRequestedSecret(
                RequestedSecret.renewable(credPath));
    }

    private void handleCreated(String name, SecretLeaseCreatedEvent event) {
        log.info("[VaultGlue] Lease created for '{}': leaseId={}, duration={}s",
                name,
                event.getLease() != null ? event.getLease().getLeaseId() : "N/A",
                event.getLease() != null ? event.getLease().getLeaseDuration().getSeconds() : "N/A");
    }

    private void handleRenewed(String name, AfterSecretLeaseRenewedEvent event) {
        String leaseId = event.getLease() != null ? event.getLease().getLeaseId() : "N/A";
        Duration remaining = event.getLease() != null ? event.getLease().getLeaseDuration() : Duration.ZERO;

        log.info("[VaultGlue] Lease renewed for '{}': remaining={}s", name, remaining.getSeconds());

        eventPublisher.publish(new LeaseRenewedEvent(
                this, "database", name, leaseId, remaining));
    }

    private void handleExpired(String name, VaultGlueDelegatingDataSource delegating,
                                DataSourceProperties props, SecretLeaseExpiredEvent event) {
        String leaseId = event.getLease() != null ? event.getLease().getLeaseId() : "N/A";
        log.warn("[VaultGlue] Lease expired for '{}': leaseId={}", name, leaseId);

        eventPublisher.publish(new LeaseExpiredEvent(this, "database", name, leaseId));

        try {
            // Lease 만료 시 Vault에서 새 credential 직접 조회
            String credPath = props.getBackend() + "/creds/" + props.getRole();
            var response = vaultTemplate.read(credPath);
            if (response != null && response.getData() != null) {
                String username = (String) response.getData().get("username");
                String password = (String) response.getData().get("password");
                if (username != null && password != null) {
                    rotator.rotate(delegating, props, username, password, Duration.ZERO);
                    return;
                }
            }
            log.warn("[VaultGlue] Failed to get new credentials for '{}' on lease expiry", name);
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to rotate DataSource on lease expiry for '{}'", name, e);
            failureStrategyHandler.handle("database", name, e, () -> null);
        }
    }

    private void handleError(String name, VaultGlueDelegatingDataSource delegating,
                              DataSourceProperties props, Exception exception) {
        log.error("[VaultGlue] Lease error for '{}': {}", name, exception.getMessage());
        failureStrategyHandler.handle("database", name, exception, () -> null);
    }
}

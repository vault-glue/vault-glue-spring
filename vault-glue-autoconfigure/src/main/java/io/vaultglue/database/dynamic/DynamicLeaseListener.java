package io.vaultglue.database.dynamic;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.event.LeaseExpiredEvent;
import io.vaultglue.core.event.LeaseRenewedEvent;
import io.vaultglue.database.DataSourceRotator;
import io.vaultglue.database.VaultDatabaseException;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.VaultGlueDelegatingDataSource;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public DynamicLeaseListener(SecretLeaseContainer leaseContainer,
                                 DataSourceRotator rotator,
                                 VaultGlueEventPublisher eventPublisher,
                                 FailureStrategyHandler failureStrategyHandler) {
        this.leaseContainer = leaseContainer;
        this.rotator = rotator;
        this.eventPublisher = eventPublisher;
        this.failureStrategyHandler = failureStrategyHandler;
    }

    /**
     * Registers a lease listener with SecretLeaseContainer to receive the initial credential
     * and delegate lease management. Blocks up to 30 seconds until the initial credential is ready.
     */
    public void register(String name, VaultGlueDelegatingDataSource delegating,
                         DataSourceProperties props) {
        String credPath = props.getBackend() + "/creds/" + props.getRole();
        log.info("[VaultGlue] Registering dynamic lease listener for '{}' at path: {}", name, credPath);

        CountDownLatch initialLatch = new CountDownLatch(1);
        AtomicReference<Exception> initialError = new AtomicReference<>();

        leaseContainer.addLeaseListener(event -> {
            String path = event.getSource().getPath();
            if (!path.equals(credPath)) return;

            if (event instanceof SecretLeaseCreatedEvent created) {
                handleCreated(name, delegating, props, created, initialLatch, initialError);
            } else if (event instanceof AfterSecretLeaseRenewedEvent renewed) {
                handleRenewed(name, renewed);
            } else if (event instanceof SecretLeaseExpiredEvent expired) {
                handleExpired(name, credPath, expired);
            }
        });

        leaseContainer.addErrorListener((requestedSecret, exception) -> {
            String path = requestedSecret.getSource().getPath();
            if (!path.equals(credPath)) return;

            handleError(name, exception);
            initialError.compareAndSet(null, exception);
            initialLatch.countDown(); // Fail fast instead of waiting 30s
        });

        // SecretLeaseContainer handles credential creation and lease tracking
        leaseContainer.addRequestedSecret(RequestedSecret.rotating(credPath));

        // Wait until the initial credential is ready
        try {
            if (!initialLatch.await(30, TimeUnit.SECONDS)) {
                throw new VaultDatabaseException(
                        "[VaultGlue] Timeout waiting for initial dynamic credential for '" + name + "'");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VaultDatabaseException("[VaultGlue] Interrupted waiting for credential for '" + name + "'", e);
        }

        // Check if the latch was released due to an error
        Exception error = initialError.get();
        if (error != null) {
            throw new VaultDatabaseException(
                    "[VaultGlue] Failed to obtain initial dynamic credential for '" + name + "'", error);
        }
    }

    private void handleCreated(String name, VaultGlueDelegatingDataSource delegating,
                                DataSourceProperties props, SecretLeaseCreatedEvent event,
                                CountDownLatch initialLatch,
                                AtomicReference<Exception> initialError) {
        Duration leaseDuration = event.getLease() != null
                ? event.getLease().getLeaseDuration() : Duration.ZERO;
        String leaseId = event.getLease() != null
                ? event.getLease().getLeaseId() : "N/A";

        log.info("[VaultGlue] Lease created for '{}': duration={}s", name, leaseDuration.getSeconds());
        log.debug("[VaultGlue] Lease details for '{}': leaseId={}", name, leaseId);

        // Extract credentials from SecretLeaseCreatedEvent.getBody()
        Map<String, Object> body = event.getSecrets();
        if (body == null) {
            log.error("[VaultGlue] No credential body in lease event for '{}'", name);
            initialError.compareAndSet(null,
                    new RuntimeException("[VaultGlue] No credential body in lease event for '" + name + "'"));
            initialLatch.countDown();
            return;
        }

        String username = (String) body.get("username");
        String password = (String) body.get("password");

        if (username == null || password == null) {
            log.error("[VaultGlue] Missing username/password in lease event for '{}'", name);
            initialError.compareAndSet(null,
                    new RuntimeException("[VaultGlue] Missing username/password in lease event for '" + name + "'"));
            initialLatch.countDown();
            return;
        }

        try {
            rotator.rotate(delegating, props, username, password, leaseDuration);
            log.info("[VaultGlue] DataSource '{}' rotated via lease", name);
            log.debug("[VaultGlue] DataSource '{}' lease credential: user={}", name, username);
            initialLatch.countDown();
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to rotate DataSource '{}' on lease creation", name, e);
            initialError.compareAndSet(null, e);
            initialLatch.countDown();
            failureStrategyHandler.handle("database", name, e, () -> {
                rotator.rotate(delegating, props, username, password, leaseDuration);
                return null;
            });
        }
    }

    private void handleRenewed(String name, AfterSecretLeaseRenewedEvent event) {
        String leaseId = event.getLease() != null ? event.getLease().getLeaseId() : "N/A";
        Duration remaining = event.getLease() != null
                ? event.getLease().getLeaseDuration() : Duration.ZERO;

        log.info("[VaultGlue] Lease renewed for '{}': remaining={}s", name, remaining.getSeconds());

        eventPublisher.publish(new LeaseRenewedEvent(
                this, "database", name, leaseId, remaining));
    }

    private void handleExpired(String name, String credPath, SecretLeaseExpiredEvent event) {
        String leaseId = event.getLease() != null ? event.getLease().getLeaseId() : "N/A";
        log.warn("[VaultGlue] Lease expired for '{}'", name);
        log.debug("[VaultGlue] Expired lease details for '{}': leaseId={}", name, leaseId);

        eventPublisher.publish(new LeaseExpiredEvent(this, "database", name, leaseId));

        // SecretLeaseContainer is in rotating mode, so it automatically requests new credentials
        // and fires SecretLeaseCreatedEvent — rotation is handled in handleCreated
        log.info("[VaultGlue] Waiting for SecretLeaseContainer to rotate credential for '{}'", name);
    }

    private void handleError(String name, Exception exception) {
        log.error("[VaultGlue] Lease error for '{}': {}", name, exception.getMessage());
    }
}

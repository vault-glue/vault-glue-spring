package io.vaultglue.pki;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.VaultGlueTimeUtils;
import io.vaultglue.core.event.CertificateRenewedEvent;

public class CertificateRenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertificateRenewalScheduler.class);

    private final VaultPkiOperations pkiOperations;
    private final VaultGluePkiProperties properties;
    private final VaultGlueEventPublisher eventPublisher;
    private final FailureStrategyHandler failureStrategyHandler;
    private final ScheduledExecutorService scheduler;

    public CertificateRenewalScheduler(VaultPkiOperations pkiOperations,
                                        VaultGluePkiProperties properties,
                                        VaultGlueEventPublisher eventPublisher,
                                        FailureStrategyHandler failureStrategyHandler) {
        this.pkiOperations = pkiOperations;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.failureStrategyHandler = failureStrategyHandler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vault-glue-pki-renewal");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!properties.isAutoRenew()) {
            log.info("[VaultGlue] PKI auto-renewal disabled");
            return;
        }

        if (properties.getRole() == null || properties.getRole().isBlank()) {
            throw new IllegalStateException("[VaultGlue] PKI 'vault-glue.pki.role' is required when auto-renew is enabled");
        }
        if (properties.getCommonName() == null || properties.getCommonName().isBlank()) {
            throw new IllegalStateException("[VaultGlue] PKI 'vault-glue.pki.common-name' is required when auto-renew is enabled");
        }

        // Initial issue
        try {
            pkiOperations.issue(properties.getRole(), properties.getCommonName(), getEffectiveTtl());
            log.info("[VaultGlue] Initial certificate issued");
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to issue initial certificate", e);
            failureStrategyHandler.handle("pki", properties.getCommonName(), e, () -> {
                pkiOperations.issue(properties.getRole(), properties.getCommonName(), getEffectiveTtl());
                return null;
            });
        }

        long interval = properties.getCheckInterval();
        log.info("[VaultGlue] PKI renewal scheduler started (check every {}ms)", interval);

        scheduler.scheduleWithFixedDelay(
                this::checkAndRenew,
                interval, interval, TimeUnit.MILLISECONDS
        );
    }

    private void checkAndRenew() {
        try {
            doCheckAndRenew();
        } catch (Exception e) {
            log.error("[VaultGlue] Certificate renewal check failed", e);
            failureStrategyHandler.handle("pki", properties.getCommonName(), e, () -> {
                doCheckAndRenew();
                return null;
            });
        }
    }

    private void doCheckAndRenew() {
        CertificateBundle current = pkiOperations.getCurrent();
        if (current == null || current.isExpiringSoon(properties.getRenewThresholdHours())) {
            log.info("[VaultGlue] Certificate expiring soon (remaining={}h), renewing...",
                    current != null ? current.getRemainingHours() : 0);

            // Revoke the previous certificate
            if (current != null && current.serialNumber() != null) {
                try {
                    pkiOperations.revoke(current.serialNumber());
                    log.info("[VaultGlue] Previous certificate revoked: serial={}",
                            current.serialNumber());
                } catch (Exception revokeEx) {
                    log.warn("[VaultGlue] Failed to revoke previous certificate: serial={}",
                            current.serialNumber(), revokeEx);
                }
            }

            CertificateBundle renewed = pkiOperations.issue(
                    properties.getRole(),
                    properties.getCommonName(),
                    getEffectiveTtl());

            eventPublisher.publish(new CertificateRenewedEvent(
                    this, "pki", properties.getCommonName(), renewed));

            log.info("[VaultGlue] Certificate renewed: serial={}, expires={}",
                    renewed.serialNumber(), renewed.expiresAt());
        }
    }

    private Duration getEffectiveTtl() {
        return VaultGlueTimeUtils.parseTtl(properties.getTtl(), Duration.ofHours(72));
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
}

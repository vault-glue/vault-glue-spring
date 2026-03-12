package io.vaultglue.pki;

import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.event.CertificateRenewedEvent;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertificateRenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertificateRenewalScheduler.class);

    private final VaultPkiOperations pkiOperations;
    private final VaultGluePkiProperties properties;
    private final VaultGlueEventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler;

    public CertificateRenewalScheduler(VaultPkiOperations pkiOperations,
                                        VaultGluePkiProperties properties,
                                        VaultGlueEventPublisher eventPublisher) {
        this.pkiOperations = pkiOperations;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
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

        // Initial issue
        try {
            pkiOperations.issue(properties.getRole(), properties.getCommonName(),
                    parseTtl(properties.getTtl()));
            log.info("[VaultGlue] Initial certificate issued");
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to issue initial certificate", e);
        }

        long interval = properties.getCheckInterval();
        log.info("[VaultGlue] PKI renewal scheduler started (check every {}ms)", interval);

        scheduler.scheduleAtFixedRate(
                this::checkAndRenew,
                interval, interval, TimeUnit.MILLISECONDS
        );
    }

    private void checkAndRenew() {
        try {
            CertificateBundle current = pkiOperations.getCurrent();
            if (current == null || current.isExpiringSoon(properties.getRenewThresholdHours())) {
                log.info("[VaultGlue] Certificate expiring soon (remaining={}h), renewing...",
                        current != null ? current.getRemainingHours() : 0);

                CertificateBundle renewed = pkiOperations.issue(
                        properties.getRole(),
                        properties.getCommonName(),
                        parseTtl(properties.getTtl()));

                eventPublisher.publish(new CertificateRenewedEvent(
                        this, "pki", properties.getCommonName(), renewed));

                log.info("[VaultGlue] Certificate renewed: serial={}, expires={}",
                        renewed.serialNumber(), renewed.expiresAt());
            }
        } catch (Exception e) {
            log.error("[VaultGlue] Certificate renewal check failed", e);
        }
    }

    private Duration parseTtl(String ttl) {
        if (ttl.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(ttl.replace("h", "")));
        } else if (ttl.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(ttl.replace("m", "")));
        } else if (ttl.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(ttl.replace("s", "")));
        }
        return Duration.ofHours(72);
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

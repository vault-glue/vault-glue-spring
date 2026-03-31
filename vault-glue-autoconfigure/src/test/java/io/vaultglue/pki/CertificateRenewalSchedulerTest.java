package io.vaultglue.pki;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateRenewalSchedulerTest {

    @Test
    void checkAndRenew_shouldCallFailureStrategyOnError() {
        VaultPkiOperations pkiOperations = mock(VaultPkiOperations.class);
        VaultGlueEventPublisher eventPublisher = mock(VaultGlueEventPublisher.class);
        FailureStrategyHandler failureStrategyHandler = mock(FailureStrategyHandler.class);

        VaultGluePkiProperties properties = new VaultGluePkiProperties();
        properties.setAutoRenew(true);
        properties.setRole("test-role");
        properties.setCommonName("test.example.com");
        properties.setTtl("72h");
        properties.setCheckInterval(100);
        properties.setRenewThresholdHours(24);

        // Initial issue succeeds
        when(pkiOperations.issue(any(), any(), any())).thenReturn(null);
        // getCurrent throws on renewal check
        when(pkiOperations.getCurrent()).thenThrow(new RuntimeException("Vault connection refused"));

        CertificateRenewalScheduler scheduler = new CertificateRenewalScheduler(
                pkiOperations, properties, eventPublisher, failureStrategyHandler);
        scheduler.start();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(failureStrategyHandler, atLeastOnce())
                        .handle(eq("PKI"), eq("test.example.com"), any(), any()));

        scheduler.shutdown();
    }
}

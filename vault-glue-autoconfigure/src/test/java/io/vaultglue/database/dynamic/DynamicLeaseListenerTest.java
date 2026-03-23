package io.vaultglue.database.dynamic;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.database.DataSourceRotator;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.VaultGlueDelegatingDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.vault.core.lease.SecretLeaseContainer;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicLeaseListenerTest {

    private SecretLeaseContainer leaseContainer;
    private DataSourceRotator rotator;
    private VaultGlueEventPublisher eventPublisher;
    private FailureStrategyHandler failureStrategyHandler;
    private DynamicLeaseListener listener;

    @BeforeEach
    void setUp() {
        leaseContainer = Mockito.mock(SecretLeaseContainer.class);
        rotator = Mockito.mock(DataSourceRotator.class);
        eventPublisher = Mockito.mock(VaultGlueEventPublisher.class);
        failureStrategyHandler = Mockito.mock(FailureStrategyHandler.class);
        listener = new DynamicLeaseListener(leaseContainer, rotator, eventPublisher, failureStrategyHandler);
    }

    @Test
    void register_shouldTimeoutWhenNoCredentialsArrive() {
        DataSourceProperties props = new DataSourceProperties();
        props.setBackend("db");
        props.setRole("test-role");

        VaultGlueDelegatingDataSource delegating = Mockito.mock(VaultGlueDelegatingDataSource.class);

        assertThrows(RuntimeException.class,
                () -> listener.register("test", delegating, props));
    }
}

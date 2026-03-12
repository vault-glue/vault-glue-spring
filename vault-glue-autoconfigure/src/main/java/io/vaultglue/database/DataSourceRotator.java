package io.vaultglue.database;

import com.zaxxer.hikari.HikariDataSource;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.event.CredentialRotatedEvent;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSourceRotator {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRotator.class);

    private final VaultGlueEventPublisher eventPublisher;

    public DataSourceRotator(VaultGlueEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public synchronized void rotate(VaultGlueDelegatingDataSource delegating,
                                     DataSourceProperties props,
                                     String newUsername, String newPassword,
                                     Duration leaseDuration) {
        String name = delegating.getName();
        String oldUsername = delegating.getCurrentUsername();

        log.info("[VaultGlue] Rotating DataSource '{}': {} -> {}", name, oldUsername, newUsername);

        DataSource oldDelegate = delegating.getDelegate();

        HikariDataSource newDataSource = HikariDataSourceFactory.create(props, newUsername, newPassword);
        delegating.setDelegate(newDataSource, newUsername);

        log.info("[VaultGlue] DataSource '{}' rotated. New pool: {}", name, newDataSource.getPoolName());

        if (oldDelegate instanceof HikariDataSource hikari) {
            GracefulShutdown.execute(hikari);
        }

        eventPublisher.publish(new CredentialRotatedEvent(
                this, "database", name, oldUsername, newUsername, leaseDuration));
    }
}

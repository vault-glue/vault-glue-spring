package io.vaultglue.database;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.event.CredentialRotatedEvent;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;

public class DataSourceRotator {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRotator.class);

    private final VaultGlueEventPublisher eventPublisher;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public DataSourceRotator(VaultGlueEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void rotate(VaultGlueDelegatingDataSource delegating,
                                     DataSourceProperties props,
                                     String newUsername, String newPassword,
                                     Duration leaseDuration) {
        String name = delegating.getName();
        Object lock = locks.computeIfAbsent(name, k -> new Object());
        synchronized (lock) {
            doRotate(delegating, props, name, newUsername, newPassword, leaseDuration);
        }
    }

    private void doRotate(VaultGlueDelegatingDataSource delegating, DataSourceProperties props,
                           String name, String newUsername, String newPassword, Duration leaseDuration) {
        String oldUsername = delegating.getCurrentUsername();

        log.info("[VaultGlue] Rotating DataSource '{}'", name);
        log.debug("[VaultGlue] DataSource '{}' credential change: {} -> {}", name, oldUsername, newUsername);

        DataSource oldDelegate = delegating.getDelegate();

        HikariDataSource newDataSource = HikariDataSourceFactory.create(name, props, newUsername, newPassword);
        delegating.setDelegate(newDataSource, newUsername);

        log.info("[VaultGlue] DataSource '{}' rotated. New pool: {}", name, newDataSource.getPoolName());

        if (oldDelegate instanceof HikariDataSource hikari) {
            GracefulShutdown.execute(hikari);
        }

        eventPublisher.publish(new CredentialRotatedEvent(
                this, "database", name, oldUsername, newUsername, leaseDuration));
    }
}

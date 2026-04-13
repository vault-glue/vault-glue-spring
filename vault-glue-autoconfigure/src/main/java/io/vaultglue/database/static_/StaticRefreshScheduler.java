package io.vaultglue.database.static_;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueSchedulerUtils;
import io.vaultglue.database.DataSourceRotator;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.VaultGlueDelegatingDataSource;

public class StaticRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaticRefreshScheduler.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final StaticCredentialProvider credentialProvider;
    private final DataSourceRotator rotator;
    private final FailureStrategyHandler failureStrategyHandler;
    private final List<ScheduledExecutorService> schedulers = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> executionCounts = new ConcurrentHashMap<>();

    public StaticRefreshScheduler(StaticCredentialProvider credentialProvider,
                                   DataSourceRotator rotator,
                                   FailureStrategyHandler failureStrategyHandler) {
        this.credentialProvider = credentialProvider;
        this.rotator = rotator;
        this.failureStrategyHandler = failureStrategyHandler;
    }

    public void schedule(String name, VaultGlueDelegatingDataSource delegating,
                         DataSourceProperties props) {
        long interval = props.getRefreshInterval();
        log.info("[VaultGlue] Scheduling static credential refresh for '{}' every {}ms", name, interval);

        ScheduledExecutorService scheduler = VaultGlueSchedulerUtils.createDaemonScheduler("vault-glue-static-refresh-" + name);
        schedulers.add(scheduler);

        scheduler.scheduleWithFixedDelay(
                () -> refresh(name, delegating, props),
                interval, interval, TimeUnit.MILLISECONDS
        );
    }

    private void refresh(String name, VaultGlueDelegatingDataSource delegating,
                         DataSourceProperties props) {
        int count = executionCounts.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();
        log.info("[VaultGlue] Static credential refresh #{} for '{}'", count, name);

        try {
            StaticCredentialProvider.DbCredential cred =
                    credentialProvider.getCredential(props.getBackend(), props.getRole());

            rotator.rotate(delegating, props, cred.username(), cred.password(),
                    Duration.ofMillis(props.getRefreshInterval()));

            log.info("[VaultGlue] Static credential refresh #{} completed for '{}'", count, name);
        } catch (Exception e) {
            log.error("[VaultGlue] Static credential refresh #{} failed for '{}'", count, name, e);
            failureStrategyHandler.handle("database", name, e, () -> {
                StaticCredentialProvider.DbCredential cred =
                        credentialProvider.getCredential(props.getBackend(), props.getRole());
                rotator.rotate(delegating, props, cred.username(), cred.password(),
                        Duration.ofMillis(props.getRefreshInterval()));
                return null;
            });
        }
    }

    public void shutdown() {
        for (ScheduledExecutorService scheduler : schedulers) {
            scheduler.shutdown();
        }
        for (ScheduledExecutorService scheduler : schedulers) {
            try {
                if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
    }
}

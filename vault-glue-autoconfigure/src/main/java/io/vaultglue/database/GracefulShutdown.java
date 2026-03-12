package io.vaultglue.database;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);
    private static final int DEFAULT_MAX_WAIT_SECONDS = 300;

    private GracefulShutdown() {}

    public static void execute(HikariDataSource dataSource) {
        execute(dataSource, DEFAULT_MAX_WAIT_SECONDS);
    }

    public static void execute(HikariDataSource dataSource, int maxWaitSeconds) {
        Thread.ofVirtual()
                .name("vault-glue-cleanup-" + dataSource.getPoolName())
                .start(() -> doShutdown(dataSource, maxWaitSeconds));
    }

    private static void doShutdown(HikariDataSource dataSource, int maxWaitSeconds) {
        String poolName = dataSource.getPoolName();
        try {
            log.info("[VaultGlue] Graceful shutdown started: {}", poolName);

            int waited = 0;
            while (waited < maxWaitSeconds) {
                HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
                if (pool == null || pool.getActiveConnections() == 0) {
                    log.info("[VaultGlue] All active connections returned: {} (waited {}s)",
                            poolName, waited);
                    break;
                }

                if (waited % 5 == 0) {
                    log.debug("[VaultGlue] Waiting for active connections: {} - active={}, idle={}, total={}",
                            poolName, pool.getActiveConnections(),
                            pool.getIdleConnections(), pool.getTotalConnections());
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[VaultGlue] Graceful shutdown interrupted: {}", poolName);
                    break;
                }
                waited++;
            }

            if (waited >= maxWaitSeconds) {
                log.warn("[VaultGlue] Graceful shutdown timed out after {}s: {}", maxWaitSeconds, poolName);
            }

            dataSource.close();
            log.info("[VaultGlue] Pool closed: {}", poolName);
        } catch (Exception e) {
            log.error("[VaultGlue] Graceful shutdown failed: {}", poolName, e);
        }
    }
}

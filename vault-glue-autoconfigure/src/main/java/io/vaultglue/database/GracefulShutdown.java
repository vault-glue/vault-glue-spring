package io.vaultglue.database;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);
    private static final int DEFAULT_MAX_WAIT_SECONDS = 300;
    private static final List<Thread> shutdownThreads = new CopyOnWriteArrayList<>();

    private GracefulShutdown() {}

    public static void execute(HikariDataSource dataSource) {
        execute(dataSource, DEFAULT_MAX_WAIT_SECONDS);
    }

    public static void execute(HikariDataSource dataSource, int maxWaitSeconds) {
        Thread thread = Thread.ofVirtual()
                .name("vault-glue-cleanup-" + dataSource.getPoolName())
                .unstarted(() -> {
                    try {
                        doShutdown(dataSource, maxWaitSeconds);
                    } finally {
                        shutdownThreads.remove(Thread.currentThread());
                    }
                });
        shutdownThreads.add(thread);
        thread.start();
    }

    /**
     * Waits for all in-progress graceful shutdown threads to complete.
     * Called during application shutdown to ensure old pools are properly closed.
     */
    public static void awaitAll(int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1_000L);
        for (Thread thread : shutdownThreads) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                log.warn("[VaultGlue] Graceful shutdown await timed out after {}s", timeoutSeconds);
                break;
            }
            try {
                thread.join(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[VaultGlue] Interrupted while waiting for graceful shutdown thread: {}",
                        thread.getName());
                break;
            }
        }
        shutdownThreads.clear();
    }

    private static void doShutdown(HikariDataSource dataSource, int maxWaitSeconds) {
        String poolName = dataSource.getPoolName();
        try {
            log.info("[VaultGlue] Graceful shutdown started: {}", poolName);

            // Mark all idle connections for eviction; active ones will be evicted on return
            HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();
            if (poolBean != null) {
                poolBean.softEvictConnections();
                log.debug("[VaultGlue] Soft-evicted connections for: {}", poolName);
            }

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
                    Thread.sleep(1_000);
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

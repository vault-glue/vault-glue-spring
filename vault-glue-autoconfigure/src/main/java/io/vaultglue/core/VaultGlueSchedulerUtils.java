package io.vaultglue.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shared utilities for creating and shutting down daemon schedulers used by VaultGlue engines.
 */
public class VaultGlueSchedulerUtils {

    private VaultGlueSchedulerUtils() {}

    public static ScheduledExecutorService createDaemonScheduler(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    public static void shutdownScheduler(ScheduledExecutorService scheduler, long timeoutSeconds) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}

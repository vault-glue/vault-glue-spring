package io.vaultglue.kv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaultKvWatcher {

    private static final Logger log = LoggerFactory.getLogger(VaultKvWatcher.class);

    private final VaultKvOperations kvOperations;
    private final VaultValueBeanPostProcessor beanPostProcessor;
    private final VaultGlueKvProperties properties;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Map<String, Object>> lastKnownValues = new ConcurrentHashMap<>();

    public VaultKvWatcher(VaultKvOperations kvOperations,
                          VaultValueBeanPostProcessor beanPostProcessor,
                          VaultGlueKvProperties properties) {
        this.kvOperations = kvOperations;
        this.beanPostProcessor = beanPostProcessor;
        this.properties = properties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vault-glue-kv-watcher");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        long intervalMs = properties.getWatch().getInterval().toMillis();
        log.info("[VaultGlue] KV watcher started (interval={}ms)", intervalMs);

        scheduler.scheduleAtFixedRate(
                this::pollChanges,
                intervalMs, intervalMs, TimeUnit.MILLISECONDS
        );
    }

    public void watch(String path) {
        lastKnownValues.computeIfAbsent(path, p -> {
            log.debug("[VaultGlue] Watching KV path: {}", p);
            return kvOperations.get(p);
        });
    }

    private void pollChanges() {
        try {
            boolean changed = false;
            for (var entry : lastKnownValues.entrySet()) {
                String path = entry.getKey();
                Map<String, Object> lastKnown = entry.getValue();
                Map<String, Object> current = kvOperations.get(path);

                if (!current.equals(lastKnown)) {
                    log.info("[VaultGlue] KV change detected: {}", path);
                    lastKnownValues.put(path, current);
                    changed = true;
                }
            }

            if (changed) {
                beanPostProcessor.refreshAll();
            }
        } catch (Exception e) {
            log.error("[VaultGlue] KV watch poll failed", e);
        }
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

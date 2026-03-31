package io.vaultglue.kv;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.vaultglue.core.FailureStrategyHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;

class VaultKvWatcherTest {

    @Test
    void pollChanges_shouldNotOverlapWhenVaultIsSlow() throws InterruptedException {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger callCount = new AtomicInteger(0);

        VaultKvOperations kvOperations = Mockito.mock(VaultKvOperations.class);
        Mockito.when(kvOperations.get(Mockito.anyString())).thenAnswer(inv -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
            Thread.sleep(150);
            concurrentCount.decrementAndGet();
            callCount.incrementAndGet();
            return Map.of("key", "value");
        });

        VaultValueBeanPostProcessor beanPostProcessor = Mockito.mock(VaultValueBeanPostProcessor.class);
        FailureStrategyHandler failureStrategyHandler = Mockito.mock(FailureStrategyHandler.class);

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        VaultGlueKvProperties.WatchProperties watchProperties = new VaultGlueKvProperties.WatchProperties();
        watchProperties.setInterval(Duration.ofMillis(50));
        properties.setWatch(watchProperties);

        VaultKvWatcher watcher = new VaultKvWatcher(kvOperations, beanPostProcessor, properties, failureStrategyHandler);
        watcher.watch("app/config");
        watcher.start();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAtomic(callCount, Matchers.greaterThanOrEqualTo(2));
        watcher.shutdown();

        assertEquals(1, maxConcurrent.get(), "Should never have concurrent poll executions");
    }

    @Test
    void pollChanges_shouldCallFailureStrategyOnError() {
        VaultKvOperations kvOperations = mock(VaultKvOperations.class);
        VaultValueBeanPostProcessor beanPostProcessor = mock(VaultValueBeanPostProcessor.class);
        FailureStrategyHandler failureStrategyHandler = mock(FailureStrategyHandler.class);

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        VaultGlueKvProperties.WatchProperties watchProperties = new VaultGlueKvProperties.WatchProperties();
        watchProperties.setInterval(Duration.ofMillis(100));
        properties.setWatch(watchProperties);

        // First call succeeds (for watch() to populate initial value), subsequent calls throw
        Mockito.when(kvOperations.get("app/config"))
                .thenReturn(Map.of("key", "value"))
                .thenThrow(new RuntimeException("Vault unavailable"));

        VaultKvWatcher watcher = new VaultKvWatcher(kvOperations, beanPostProcessor, properties, failureStrategyHandler);
        watcher.watch("app/config");
        watcher.start();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(failureStrategyHandler, atLeastOnce())
                        .handle(eq("KV"), eq("watch"), any(), any()));

        watcher.shutdown();
    }
}

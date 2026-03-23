package io.vaultglue.kv;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        VaultGlueKvProperties properties = new VaultGlueKvProperties();
        VaultGlueKvProperties.WatchProperties watchProperties = new VaultGlueKvProperties.WatchProperties();
        watchProperties.setInterval(Duration.ofMillis(50));
        properties.setWatch(watchProperties);

        VaultKvWatcher watcher = new VaultKvWatcher(kvOperations, beanPostProcessor, properties);
        watcher.watch("app/config");
        watcher.start();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAtomic(callCount, Matchers.greaterThanOrEqualTo(2));
        watcher.shutdown();

        assertEquals(1, maxConcurrent.get(), "Should never have concurrent poll executions");
    }
}

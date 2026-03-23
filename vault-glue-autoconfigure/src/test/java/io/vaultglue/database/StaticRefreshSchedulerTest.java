package io.vaultglue.database;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.static_.StaticCredentialProvider;
import io.vaultglue.database.static_.StaticRefreshScheduler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticRefreshSchedulerTest {

    @Test
    void schedule_shouldNotRunConcurrentRefreshes() throws InterruptedException {
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger callCount = new AtomicInteger(0);

        StaticCredentialProvider credentialProvider = Mockito.mock(StaticCredentialProvider.class);
        Mockito.when(credentialProvider.getCredential(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> {
                    int current = concurrentCount.incrementAndGet();
                    maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
                    Thread.sleep(200);
                    concurrentCount.decrementAndGet();
                    callCount.incrementAndGet();
                    return new StaticCredentialProvider.DbCredential("user", "pass");
                });

        DataSourceRotator rotator = Mockito.mock(DataSourceRotator.class);
        FailureStrategyHandler handler = Mockito.mock(FailureStrategyHandler.class);

        StaticRefreshScheduler scheduler = new StaticRefreshScheduler(credentialProvider, rotator, handler);

        DataSourceProperties props = new DataSourceProperties();
        props.setBackend("db");
        props.setRole("test");
        props.setRefreshInterval(50);

        VaultGlueDelegatingDataSource delegating = Mockito.mock(VaultGlueDelegatingDataSource.class);

        scheduler.schedule("test", delegating, props);
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAtomic(callCount, Matchers.greaterThanOrEqualTo(2));
        scheduler.shutdown();

        assertEquals(1, maxConcurrent.get(), "Should never have concurrent refreshes on same DataSource");
    }
}

package io.vaultglue.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import io.vaultglue.core.event.CredentialRotationFailedEvent;

class FailureStrategyHandlerTest {

    private VaultGlueProperties properties;
    private VaultGlueEventPublisher eventPublisher;
    private ConfigurableApplicationContext applicationContext;
    private FailureStrategyHandler handler;

    @BeforeEach
    void setUp() {
        properties = new VaultGlueProperties();
        ApplicationEventPublisher springPublisher = Mockito.mock(ApplicationEventPublisher.class);
        eventPublisher = new VaultGlueEventPublisher(springPublisher);
        applicationContext = Mockito.mock(ConfigurableApplicationContext.class);
        handler = new FailureStrategyHandler(properties, eventPublisher, applicationContext);
    }

    @Test
    void retryStrategy_shouldRetryAndSucceed() {
        properties.setOnFailure(FailureStrategy.RETRY);
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setDelay(10);

        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<Void> retryAction = () -> {
            if (callCount.incrementAndGet() < 2) {
                throw new RuntimeException("simulated failure");
            }
            return null;
        };

        handler.handle("kv", "test-key", new RuntimeException("initial"), retryAction);

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(applicationContext, Mockito.never()).close());
    }

    @Test
    void restartStrategy_shouldCloseContext() {
        properties.setOnFailure(FailureStrategy.RESTART);

        handler.handle("kv", "test-key", new RuntimeException("fatal error"), () -> null);

        Awaitility.await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(applicationContext, Mockito.times(1)).close());
    }

    @Test
    void ignoreStrategy_shouldNotCloseContext() throws InterruptedException {
        properties.setOnFailure(FailureStrategy.IGNORE);

        handler.handle("kv", "test-key", new RuntimeException("ignored error"), () -> null);

        Thread.sleep(200);
        Mockito.verify(applicationContext, Mockito.never()).close();
    }

    @Test
    void retryExhausted_shouldThrowRuntimeException() {
        properties.setOnFailure(FailureStrategy.RETRY);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setDelay(50);

        Supplier<Void> alwaysFails = () -> {
            throw new RuntimeException("always fails");
        };

        handler.handle("db", "primary", new RuntimeException("initial"), alwaysFails);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(applicationContext, Mockito.never()).close());
    }
}

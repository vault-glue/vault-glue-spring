package io.vaultglue.core;

import io.vaultglue.core.event.CredentialRotationFailedEvent;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

public class FailureStrategyHandler {

    private static final Logger log = LoggerFactory.getLogger(FailureStrategyHandler.class);

    private final VaultGlueProperties properties;
    private final VaultGlueEventPublisher eventPublisher;
    private final ConfigurableApplicationContext applicationContext;

    public FailureStrategyHandler(VaultGlueProperties properties,
                                  VaultGlueEventPublisher eventPublisher,
                                  ConfigurableApplicationContext applicationContext) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.applicationContext = applicationContext;
    }

    public void handle(String engine, String identifier, Exception cause,
                       Supplier<Void> retryAction) {
        switch (properties.getOnFailure()) {
            case RETRY -> retryWithBackoff(engine, identifier, cause, retryAction);
            case RESTART -> shutdownApplication(engine, identifier, cause);
            case IGNORE -> logAndIgnore(engine, identifier, cause);
        }
    }

    private void retryWithBackoff(String engine, String identifier, Exception cause,
                                   Supplier<Void> retryAction) {
        int maxAttempts = properties.getRetry().getMaxAttempts();
        long baseDelay = properties.getRetry().getDelay();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                long delay = baseDelay * attempt;
                log.warn("[VaultGlue] Retry {}/{} for {}/{} in {}ms",
                        attempt, maxAttempts, engine, identifier, delay);
                Thread.sleep(delay);

                retryAction.get();
                log.info("[VaultGlue] Retry succeeded for {}/{} on attempt {}",
                        engine, identifier, attempt);
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("[VaultGlue] Retry interrupted for {}/{}", engine, identifier);
                break;
            } catch (Exception retryEx) {
                eventPublisher.publish(new CredentialRotationFailedEvent(
                        this, engine, identifier, retryEx, attempt));
                log.error("[VaultGlue] Retry {}/{} failed for {}/{}",
                        attempt, maxAttempts, engine, identifier, retryEx);
            }
        }

        log.error("[VaultGlue] All {} retries exhausted for {}/{}. Continuing with stale state.",
                maxAttempts, engine, identifier, cause);
    }

    private void shutdownApplication(String engine, String identifier, Exception cause) {
        log.error("[VaultGlue] Fatal failure in {}/{}. Shutting down application.",
                engine, identifier, cause);
        applicationContext.close();
    }

    private void logAndIgnore(String engine, String identifier, Exception cause) {
        log.warn("[VaultGlue] Ignoring failure in {}/{}: {}",
                engine, identifier, cause.getMessage());
    }
}

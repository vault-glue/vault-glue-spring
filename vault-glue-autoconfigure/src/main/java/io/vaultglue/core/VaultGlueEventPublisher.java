package io.vaultglue.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

public class VaultGlueEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(VaultGlueEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public VaultGlueEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(VaultGlueEvent event) {
        log.debug("[VaultGlue] Publishing event: {} for {}/{}",
                event.getClass().getSimpleName(), event.getEngine(), event.getIdentifier());
        applicationEventPublisher.publishEvent(event);
    }
}

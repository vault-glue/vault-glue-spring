package io.vaultglue.core;

import java.time.Instant;
import org.springframework.context.ApplicationEvent;

public abstract class VaultGlueEvent extends ApplicationEvent {

    private final String engine;
    private final String identifier;
    private final Instant eventTimestamp;

    protected VaultGlueEvent(Object source, String engine, String identifier) {
        super(source);
        this.engine = engine;
        this.identifier = identifier;
        this.eventTimestamp = Instant.now();
    }

    public String getEngine() {
        return engine;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }
}

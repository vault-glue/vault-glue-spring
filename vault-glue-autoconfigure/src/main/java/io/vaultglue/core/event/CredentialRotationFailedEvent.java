package io.vaultglue.core.event;

import io.vaultglue.core.VaultGlueEvent;

public class CredentialRotationFailedEvent extends VaultGlueEvent {

    private final Exception cause;
    private final int attemptCount;

    public CredentialRotationFailedEvent(Object source, String engine, String identifier,
                                         Exception cause, int attemptCount) {
        super(source, engine, identifier);
        this.cause = cause;
        this.attemptCount = attemptCount;
    }

    public Exception getCause() {
        return cause;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}

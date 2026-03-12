package io.vaultglue.core.event;

import io.vaultglue.core.VaultGlueEvent;
import java.time.Duration;

public class CredentialRotatedEvent extends VaultGlueEvent {

    private final String oldUsername;
    private final String newUsername;
    private final Duration leaseDuration;

    public CredentialRotatedEvent(Object source, String engine, String identifier,
                                  String oldUsername, String newUsername, Duration leaseDuration) {
        super(source, engine, identifier);
        this.oldUsername = oldUsername;
        this.newUsername = newUsername;
        this.leaseDuration = leaseDuration;
    }

    public String getOldUsername() {
        return oldUsername;
    }

    public String getNewUsername() {
        return newUsername;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }
}

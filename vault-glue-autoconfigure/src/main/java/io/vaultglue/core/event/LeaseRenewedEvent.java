package io.vaultglue.core.event;

import java.time.Duration;
import io.vaultglue.core.VaultGlueEvent;

public class LeaseRenewedEvent extends VaultGlueEvent {

    private final String leaseId;
    private final Duration remainingDuration;

    public LeaseRenewedEvent(Object source, String engine, String identifier,
                             String leaseId, Duration remainingDuration) {
        super(source, engine, identifier);
        this.leaseId = leaseId;
        this.remainingDuration = remainingDuration;
    }

    public String getLeaseId() {
        return leaseId;
    }

    public Duration getRemainingDuration() {
        return remainingDuration;
    }
}

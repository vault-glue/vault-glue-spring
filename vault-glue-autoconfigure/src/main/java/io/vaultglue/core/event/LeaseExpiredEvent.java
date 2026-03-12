package io.vaultglue.core.event;

import io.vaultglue.core.VaultGlueEvent;

public class LeaseExpiredEvent extends VaultGlueEvent {

    private final String leaseId;

    public LeaseExpiredEvent(Object source, String engine, String identifier, String leaseId) {
        super(source, engine, identifier);
        this.leaseId = leaseId;
    }

    public String getLeaseId() {
        return leaseId;
    }
}

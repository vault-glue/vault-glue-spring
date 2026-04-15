package io.vaultglue.core.event;

import io.vaultglue.core.VaultGlueEvent;
import io.vaultglue.pki.CertificateRenewalInfo;

public class CertificateRenewedEvent extends VaultGlueEvent {

    private final CertificateRenewalInfo renewalInfo;

    public CertificateRenewedEvent(Object source, String engine, String identifier,
                                    CertificateRenewalInfo renewalInfo) {
        super(source, engine, identifier);
        this.renewalInfo = renewalInfo;
    }

    public CertificateRenewalInfo getRenewalInfo() {
        return renewalInfo;
    }
}

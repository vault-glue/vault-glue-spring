package io.vaultglue.core.event;

import io.vaultglue.core.VaultGlueEvent;
import io.vaultglue.pki.CertificateBundle;

public class CertificateRenewedEvent extends VaultGlueEvent {

    private final CertificateBundle certificateBundle;

    public CertificateRenewedEvent(Object source, String engine, String identifier,
                                    CertificateBundle certificateBundle) {
        super(source, engine, identifier);
        this.certificateBundle = certificateBundle;
    }

    public CertificateBundle getCertificateBundle() {
        return certificateBundle;
    }
}

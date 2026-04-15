package io.vaultglue.pki;

import java.time.Instant;

public record CertificateRenewalInfo(
    String serialNumber,
    Instant expiresAt,
    long remainingHours
) {
    public static CertificateRenewalInfo from(CertificateBundle bundle) {
        return new CertificateRenewalInfo(
                bundle.serialNumber(),
                bundle.expiresAt(),
                bundle.getRemainingHours());
    }
}

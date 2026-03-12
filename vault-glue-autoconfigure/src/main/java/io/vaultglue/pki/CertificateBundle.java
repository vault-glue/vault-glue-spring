package io.vaultglue.pki;

import java.time.Duration;
import java.time.Instant;

public record CertificateBundle(
    String certificate,
    String privateKey,
    String issuingCa,
    String caChain,
    String serialNumber,
    Instant expiresAt
) {
    public long getRemainingHours() {
        return Duration.between(Instant.now(), expiresAt).toHours();
    }

    public boolean isExpiringSoon(long thresholdHours) {
        return getRemainingHours() < thresholdHours;
    }
}

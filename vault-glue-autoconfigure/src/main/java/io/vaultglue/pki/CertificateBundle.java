package io.vaultglue.pki;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record CertificateBundle(
    String certificate,
    String privateKey,
    String issuingCa,
    List<String> caChain,
    String serialNumber,
    Instant expiresAt
) {
    public long getRemainingHours() {
        return Duration.between(Instant.now(), expiresAt).toHours();
    }

    public boolean isExpiringSoon(long thresholdHours) {
        return getRemainingHours() < thresholdHours;
    }

    @Override
    public String toString() {
        return "CertificateBundle[serialNumber=" + serialNumber
                + ", expiresAt=" + expiresAt
                + ", privateKey=***masked***]";
    }
}

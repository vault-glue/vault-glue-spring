package io.vaultglue.pki;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CertificateBundle(
    String certificate,
    String privateKey,
    String issuingCa,
    List<String> caChain,
    String serialNumber,
    Instant expiresAt
) {
    public long getRemainingHours() {
        return Math.max(0, Duration.between(Instant.now(), expiresAt).toHours());
    }

    public boolean isExpiringSoon(long thresholdHours) {
        return getRemainingHours() < thresholdHours;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serialNumber, expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CertificateBundle that)) return false;
        return Objects.equals(serialNumber, that.serialNumber)
                && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public String toString() {
        return "CertificateBundle[serialNumber=" + serialNumber
                + ", expiresAt=" + expiresAt
                + ", privateKey=***masked***]";
    }
}

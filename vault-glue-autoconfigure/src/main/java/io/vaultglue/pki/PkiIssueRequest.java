package io.vaultglue.pki;

import java.time.Duration;
import java.util.List;

public record PkiIssueRequest(
    String role,
    String commonName,
    List<String> altNames,
    List<String> ipSans,
    Duration ttl
) {
    public static Builder builder(String role, String commonName) {
        return new Builder(role, commonName);
    }

    public static class Builder {
        private final String role;
        private final String commonName;
        private List<String> altNames = List.of();
        private List<String> ipSans = List.of();
        private Duration ttl;

        Builder(String role, String commonName) {
            this.role = role;
            this.commonName = commonName;
        }

        public Builder altNames(List<String> altNames) { this.altNames = altNames; return this; }
        public Builder ipSans(List<String> ipSans) { this.ipSans = ipSans; return this; }
        public Builder ttl(Duration ttl) { this.ttl = ttl; return this; }

        public PkiIssueRequest build() {
            return new PkiIssueRequest(role, commonName, altNames, ipSans, ttl);
        }
    }
}

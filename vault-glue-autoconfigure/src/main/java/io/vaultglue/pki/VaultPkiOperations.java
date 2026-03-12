package io.vaultglue.pki;

import java.time.Duration;

public interface VaultPkiOperations {

    CertificateBundle issue(String role, String commonName);

    CertificateBundle issue(String role, String commonName, Duration ttl);

    CertificateBundle issue(PkiIssueRequest request);

    void revoke(String serialNumber);

    CertificateBundle getCurrent();
}

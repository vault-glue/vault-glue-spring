package io.vaultglue.pki;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

public class DefaultVaultPkiOperations implements VaultPkiOperations {

    private static final Logger log = LoggerFactory.getLogger(DefaultVaultPkiOperations.class);

    private final VaultTemplate vaultTemplate;
    private final VaultGluePkiProperties properties;
    private volatile CertificateBundle currentCertificate;

    public DefaultVaultPkiOperations(VaultTemplate vaultTemplate, VaultGluePkiProperties properties) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
    }

    @Override
    public CertificateBundle issue(String role, String commonName) {
        return issue(PkiIssueRequest.builder(role, commonName).build());
    }

    @Override
    public CertificateBundle issue(String role, String commonName, Duration ttl) {
        return issue(PkiIssueRequest.builder(role, commonName).ttl(ttl).build());
    }

    @Override
    public CertificateBundle issue(PkiIssueRequest request) {
        String path = properties.getBackend() + "/issue/" + request.role();
        log.info("[VaultGlue] Issuing certificate: role={}, cn={}", request.role(), request.commonName());

        Map<String, Object> body = new HashMap<>();
        body.put("common_name", request.commonName());

        if (request.altNames() != null && !request.altNames().isEmpty()) {
            body.put("alt_names", String.join(",", request.altNames()));
        }
        if (request.ipSans() != null && !request.ipSans().isEmpty()) {
            body.put("ip_sans", String.join(",", request.ipSans()));
        }
        if (request.ttl() != null) {
            body.put("ttl", request.ttl().getSeconds() + "s");
        }

        VaultResponse response = vaultTemplate.write(path, body);
        if (response == null || response.getData() == null) {
            throw new RuntimeException("[VaultGlue] Failed to issue certificate from PKI");
        }

        Map<String, Object> data = response.getData();
        CertificateBundle bundle = new CertificateBundle(
                (String) data.get("certificate"),
                (String) data.get("private_key"),
                (String) data.get("issuing_ca"),
                (String) data.get("ca_chain"),
                (String) data.get("serial_number"),
                parseExpiration(data.get("expiration"))
        );

        this.currentCertificate = bundle;
        log.info("[VaultGlue] Certificate issued: serial={}, expires={}",
                bundle.serialNumber(), bundle.expiresAt());
        return bundle;
    }

    @Override
    public void revoke(String serialNumber) {
        log.info("[VaultGlue] Revoking certificate: serial={}", serialNumber);
        vaultTemplate.write(
                properties.getBackend() + "/revoke",
                Map.of("serial_number", serialNumber));
    }

    @Override
    public CertificateBundle getCurrent() {
        return currentCertificate;
    }

    private Instant parseExpiration(Object expiration) {
        if (expiration instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        if (expiration instanceof String s) {
            return Instant.ofEpochSecond(Long.parseLong(s));
        }
        return Instant.now().plus(Duration.ofHours(72));
    }
}

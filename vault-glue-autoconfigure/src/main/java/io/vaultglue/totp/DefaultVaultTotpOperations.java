package io.vaultglue.totp;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

public class DefaultVaultTotpOperations implements VaultTotpOperations {

    private static final Logger log = LoggerFactory.getLogger(DefaultVaultTotpOperations.class);

    private final VaultTemplate vaultTemplate;
    private final VaultGlueTotpProperties properties;

    public DefaultVaultTotpOperations(VaultTemplate vaultTemplate, VaultGlueTotpProperties properties) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
    }

    @Override
    public TotpKey createKey(String name, String issuer, String accountName) {
        String path = properties.getBackend() + "/keys/" + name;
        log.info("[VaultGlue] Creating TOTP key: {}", name);

        VaultResponse response = vaultTemplate.write(path, Map.of(
                "generate", true,
                "issuer", issuer,
                "account_name", accountName
        ));

        if (response == null || response.getData() == null) {
            throw new RuntimeException("[VaultGlue] Failed to create TOTP key: " + name);
        }

        Map<String, Object> data = response.getData();
        return new TotpKey(
                name,
                (String) data.get("barcode"),
                (String) data.get("url")
        );
    }

    @Override
    public String generateCode(String name) {
        String path = properties.getBackend() + "/code/" + name;
        log.debug("[VaultGlue] Generating TOTP code for: {}", name);

        VaultResponse response = vaultTemplate.read(path);
        if (response == null || response.getData() == null) {
            throw new RuntimeException("[VaultGlue] Failed to generate TOTP code for: " + name);
        }
        String code = (String) response.getData().get("code");
        if (code == null) {
            throw new RuntimeException("[VaultGlue] TOTP code missing in Vault response for: " + name);
        }
        return code;
    }

    @Override
    public boolean validate(String name, String code) {
        String path = properties.getBackend() + "/code/" + name;

        VaultResponse response = vaultTemplate.write(path, Map.of("code", code));
        if (response == null || response.getData() == null) {
            throw new RuntimeException(
                    "[VaultGlue] Failed to validate TOTP code for: " + name
                    + ". Vault returned empty response.");
        }

        Object valid = response.getData().get("valid");
        if (valid instanceof Boolean b) return b;
        if (valid instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    @Override
    public void deleteKey(String name) {
        String path = properties.getBackend() + "/keys/" + name;
        log.info("[VaultGlue] Deleting TOTP key: {}", name);
        vaultTemplate.delete(path);
    }
}

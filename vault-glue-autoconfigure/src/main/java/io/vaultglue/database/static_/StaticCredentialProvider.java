package io.vaultglue.database.static_;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import io.vaultglue.core.VaultPathUtils;
import io.vaultglue.database.VaultGlueCredentialException;

public class StaticCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(StaticCredentialProvider.class);

    private final VaultTemplate vaultTemplate;

    public StaticCredentialProvider(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    public DbCredential getCredential(String backend, String role) {
        VaultPathUtils.validatePathSegment(backend, "backend");
        VaultPathUtils.validatePathSegment(role, "role");
        String path = backend + "/static-creds/" + role;
        log.debug("[VaultGlue] Reading static credential from: {}", path);

        VaultResponse response = vaultTemplate.read(path);
        if (response == null || response.getData() == null) {
            throw new VaultGlueCredentialException(
                    "[VaultGlue] Failed to read static credential from Vault path: " + path);
        }

        Map<String, Object> data = response.getData();
        String username = (String) data.get("username");
        String password = (String) data.get("password");

        if (username == null || password == null) {
            throw new VaultGlueCredentialException(
                    "[VaultGlue] Invalid credential response from Vault path: " + path);
        }

        log.debug("[VaultGlue] Static credential retrieved: username={}", username);
        return new DbCredential(username, password);
    }

    public record DbCredential(String username, String password) {
        @Override
        public String toString() {
            return "DbCredential[username=" + username + ", password=***masked***]";
        }
    }

}

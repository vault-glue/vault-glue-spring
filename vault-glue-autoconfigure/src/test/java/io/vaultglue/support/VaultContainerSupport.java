package io.vaultglue.support;

import org.testcontainers.vault.VaultContainer;

public final class VaultContainerSupport {

    public static final String VAULT_TOKEN = "test-root-token";
    public static final String VAULT_IMAGE = "hashicorp/vault:1.17";

    private static final VaultContainer<?> VAULT = new VaultContainer<>(VAULT_IMAGE)
            .withVaultToken(VAULT_TOKEN);

    static {
        VAULT.start();
    }

    private VaultContainerSupport() {
    }

    public static VaultContainer<?> getContainer() {
        return VAULT;
    }

    public static String getAddress() {
        return "http://" + VAULT.getHost() + ":" + VAULT.getFirstMappedPort();
    }
}

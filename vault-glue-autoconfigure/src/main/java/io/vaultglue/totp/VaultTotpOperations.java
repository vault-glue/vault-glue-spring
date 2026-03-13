package io.vaultglue.totp;

public interface VaultTotpOperations {

    TotpKey createKey(String name, String issuer, String accountName);

    String generateCode(String name);

    boolean validate(String name, String code);

    void deleteKey(String name);
}

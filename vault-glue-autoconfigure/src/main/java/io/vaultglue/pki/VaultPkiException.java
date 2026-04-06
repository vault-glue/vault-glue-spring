package io.vaultglue.pki;

public class VaultPkiException extends RuntimeException {

    public VaultPkiException(String message) {
        super(message);
    }

    public VaultPkiException(String message, Throwable cause) {
        super(message, cause);
    }
}

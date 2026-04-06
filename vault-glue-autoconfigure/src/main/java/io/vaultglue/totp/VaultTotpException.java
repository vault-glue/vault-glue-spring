package io.vaultglue.totp;

public class VaultTotpException extends RuntimeException {

    public VaultTotpException(String message) {
        super(message);
    }

    public VaultTotpException(String message, Throwable cause) {
        super(message, cause);
    }
}

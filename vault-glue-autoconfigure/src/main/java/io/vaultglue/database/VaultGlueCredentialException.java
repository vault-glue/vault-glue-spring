package io.vaultglue.database;

public class VaultGlueCredentialException extends RuntimeException {

    public VaultGlueCredentialException(String message) {
        super(message);
    }

    public VaultGlueCredentialException(String message, Throwable cause) {
        super(message, cause);
    }
}

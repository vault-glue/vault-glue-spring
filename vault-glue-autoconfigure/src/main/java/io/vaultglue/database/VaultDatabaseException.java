package io.vaultglue.database;

public class VaultDatabaseException extends RuntimeException {

    public VaultDatabaseException(String message) {
        super(message);
    }

    public VaultDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

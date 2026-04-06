package io.vaultglue.aws;

public class VaultAwsException extends RuntimeException {

    public VaultAwsException(String message) {
        super(message);
    }

    public VaultAwsException(String message, Throwable cause) {
        super(message, cause);
    }
}

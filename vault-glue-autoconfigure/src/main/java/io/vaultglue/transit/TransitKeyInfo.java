package io.vaultglue.transit;

public record TransitKeyInfo(
    String name,
    String type,
    int latestVersion,
    int minDecryptionVersion,
    int minEncryptionVersion,
    boolean deletionAllowed,
    boolean exportable,
    boolean supportsEncryption,
    boolean supportsDecryption,
    boolean supportsSigning
) {}

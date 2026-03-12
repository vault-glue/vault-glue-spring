package io.vaultglue.totp;

public record TotpKey(
    String name,
    String barcode,
    String url
) {}

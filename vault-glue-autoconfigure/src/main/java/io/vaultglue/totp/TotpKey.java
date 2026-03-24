package io.vaultglue.totp;

public record TotpKey(
    String name,
    String barcode,
    String url
) {
    @Override
    public String toString() {
        return "TotpKey[name=" + name + ", barcode=***masked***, url=***masked***]";
    }
}

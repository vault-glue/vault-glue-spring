package io.vaultglue.transit;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TransitKeyType {

    AES128_GCM96("aes128-gcm96"),
    AES256_GCM96("aes256-gcm96"),
    CHACHA20_POLY1305("chacha20-poly1305"),
    ED25519("ed25519"),
    ECDSA_P256("ecdsa-p256"),
    ECDSA_P384("ecdsa-p384"),
    ECDSA_P521("ecdsa-p521"),
    RSA_2048("rsa-2048"),
    RSA_3072("rsa-3072"),
    RSA_4096("rsa-4096");

    private final String value;

    TransitKeyType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TransitKeyType fromValue(String value) {
        for (TransitKeyType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("[VaultGlue] Unknown transit key type: " + value);
    }
}

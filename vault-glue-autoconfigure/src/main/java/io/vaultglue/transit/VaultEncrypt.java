package io.vaultglue.transit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation indicating a field should be encrypted via Vault Transit.
 * Use with @Convert(converter = VaultEncryptConverter.class).
 * Encryption uses the configured default transit key.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VaultEncrypt {
}

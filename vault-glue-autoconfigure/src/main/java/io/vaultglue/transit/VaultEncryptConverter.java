package io.vaultglue.transit;

import jakarta.persistence.AttributeConverter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * JPA AttributeConverter that encrypts/decrypts field values using Vault Transit.
 *
 * <p>Ciphertext is stored as {@code vg:{keyName}:vault:v1:...} to track which key was used.
 * This allows different fields to use different Transit keys via a single converter.
 *
 * <p>Usage:
 * <pre>
 * {@code
 * @Entity
 * public class User {
 *     @Convert(converter = VaultEncryptConverter.class)
 *     private String residentNumber;  // encrypted with the defaultKeyName
 * }
 * }
 * </pre>
 */
public class VaultEncryptConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(VaultEncryptConverter.class);

    /** Format: vg:{keyName}:vault:v1:ciphertext */
    private static final String VG_PREFIX = "vg:";
    private static final Pattern VG_PATTERN = Pattern.compile("^vg:([^:]+):(vault:v\\d+:.+)$");
    private static final Pattern VAULT_CIPHERTEXT_PATTERN = Pattern.compile("^vault:v\\d+:.+");

    private static final Object INIT_LOCK = new Object();
    private static volatile ApplicationContext applicationContext;
    private static volatile String defaultKeyName;

    public static void initialize(ApplicationContext context, String keyName) {
        synchronized (INIT_LOCK) {
            applicationContext = context;
            defaultKeyName = keyName;
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;

        VaultTransitOperations transit = getTransitOperations();
        String keyName = defaultKeyName; // single volatile read
        try {
            String ciphertext = transit.encrypt(keyName, attribute);
            // Include key name as prefix so the correct key can be identified during decryption
            return VG_PREFIX + keyName + ":" + ciphertext;
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to encrypt field value", e);
            throw new RuntimeException("Vault transit encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;

        // vg:{keyName}:vault:v1:xxx format (new format)
        Matcher vgMatcher = VG_PATTERN.matcher(dbData);
        if (vgMatcher.matches()) {
            String keyName = vgMatcher.group(1);
            String ciphertext = vgMatcher.group(2);
            return decryptWith(keyName, ciphertext);
        }

        // vault:v1:xxx format (legacy — decrypt with defaultKeyName)
        if (VAULT_CIPHERTEXT_PATTERN.matcher(dbData).matches()) {
            return decryptWith(defaultKeyName, dbData);
        }

        // Unencrypted data should not exist in encrypted columns
        throw new IllegalStateException(
                "[VaultGlue] Unencrypted data found in column marked with VaultEncryptConverter. "
                + "Data does not match any known ciphertext format.");
    }

    private String decryptWith(String keyName, String ciphertext) {
        VaultTransitOperations transit = getTransitOperations();
        try {
            return transit.decrypt(keyName, ciphertext);
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to decrypt field value with key '{}'", keyName, e);
            throw new RuntimeException("Vault transit decryption failed", e);
        }
    }

    private VaultTransitOperations getTransitOperations() {
        ApplicationContext ctx = applicationContext; // single volatile read
        if (ctx == null) {
            throw new IllegalStateException(
                    "VaultEncryptConverter not initialized. Ensure VaultGlueTransitAutoConfiguration is active.");
        }
        return ctx.getBean(VaultTransitOperations.class);
    }
}

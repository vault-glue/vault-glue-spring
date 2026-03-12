package io.vaultglue.transit;

import jakarta.persistence.AttributeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * JPA AttributeConverter that encrypts/decrypts field values using Vault Transit.
 *
 * <p>Usage with @VaultEncrypt annotation:
 * <pre>
 * {@code
 * @Entity
 * public class User {
 *     @Convert(converter = VaultEncryptConverter.class)
 *     @VaultEncrypt(key = "user-pii")
 *     private String residentNumber;
 * }
 * }
 * </pre>
 *
 * <p>Note: This converter requires a static ApplicationContext reference
 * because JPA instantiates converters outside of Spring's control.
 */
public class VaultEncryptConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(VaultEncryptConverter.class);
    private static final String VAULT_PREFIX = "vault:v";

    private static volatile ApplicationContext applicationContext;
    private static volatile String defaultKeyName;

    public static void initialize(ApplicationContext context, String keyName) {
        applicationContext = context;
        defaultKeyName = keyName;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;

        try {
            VaultTransitOperations transit = getTransitOperations();
            return transit.encrypt(defaultKeyName, attribute);
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to encrypt field value", e);
            throw new RuntimeException("Vault transit encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;

        // 미암호화 데이터 호환 (마이그레이션 중)
        if (!dbData.startsWith(VAULT_PREFIX)) {
            return dbData;
        }

        try {
            VaultTransitOperations transit = getTransitOperations();
            return transit.decrypt(defaultKeyName, dbData);
        } catch (Exception e) {
            log.error("[VaultGlue] Failed to decrypt field value", e);
            throw new RuntimeException("Vault transit decryption failed", e);
        }
    }

    private VaultTransitOperations getTransitOperations() {
        if (applicationContext == null) {
            throw new IllegalStateException(
                    "VaultEncryptConverter not initialized. Ensure VaultGlueTransitAutoConfiguration is active.");
        }
        return applicationContext.getBean(VaultTransitOperations.class);
    }
}

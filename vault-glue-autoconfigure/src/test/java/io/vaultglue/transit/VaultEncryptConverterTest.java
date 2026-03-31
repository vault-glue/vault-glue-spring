package io.vaultglue.transit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultEncryptConverterTest {

    private VaultEncryptConverter converter;
    private VaultTransitOperations transitOps;

    @BeforeEach
    void setUp() {
        transitOps = Mockito.mock(VaultTransitOperations.class);
        ApplicationContext ctx = Mockito.mock(ApplicationContext.class);
        Mockito.when(ctx.getBean(VaultTransitOperations.class)).thenReturn(transitOps);
        VaultEncryptConverter.setApplicationContext(ctx);
        VaultEncryptConverter.setDefaultKeyName("default-key");
        converter = new VaultEncryptConverter();
    }

    @AfterEach
    void tearDown() {
        // Re-initialize to prevent static state pollution across test classes
        VaultEncryptConverter.reset();
    }

    @Test
    void convertToDatabaseColumn_shouldThrowWhenNotInitialized() {
        VaultEncryptConverter.reset();
        VaultEncryptConverter uninitConverter = new VaultEncryptConverter();

        assertThrows(IllegalStateException.class,
                () -> uninitConverter.convertToDatabaseColumn("test"));
    }

    @Test
    void convertToEntityAttribute_shouldThrowOnPlaintext() {
        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute("some-plaintext-data"));
    }

    @Test
    void convertToEntityAttribute_shouldReturnNullForNull() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void convertToEntityAttribute_shouldDecryptVgFormat() {
        Mockito.when(transitOps.decrypt("my-key", "vault:v1:abc123"))
                .thenReturn("secret");

        String result = converter.convertToEntityAttribute("vg:my-key:vault:v1:abc123");
        assertEquals("secret", result);
    }

    @Test
    void convertToEntityAttribute_shouldDecryptLegacyFormat() {
        Mockito.when(transitOps.decrypt("default-key", "vault:v1:abc123"))
                .thenReturn("secret");

        String result = converter.convertToEntityAttribute("vault:v1:abc123");
        assertEquals("secret", result);
    }

    @Test
    void convertToDatabaseColumn_shouldLazyResolveOperations() {
        VaultEncryptConverter.reset();

        ApplicationContext ctx = Mockito.mock(ApplicationContext.class);
        VaultTransitOperations ops = Mockito.mock(VaultTransitOperations.class);
        Mockito.when(ctx.getBean(VaultTransitOperations.class)).thenReturn(ops);
        Mockito.when(ops.encrypt("default-key", "plaintext")).thenReturn("vault:v1:encrypted");

        VaultEncryptConverter.setApplicationContext(ctx);
        VaultEncryptConverter.setDefaultKeyName("default-key");

        VaultEncryptConverter conv = new VaultEncryptConverter();
        String result = conv.convertToDatabaseColumn("plaintext");

        assertEquals("vg:default-key:vault:v1:encrypted", result);
        Mockito.verify(ctx).getBean(VaultTransitOperations.class);
    }
}

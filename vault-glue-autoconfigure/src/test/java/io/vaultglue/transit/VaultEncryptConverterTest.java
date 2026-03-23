package io.vaultglue.transit;

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
        VaultEncryptConverter.initialize(ctx, "default-key");
        converter = new VaultEncryptConverter();
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
}

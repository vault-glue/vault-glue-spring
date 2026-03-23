package io.vaultglue.totp;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVaultTotpOperationsTest {

    private VaultTemplate vaultTemplate;
    private DefaultVaultTotpOperations totpOps;

    @BeforeEach
    void setUp() {
        vaultTemplate = Mockito.mock(VaultTemplate.class);
        VaultGlueTotpProperties properties = new VaultGlueTotpProperties();
        properties.setBackend("totp");
        totpOps = new DefaultVaultTotpOperations(vaultTemplate, properties);
    }

    @Test
    void validate_shouldThrowOnNullResponse() {
        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> totpOps.validate("test-key", "123456"));
    }

    @Test
    void validate_shouldReturnTrueForValidCode() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("valid", true));
        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(response);

        assertTrue(totpOps.validate("test-key", "123456"));
    }

    @Test
    void validate_shouldReturnFalseForInvalidCode() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("valid", false));
        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(response);

        assertFalse(totpOps.validate("test-key", "000000"));
    }

    @Test
    void generateCode_shouldThrowOnMissingCodeInResponse() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("unexpected", "value"));
        Mockito.when(vaultTemplate.read(Mockito.anyString())).thenReturn(response);

        assertThrows(RuntimeException.class,
                () -> totpOps.generateCode("test-key"));
    }
}

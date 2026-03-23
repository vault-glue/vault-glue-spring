package io.vaultglue.transit;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultVaultTransitOperationsTest {

    private VaultTemplate vaultTemplate;
    private VaultGlueTransitProperties properties;
    private DefaultVaultTransitOperations transitOps;

    @BeforeEach
    void setUp() {
        vaultTemplate = Mockito.mock(VaultTemplate.class);
        properties = new VaultGlueTransitProperties();
        properties.setBackend("transit");
        transitOps = new DefaultVaultTransitOperations(vaultTemplate, properties);
    }

    @Test
    void encrypt_shouldReturnCiphertext() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("ciphertext", "vault:v1:abc123"));

        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/encrypt/test-key"),
                Mockito.any()
        )).thenReturn(response);

        String result = transitOps.encrypt("test-key", "hello");

        assertEquals("vault:v1:abc123", result);
    }

    @Test
    void decrypt_shouldReturnPlaintext() {
        String base64Hello = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));

        VaultResponse response = new VaultResponse();
        response.setData(Map.of("plaintext", base64Hello));

        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/decrypt/test-key"),
                Mockito.any()
        )).thenReturn(response);

        String result = transitOps.decrypt("test-key", "vault:v1:abc123");

        assertEquals("hello", result);
    }

    @Test
    void encryptBatch_shouldReturnCiphertexts() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "batch_results", List.of(
                        Map.of("ciphertext", "vault:v1:aaa"),
                        Map.of("ciphertext", "vault:v1:bbb")
                )
        ));

        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/encrypt/test-key"),
                Mockito.any()
        )).thenReturn(response);

        List<String> results = transitOps.encryptBatch("test-key", List.of("foo", "bar"));

        assertEquals(2, results.size());
        assertEquals("vault:v1:aaa", results.get(0));
        assertEquals("vault:v1:bbb", results.get(1));
    }

    @Test
    void encryptBatch_shouldThrowOnMissingResultKey() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "batch_results", List.of(
                        Map.of("unexpected_field", "value")
                )
        ));

        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/encrypt/test-key"),
                Mockito.any()
        )).thenReturn(response);

        org.junit.jupiter.api.Assertions.assertThrows(
                DefaultVaultTransitOperations.VaultTransitException.class,
                () -> transitOps.encryptBatch("test-key", List.of("hello")));
    }
}

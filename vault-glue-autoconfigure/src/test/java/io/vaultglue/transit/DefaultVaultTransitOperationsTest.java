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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        BatchResult<String> result = transitOps.encryptBatch("test-key", List.of("foo", "bar"));

        assertEquals(2, result.successes().size());
        assertEquals("vault:v1:aaa", result.successes().get(0));
        assertEquals("vault:v1:bbb", result.successes().get(1));
        assertFalse(result.hasFailures());
    }

    @Test
    void encryptBatch_shouldReportMissingKeyAsFailure() {
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

        BatchResult<String> result = transitOps.encryptBatch("test-key", List.of("hello"));

        assertTrue(result.hasFailures());
        assertEquals(1, result.failures().size());
        assertEquals("Missing 'ciphertext' in batch result item", result.failures().get(0).error());
    }

    @Test
    void encryptBatch_shouldReturnPartialResults() {
        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "batch_results", List.of(
                        Map.of("ciphertext", "vault:v1:abc"),
                        Map.of("error", "key not found"),
                        Map.of("ciphertext", "vault:v1:def")
                )
        ));

        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/encrypt/test-key"),
                Mockito.any()
        )).thenReturn(response);

        BatchResult<String> result = transitOps.encryptBatch("test-key", List.of("a", "b", "c"));

        assertEquals(2, result.successes().size());
        assertEquals("vault:v1:abc", result.successes().get(0));
        assertEquals("vault:v1:def", result.successes().get(1));
        assertEquals(1, result.failures().size());
        assertEquals("key not found", result.failures().get(0).error());
        assertEquals(1, result.failures().get(0).index());
    }

    @Test
    void verifyHmac_shouldThrowOnNullResponse() {
        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/verify/test-key"),
                Mockito.any()
        )).thenReturn(null);

        assertThrows(VaultTransitException.class,
                () -> transitOps.verifyHmac("test-key", "data", "hmac-value"));
    }

    @Test
    void verifyHmac_shouldThrowOnEmptyData() {
        VaultResponse response = new VaultResponse();
        response.setData(null);

        Mockito.when(vaultTemplate.write(
                Mockito.eq("transit/verify/test-key"),
                Mockito.any()
        )).thenReturn(response);

        assertThrows(VaultTransitException.class,
                () -> transitOps.verifyHmac("test-key", "data", "hmac-value"));
    }
}

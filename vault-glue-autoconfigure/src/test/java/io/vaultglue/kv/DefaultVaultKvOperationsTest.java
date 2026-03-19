package io.vaultglue.kv;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVaultKvOperationsTest {

    private VaultTemplate vaultTemplate;
    private VaultKeyValueOperations kvOps;
    private VaultGlueKvProperties properties;

    @BeforeEach
    void setUp() {
        vaultTemplate = Mockito.mock(VaultTemplate.class);
        kvOps = Mockito.mock(VaultKeyValueOperations.class);
        properties = new VaultGlueKvProperties();
        properties.setBackend("app");
        properties.setVersion(2);
    }

    @Test
    void get_v2_shouldReturnData() {
        Mockito.when(vaultTemplate.opsForKeyValue("app", KeyValueBackend.KV_2)).thenReturn(kvOps);

        Map<String, Object> secretData = Map.of("username", "admin", "password", "s3cr3t");
        VaultResponse response = new VaultResponse();
        response.setData(secretData);
        Mockito.when(kvOps.get("myapp/config")).thenReturn(response);

        DefaultVaultKvOperations ops = new DefaultVaultKvOperations(vaultTemplate, properties, new ObjectMapper());

        Map<String, Object> result = ops.get("myapp/config");

        assertEquals("admin", result.get("username"));
        assertEquals("s3cr3t", result.get("password"));
    }

    @Test
    void get_nonExistentPath_shouldReturnEmptyMap() {
        Mockito.when(vaultTemplate.opsForKeyValue("app", KeyValueBackend.KV_2)).thenReturn(kvOps);
        Mockito.when(kvOps.get("myapp/missing")).thenReturn(null);

        DefaultVaultKvOperations ops = new DefaultVaultKvOperations(vaultTemplate, properties, new ObjectMapper());

        Map<String, Object> result = ops.get("myapp/missing");

        assertTrue(result.isEmpty());
    }

    @Test
    void getByVersion_onV1_shouldThrowUnsupportedOperation() {
        properties.setVersion(1);
        VaultKeyValueOperations kvOpsV1 = Mockito.mock(VaultKeyValueOperations.class);
        Mockito.when(vaultTemplate.opsForKeyValue("app", KeyValueBackend.KV_1)).thenReturn(kvOpsV1);

        DefaultVaultKvOperations ops = new DefaultVaultKvOperations(vaultTemplate, properties, new ObjectMapper());

        assertThrows(UnsupportedOperationException.class, () -> ops.get("myapp/config", 1));
    }
}

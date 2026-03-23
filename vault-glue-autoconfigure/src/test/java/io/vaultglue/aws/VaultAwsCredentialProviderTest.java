package io.vaultglue.aws;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultAwsCredentialProviderTest {

    private VaultTemplate vaultTemplate;
    private VaultGlueAwsProperties properties;

    @BeforeEach
    void setUp() {
        vaultTemplate = Mockito.mock(VaultTemplate.class);
        properties = new VaultGlueAwsProperties();
        properties.setBackend("aws");
        properties.setRole("test-role");
        properties.setTtl("1h");
    }

    @Test
    void getCredential_shouldThrowBeforeStart() {
        properties.setCredentialType("iam_user");
        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties);

        assertThrows(IllegalStateException.class, provider::getCredential);
    }

    @Test
    void start_shouldValidateSecurityTokenForStsType() {
        properties.setCredentialType("sts");

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "access_key", "AKIA1234567890",
                "secret_key", "secretkey1234567890"
        ));

        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(response);

        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties);

        assertThrows(RuntimeException.class, provider::start);
    }
}

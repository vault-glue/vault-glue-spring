package io.vaultglue.aws;

import io.vaultglue.core.FailureStrategyHandler;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
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
    private FailureStrategyHandler failureStrategyHandler;

    @BeforeEach
    void setUp() {
        vaultTemplate = Mockito.mock(VaultTemplate.class);
        failureStrategyHandler = Mockito.mock(FailureStrategyHandler.class);
        properties = new VaultGlueAwsProperties();
        properties.setBackend("aws");
        properties.setRole("test-role");
        properties.setTtl("1h");
    }

    @Test
    void getCredential_shouldThrowBeforeStart() {
        properties.setCredentialType("iam_user");
        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties, failureStrategyHandler);

        assertThrows(IllegalStateException.class, provider::getCredential);
    }

    @Test
    void start_shouldLogErrorAndContinueWhenSecurityTokenMissingForStsType() {
        properties.setCredentialType("sts");

        VaultResponse response = new VaultResponse();
        response.setData(Map.of(
                "access_key", "AKIA1234567890",
                "secret_key", "secretkey1234567890"
        ));

        Mockito.when(vaultTemplate.write(Mockito.anyString(), Mockito.any()))
                .thenReturn(response);

        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties, failureStrategyHandler);

        // start() no longer throws — it logs error and schedules retry
        provider.start();
        assertThrows(IllegalStateException.class, provider::getCredential);
        provider.shutdown();
    }

    @Test
    void scheduledRotate_shouldCallFailureStrategyOnError() {
        properties.setCredentialType("iam_user");

        // First rotate succeeds (in start()), subsequent fails
        VaultResponse successResponse = new VaultResponse();
        successResponse.setData(Map.of(
                "access_key", "AKIA1234567890",
                "secret_key", "secretkey1234567890"
        ));

        Mockito.when(vaultTemplate.read(Mockito.anyString()))
                .thenReturn(successResponse)
                .thenThrow(new RuntimeException("Vault unavailable"));

        VaultAwsCredentialProvider provider = new VaultAwsCredentialProvider(vaultTemplate, properties, failureStrategyHandler);

        // Override TTL to make scheduler fire quickly
        properties.setTtl("1s"); // 1 second TTL → 800ms renewal interval
        provider.start();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(failureStrategyHandler, Mockito.atLeastOnce())
                        .handle(Mockito.eq("aws"), Mockito.eq("test-role"), Mockito.any(), Mockito.any()));

        provider.shutdown();
    }
}

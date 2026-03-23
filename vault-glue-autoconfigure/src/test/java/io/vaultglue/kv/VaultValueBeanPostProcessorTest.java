package io.vaultglue.kv;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultValueBeanPostProcessorTest {

    private VaultKvOperations kvOperations;
    private VaultValueBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        kvOperations = Mockito.mock(VaultKvOperations.class);
        processor = new VaultValueBeanPostProcessor(kvOperations);
    }

    @Test
    void refreshAll_shouldKeepOldValuesOnVaultFailure() {
        Mockito.when(kvOperations.get("app/config"))
                .thenReturn(Map.of("key", "original-value"))
                .thenThrow(new RuntimeException("Vault unavailable"));

        TestBean bean = new TestBean();
        processor.postProcessAfterInitialization(bean, "testBean");
        assertEquals("original-value", bean.value);

        processor.refreshAll();
        assertEquals("original-value", bean.value);
    }

    static class TestBean {
        @VaultValue(path = "app/config", key = "key", refresh = true)
        String value;
    }
}

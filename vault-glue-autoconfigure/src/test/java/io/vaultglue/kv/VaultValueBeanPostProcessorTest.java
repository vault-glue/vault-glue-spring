package io.vaultglue.kv;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void refreshAll_shouldUpdateCacheWhenSecretIsDeleted() {
        // First call returns data; second call returns empty map (secret deleted from Vault)
        Mockito.when(kvOperations.get("app/config"))
                .thenReturn(Map.of("key", "original-value"))
                .thenReturn(Collections.emptyMap());

        TestBean bean = new TestBean();
        processor.postProcessAfterInitialization(bean, "testBean");
        assertEquals("original-value", bean.value);

        // After refresh, kvOperations returns empty — cache entry for the path must reflect empty map
        processor.refreshAll();
        // The field cannot be un-injected, but the cache must no longer hold the stale value
        // A subsequent injectValue call using the updated cache must yield null (no stale data served)
        TestBean bean2 = new TestBean();
        processor.postProcessAfterInitialization(bean2, "testBean2");
        assertNull(bean2.value);
    }

    static class TestBean {
        @VaultValue(path = "app/config", key = "key", refresh = true)
        String value;
    }
}

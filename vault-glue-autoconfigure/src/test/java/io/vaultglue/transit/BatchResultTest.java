package io.vaultglue.transit;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchResultTest {

    @Test
    void batchResultItem_success() {
        BatchResultItem<String> item = new BatchResultItem<>(0, "encrypted", null);
        assertTrue(item.isSuccess());
        assertEquals("encrypted", item.value());
        assertNull(item.error());
    }

    @Test
    void batchResultItem_failure() {
        BatchResultItem<String> item = new BatchResultItem<>(1, null, "key not found");
        assertFalse(item.isSuccess());
        assertNull(item.value());
        assertEquals("key not found", item.error());
    }

    @Test
    void batchResult_successes() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, "a", null),
                new BatchResultItem<>(1, null, "error"),
                new BatchResultItem<>(2, "c", null)
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertEquals(List.of("a", "c"), result.successes());
    }

    @Test
    void batchResult_failures() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, "a", null),
                new BatchResultItem<>(1, null, "error")
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertEquals(1, result.failures().size());
        assertEquals("error", result.failures().get(0).error());
    }

    @Test
    void batchResult_hasFailures() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, "a", null)
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertFalse(result.hasFailures());
    }

    @Test
    void batchResult_allFailed() {
        List<BatchResultItem<String>> items = List.of(
                new BatchResultItem<>(0, null, "err1"),
                new BatchResultItem<>(1, null, "err2")
        );
        BatchResult<String> result = new BatchResult<>(items);
        assertTrue(result.hasFailures());
        assertTrue(result.successes().isEmpty());
    }
}

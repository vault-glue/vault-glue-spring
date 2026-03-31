package io.vaultglue.transit;

import java.util.List;

/**
 * Contains results from a batch transit operation, supporting partial success.
 */
public record BatchResult<T>(List<BatchResultItem<T>> items) {

    public List<T> successes() {
        return items.stream()
                .filter(BatchResultItem::isSuccess)
                .map(BatchResultItem::value)
                .toList();
    }

    public List<BatchResultItem<T>> failures() {
        return items.stream()
                .filter(item -> !item.isSuccess())
                .toList();
    }

    public boolean hasFailures() {
        return items.stream().anyMatch(item -> !item.isSuccess());
    }
}

package io.vaultglue.transit;

/**
 * Represents a single item result in a batch transit operation.
 */
public record BatchResultItem<T>(int index, T value, String error) {

    public boolean isSuccess() {
        return error == null;
    }
}

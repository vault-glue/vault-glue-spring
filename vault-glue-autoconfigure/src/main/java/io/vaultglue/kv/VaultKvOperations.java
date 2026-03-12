package io.vaultglue.kv;

import java.util.List;
import java.util.Map;

public interface VaultKvOperations {

    // Read
    Map<String, Object> get(String path);

    <T> T get(String path, Class<T> type);

    Map<String, Object> get(String path, int version);

    // Write
    void put(String path, Map<String, Object> data);

    void put(String path, Object data);

    // Delete
    void delete(String path);

    void delete(String path, int... versions);

    void undelete(String path, int... versions);

    void destroy(String path, int... versions);

    // Metadata & List
    VaultKvMetadata metadata(String path);

    List<String> list(String path);
}

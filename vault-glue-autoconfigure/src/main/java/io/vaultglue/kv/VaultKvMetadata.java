package io.vaultglue.kv;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record VaultKvMetadata(
    int currentVersion,
    int oldestVersion,
    Instant createdTime,
    Instant updatedTime,
    Map<String, String> customMetadata
) {
    public VaultKvMetadata {
        customMetadata = customMetadata != null
                ? Collections.unmodifiableMap(customMetadata)
                : Collections.emptyMap();
    }
}

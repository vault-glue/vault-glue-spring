package io.vaultglue.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;

public class DefaultVaultKvOperations implements VaultKvOperations {

    private static final Logger log = LoggerFactory.getLogger(DefaultVaultKvOperations.class);

    private final VaultTemplate vaultTemplate;
    private final VaultKeyValueOperations kvOps;
    private final VaultGlueKvProperties properties;
    private final ObjectMapper objectMapper;

    public DefaultVaultKvOperations(VaultTemplate vaultTemplate,
                                     VaultGlueKvProperties properties,
                                     ObjectMapper objectMapper) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;

        KeyValueBackend kvBackend = properties.getVersion() == 1
                ? KeyValueBackend.KV_1
                : KeyValueBackend.KV_2;
        this.kvOps = vaultTemplate.opsForKeyValue(properties.getBackend(), kvBackend);
    }

    @Override
    public Map<String, Object> get(String path) {
        log.debug("[VaultGlue] KV get: {}", path);
        var response = kvOps.get(path);
        if (response == null || response.getData() == null) {
            return Collections.emptyMap();
        }
        return response.getData();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String path, Class<T> type) {
        Map<String, Object> data = get(path);
        if (data.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(data, type);
    }

    @Override
    public Map<String, Object> get(String path, int version) {
        if (properties.getVersion() != 2) {
            throw new UnsupportedOperationException("Versioned get is only supported with KV v2");
        }
        log.debug("[VaultGlue] KV get version {}: {}", version, path);
        var versionedOps = vaultTemplate.opsForVersionedKeyValue(properties.getBackend());
        @SuppressWarnings("unchecked")
        Versioned<Map<String, Object>> versioned =
                (Versioned<Map<String, Object>>) (Versioned<?>)
                        versionedOps.get(path, Versioned.Version.from(version));
        if (versioned == null || versioned.getData() == null) {
            return Collections.emptyMap();
        }
        return versioned.getData();
    }

    @Override
    public void put(String path, Map<String, Object> data) {
        log.debug("[VaultGlue] KV put: {}", path);
        kvOps.put(path, data);
    }

    @Override
    public void put(String path, Object data) {
        log.debug("[VaultGlue] KV put (object): {}", path);
        kvOps.put(path, data);
    }

    @Override
    public void delete(String path) {
        log.debug("[VaultGlue] KV delete: {}", path);
        kvOps.delete(path);
    }

    @Override
    public void delete(String path, int... versions) {
        if (properties.getVersion() != 2) {
            throw new UnsupportedOperationException("Versioned delete is only supported with KV v2");
        }
        log.debug("[VaultGlue] KV delete versions: {}", path);
        // KV v2 soft delete specific versions
        String fullPath = properties.getBackend() + "/delete/" + path;
        Map<String, Object> body = Map.of("versions", toIntList(versions));
        vaultTemplate.write(fullPath, body);
    }

    @Override
    public void undelete(String path, int... versions) {
        if (properties.getVersion() != 2) {
            throw new UnsupportedOperationException("Undelete is only supported with KV v2");
        }
        log.debug("[VaultGlue] KV undelete: {}", path);
        String fullPath = properties.getBackend() + "/undelete/" + path;
        Map<String, Object> body = Map.of("versions", toIntList(versions));
        vaultTemplate.write(fullPath, body);
    }

    @Override
    public void destroy(String path, int... versions) {
        if (properties.getVersion() != 2) {
            throw new UnsupportedOperationException("Destroy is only supported with KV v2");
        }
        log.debug("[VaultGlue] KV destroy: {}", path);
        String fullPath = properties.getBackend() + "/destroy/" + path;
        Map<String, Object> body = Map.of("versions", toIntList(versions));
        vaultTemplate.write(fullPath, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public VaultKvMetadata metadata(String path) {
        if (properties.getVersion() != 2) {
            throw new UnsupportedOperationException("Metadata is only supported with KV v2");
        }
        String fullPath = properties.getBackend() + "/metadata/" + path;
        VaultResponse response = vaultTemplate.read(fullPath);
        if (response == null || response.getData() == null) {
            return new VaultKvMetadata(0, 0, Instant.EPOCH, Instant.EPOCH, Collections.emptyMap());
        }
        Map<String, Object> data = response.getData();
        return new VaultKvMetadata(
                toInt(data.get("current_version")),
                toInt(data.get("oldest_version")),
                parseInstant(data.get("created_time")),
                parseInstant(data.get("updated_time")),
                data.get("custom_metadata") instanceof Map
                        ? (Map<String, String>) data.get("custom_metadata")
                        : Collections.emptyMap()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> list(String path) {
        log.debug("[VaultGlue] KV list: {}", path);
        var response = kvOps.list(path);
        return response != null ? response : Collections.emptyList();
    }

    private List<Integer> toIntList(int[] arr) {
        return Arrays.stream(arr).boxed().toList();
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private Instant parseInstant(Object value) {
        if (value instanceof String s) {
            try {
                return Instant.parse(s);
            } catch (Exception e) {
                return Instant.EPOCH;
            }
        }
        return Instant.EPOCH;
    }
}

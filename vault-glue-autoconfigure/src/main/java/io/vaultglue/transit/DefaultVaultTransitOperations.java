package io.vaultglue.transit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import io.vaultglue.core.VaultResponseParseUtils;

public class DefaultVaultTransitOperations implements VaultTransitOperations {

    private static final Logger log = LoggerFactory.getLogger(DefaultVaultTransitOperations.class);

    private final VaultTemplate vaultTemplate;
    private final VaultGlueTransitProperties properties;

    public DefaultVaultTransitOperations(VaultTemplate vaultTemplate,
                                          VaultGlueTransitProperties properties) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
    }

    private String transitPath(String action) {
        return properties.getBackend() + "/" + action;
    }

    // ─── Encrypt / Decrypt ───────────────────────────────────────

    @Override
    public String encrypt(String keyName, String plaintext) {
        return encrypt(keyName, plaintext, null);
    }

    @Override
    public String encrypt(String keyName, String plaintext, String context) {
        Map<String, Object> body = new HashMap<>();
        body.put("plaintext", Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8)));
        if (context != null && !context.isEmpty()) {
            body.put("context", Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8)));
        }

        VaultResponse response = vaultTemplate.write(transitPath("encrypt/" + keyName), body);
        return extractString(response, "ciphertext");
    }

    @Override
    public String decrypt(String keyName, String ciphertext) {
        return decrypt(keyName, ciphertext, null);
    }

    @Override
    public String decrypt(String keyName, String ciphertext, String context) {
        Map<String, Object> body = new HashMap<>();
        body.put("ciphertext", ciphertext);
        if (context != null && !context.isEmpty()) {
            body.put("context", Base64.getEncoder().encodeToString(context.getBytes(StandardCharsets.UTF_8)));
        }

        VaultResponse response = vaultTemplate.write(transitPath("decrypt/" + keyName), body);
        String base64Plaintext = extractString(response, "plaintext");
        try {
            return new String(Base64.getDecoder().decode(base64Plaintext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new VaultTransitException("Invalid Base64 plaintext in transit decrypt response", e);
        }
    }

    // ─── Batch ───────────────────────────────────────────────────

    @Override
    public BatchResult<String> encryptBatch(String keyName, List<String> plaintexts) {
        List<Map<String, String>> batchInput = plaintexts.stream()
                .map(p -> Map.of("plaintext", Base64.getEncoder().encodeToString(p.getBytes(StandardCharsets.UTF_8))))
                .toList();

        VaultResponse response = vaultTemplate.write(
                transitPath("encrypt/" + keyName),
                Map.of("batch_input", batchInput));

        return extractBatchResults(response, "ciphertext");
    }

    @Override
    public BatchResult<String> decryptBatch(String keyName, List<String> ciphertexts) {
        List<Map<String, String>> batchInput = ciphertexts.stream()
                .map(c -> Map.of("ciphertext", c))
                .toList();

        VaultResponse response = vaultTemplate.write(
                transitPath("decrypt/" + keyName),
                Map.of("batch_input", batchInput));

        BatchResult<String> rawResult = extractBatchResults(response, "plaintext");

        List<BatchResultItem<String>> decoded = rawResult.items().stream()
                .map(item -> {
                    if (item.isSuccess()) {
                        String plain = new String(Base64.getDecoder().decode(item.value()), StandardCharsets.UTF_8);
                        return new BatchResultItem<String>(item.index(), plain, null);
                    }
                    return item;
                })
                .toList();
        return new BatchResult<>(decoded);
    }

    // ─── Rewrap ──────────────────────────────────────────────────

    @Override
    public String rewrap(String keyName, String ciphertext) {
        VaultResponse response = vaultTemplate.write(
                transitPath("rewrap/" + keyName),
                Map.of("ciphertext", ciphertext));
        return extractString(response, "ciphertext");
    }

    @Override
    public BatchResult<String> rewrapBatch(String keyName, List<String> ciphertexts) {
        List<Map<String, String>> batchInput = ciphertexts.stream()
                .map(c -> Map.of("ciphertext", c))
                .toList();

        VaultResponse response = vaultTemplate.write(
                transitPath("rewrap/" + keyName),
                Map.of("batch_input", batchInput));

        return extractBatchResults(response, "ciphertext");
    }

    // ─── HMAC ────────────────────────────────────────────────────

    @Override
    public String hmac(String keyName, String data) {
        return hmac(keyName, data, null);
    }

    @Override
    public String hmac(String keyName, String data, String algorithm) {
        Map<String, Object> body = new HashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
        if (algorithm != null) {
            body.put("algorithm", algorithm);
        }

        VaultResponse response = vaultTemplate.write(transitPath("hmac/" + keyName), body);
        return extractString(response, "hmac");
    }

    @Override
    public boolean verifyHmac(String keyName, String data, String hmac) {
        Map<String, Object> body = new HashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
        body.put("hmac", hmac);

        VaultResponse response = vaultTemplate.write(transitPath("verify/" + keyName), body);
        return extractBoolean(response, "valid");
    }

    // ─── Sign / Verify ───────────────────────────────────────────

    @Override
    public String sign(String keyName, String data) {
        return sign(keyName, data, null, null);
    }

    @Override
    public String sign(String keyName, String data, String hashAlgorithm, String signatureAlgorithm) {
        Map<String, Object> body = new HashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
        if (hashAlgorithm != null) {
            body.put("hash_algorithm", hashAlgorithm);
        }
        if (signatureAlgorithm != null) {
            body.put("signature_algorithm", signatureAlgorithm);
        }

        VaultResponse response = vaultTemplate.write(transitPath("sign/" + keyName), body);
        return extractString(response, "signature");
    }

    @Override
    public boolean verify(String keyName, String data, String signature) {
        Map<String, Object> body = new HashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
        body.put("signature", signature);

        VaultResponse response = vaultTemplate.write(transitPath("verify/" + keyName), body);
        return extractBoolean(response, "valid");
    }

    // ─── Key Management ──────────────────────────────────────────

    @Override
    public void createKey(String keyName, TransitKeyType type) {
        log.info("[VaultGlue] Creating transit key: {} ({})", keyName, type.getValue());
        vaultTemplate.write(
                transitPath("keys/" + keyName),
                Map.of("type", type.getValue()));
    }

    @Override
    public void rotateKey(String keyName) {
        log.info("[VaultGlue] Rotating transit key: {}", keyName);
        vaultTemplate.write(transitPath("keys/" + keyName + "/rotate"), null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public TransitKeyInfo getKeyInfo(String keyName) {
        VaultResponse response = vaultTemplate.read(transitPath("keys/" + keyName));
        if (response == null || response.getData() == null) {
            return null;
        }
        Map<String, Object> data = response.getData();
        return new TransitKeyInfo(
                (String) data.get("name"),
                (String) data.get("type"),
                toInt(data.get("latest_version")),
                toInt(data.get("min_decryption_version")),
                toInt(data.get("min_encryption_version")),
                toBoolean(data.get("deletion_allowed")),
                toBoolean(data.get("exportable")),
                toBoolean(data.get("supports_encryption")),
                toBoolean(data.get("supports_decryption")),
                toBoolean(data.get("supports_signing"))
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private String extractString(VaultResponse response, String key) {
        if (response == null || response.getData() == null) {
            throw new VaultTransitException("Empty response from Vault transit");
        }
        Object value = response.getData().get(key);
        if (value == null) {
            throw new VaultTransitException("Missing '" + key + "' in transit response");
        }
        return value.toString();
    }

    private boolean extractBoolean(VaultResponse response, String key) {
        if (response == null || response.getData() == null) {
            throw new VaultTransitException("Empty response from Vault transit");
        }
        Object value = response.getData().get(key);
        if (value == null) {
            throw new VaultTransitException("Missing '" + key + "' in transit response");
        }
        return toBoolean(value);
    }

    @SuppressWarnings("unchecked")
    private BatchResult<String> extractBatchResults(VaultResponse response, String key) {
        if (response == null || response.getData() == null) {
            throw new VaultTransitException("Empty batch response from Vault transit");
        }
        List<Map<String, Object>> batchResults =
                (List<Map<String, Object>>) response.getData().get("batch_results");
        if (batchResults == null) {
            throw new VaultTransitException("Missing 'batch_results' in transit response");
        }
        List<BatchResultItem<String>> items = new ArrayList<>(batchResults.size());
        for (int i = 0; i < batchResults.size(); i++) {
            Map<String, Object> item = batchResults.get(i);
            if (item.containsKey("error")) {
                items.add(new BatchResultItem<>(i, null, item.get("error").toString()));
            } else {
                Object value = item.get(key);
                if (value == null) {
                    items.add(new BatchResultItem<>(i, null,
                            "Missing '" + key + "' in batch result item"));
                } else {
                    items.add(new BatchResultItem<>(i, value.toString(), null));
                }
            }
        }
        return new BatchResult<>(items);
    }

    private int toInt(Object value) {
        return VaultResponseParseUtils.toInt(value);
    }

    private boolean toBoolean(Object value) {
        return VaultResponseParseUtils.toBoolean(value);
    }

}

package io.vaultglue.transit;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

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
        body.put("plaintext", Base64.getEncoder().encodeToString(plaintext.getBytes()));
        if (context != null && !context.isEmpty()) {
            body.put("context", Base64.getEncoder().encodeToString(context.getBytes()));
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
            body.put("context", Base64.getEncoder().encodeToString(context.getBytes()));
        }

        VaultResponse response = vaultTemplate.write(transitPath("decrypt/" + keyName), body);
        String base64Plaintext = extractString(response, "plaintext");
        return new String(Base64.getDecoder().decode(base64Plaintext));
    }

    // ─── Batch ───────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<String> encryptBatch(String keyName, List<String> plaintexts) {
        List<Map<String, String>> batchInput = plaintexts.stream()
                .map(p -> Map.of("plaintext", Base64.getEncoder().encodeToString(p.getBytes())))
                .toList();

        VaultResponse response = vaultTemplate.write(
                transitPath("encrypt/" + keyName),
                Map.of("batch_input", batchInput));

        return extractBatchResults(response, "ciphertext");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> decryptBatch(String keyName, List<String> ciphertexts) {
        List<Map<String, String>> batchInput = ciphertexts.stream()
                .map(c -> Map.of("ciphertext", c))
                .toList();

        VaultResponse response = vaultTemplate.write(
                transitPath("decrypt/" + keyName),
                Map.of("batch_input", batchInput));

        List<String> base64Results = extractBatchResults(response, "plaintext");
        return base64Results.stream()
                .map(b64 -> new String(Base64.getDecoder().decode(b64)))
                .toList();
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
    public List<String> rewrapBatch(String keyName, List<String> ciphertexts) {
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
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes()));
        if (algorithm != null) {
            body.put("algorithm", algorithm);
        }

        VaultResponse response = vaultTemplate.write(transitPath("hmac/" + keyName), body);
        return extractString(response, "hmac");
    }

    @Override
    public boolean verifyHmac(String keyName, String data, String hmac) {
        Map<String, Object> body = new HashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes()));
        body.put("hmac", hmac);

        VaultResponse response = vaultTemplate.write(transitPath("verify/" + keyName), body);
        return extractBoolean(response, "valid");
    }

    // ─── Sign / Verify ───────────────────────────────────────────

    @Override
    public String sign(String keyName, String data) {
        return sign(keyName, data, null);
    }

    @Override
    public String sign(String keyName, String data, String algorithm) {
        Map<String, Object> body = new HashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes()));
        if (algorithm != null) {
            body.put("signature_algorithm", algorithm);
        }

        VaultResponse response = vaultTemplate.write(transitPath("sign/" + keyName), body);
        return extractString(response, "signature");
    }

    @Override
    public boolean verify(String keyName, String data, String signature) {
        Map<String, Object> body = new HashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(data.getBytes()));
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
            return false;
        }
        return toBoolean(response.getData().get(key));
    }

    @SuppressWarnings("unchecked")
    private List<String> extractBatchResults(VaultResponse response, String key) {
        if (response == null || response.getData() == null) {
            throw new VaultTransitException("Empty batch response from Vault transit");
        }
        List<Map<String, Object>> batchResults =
                (List<Map<String, Object>>) response.getData().get("batch_results");
        if (batchResults == null) {
            throw new VaultTransitException("Missing 'batch_results' in transit response");
        }
        List<String> results = new ArrayList<>(batchResults.size());
        for (Map<String, Object> item : batchResults) {
            if (item.containsKey("error")) {
                throw new VaultTransitException("Batch item error: " + item.get("error"));
            }
            results.add(item.get(key).toString());
        }
        return results;
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    public static class VaultTransitException extends RuntimeException {
        public VaultTransitException(String message) {
            super(message);
        }
    }
}

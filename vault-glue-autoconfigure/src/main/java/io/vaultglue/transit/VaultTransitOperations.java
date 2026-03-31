package io.vaultglue.transit;

import java.util.List;

public interface VaultTransitOperations {

    // Encrypt / Decrypt
    String encrypt(String keyName, String plaintext);

    String encrypt(String keyName, String plaintext, String context);

    String decrypt(String keyName, String ciphertext);

    String decrypt(String keyName, String ciphertext, String context);

    // Batch
    BatchResult<String> encryptBatch(String keyName, List<String> plaintexts);

    BatchResult<String> decryptBatch(String keyName, List<String> ciphertexts);

    // Rewrap (re-encrypt with latest key version)
    String rewrap(String keyName, String ciphertext);

    BatchResult<String> rewrapBatch(String keyName, List<String> ciphertexts);

    // HMAC
    String hmac(String keyName, String data);

    String hmac(String keyName, String data, String algorithm);

    boolean verifyHmac(String keyName, String data, String hmac);

    // Sign / Verify
    String sign(String keyName, String data);

    String sign(String keyName, String data, String hashAlgorithm, String signatureAlgorithm);

    boolean verify(String keyName, String data, String signature);

    // Key Management
    void createKey(String keyName, TransitKeyType type);

    void rotateKey(String keyName);

    TransitKeyInfo getKeyInfo(String keyName);
}

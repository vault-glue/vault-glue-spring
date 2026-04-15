package io.vaultglue.core;

public final class VaultPathUtils {

    private VaultPathUtils() {}

    /**
     * Validates that a value is safe to use as a single Vault path segment.
     * Rejects null, blank, path traversal (..), forward/back slashes, and control characters.
     */
    public static void validatePathSegment(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " must not be null or blank");
        }
        if (value.contains("/") || value.contains("..")) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " contains invalid path characters");
        }
        if (value.contains("\\") || value.chars().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " contains invalid characters");
        }
    }

    /**
     * Validates a Vault mount path (backend). Allows forward slashes for nested mounts
     * but rejects path traversal.
     */
    public static void validateMountPath(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " must not be null or blank");
        }
        if (value.contains("..") || value.startsWith("/") || value.endsWith("/")) {
            throw new IllegalArgumentException(
                    "[VaultGlue] " + paramName + " contains invalid path characters");
        }
    }
}

package io.vaultglue.core;

/**
 * Utility methods for parsing untyped values from Vault responses.
 */
public final class VaultResponseParseUtils {

    private VaultResponseParseUtils() {}

    public static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    public static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }
}

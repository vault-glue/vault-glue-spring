package io.vaultglue.core;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VaultGlueTimeUtils {

    private static final Logger log = LoggerFactory.getLogger(VaultGlueTimeUtils.class);

    private VaultGlueTimeUtils() {}

    /**
     * Parses a TTL string (e.g. "72h", "30m", "7d", "3600s") into a Duration.
     * Falls back to the given default if parsing fails.
     */
    public static Duration parseTtl(String ttl, Duration defaultValue) {
        try {
            if (ttl.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(ttl.substring(0, ttl.length() - 1)));
            } else if (ttl.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(ttl.substring(0, ttl.length() - 1)));
            } else if (ttl.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(ttl.substring(0, ttl.length() - 1)));
            } else if (ttl.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(ttl.substring(0, ttl.length() - 1)));
            }
        } catch (NumberFormatException e) {
            log.warn("[VaultGlue] Failed to parse TTL '{}': {}. Using default: {}",
                    ttl, e.getMessage(), defaultValue);
            return defaultValue;
        }
        log.warn("[VaultGlue] Unrecognized TTL format '{}'. Supported: <number>[d|h|m|s]. Using default: {}",
                ttl, defaultValue);
        return defaultValue;
    }

    /**
     * Parses a TTL string into milliseconds. Falls back to the given default.
     */
    public static long parseTtlMs(String ttl, long defaultMs) {
        return parseTtl(ttl, Duration.ofMillis(defaultMs)).toMillis();
    }
}

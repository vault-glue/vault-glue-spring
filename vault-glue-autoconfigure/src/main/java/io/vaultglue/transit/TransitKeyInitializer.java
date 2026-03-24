package io.vaultglue.transit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class TransitKeyInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TransitKeyInitializer.class);

    private final VaultTransitOperations transitOperations;
    private final VaultGlueTransitProperties properties;

    public TransitKeyInitializer(VaultTransitOperations transitOperations,
                                  VaultGlueTransitProperties properties) {
        this.transitOperations = transitOperations;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.getKeys().forEach((name, keyProps) -> {
            if (!keyProps.isAutoCreate()) return;

            try {
                TransitKeyInfo info = transitOperations.getKeyInfo(name);
                if (info != null) {
                    log.info("[VaultGlue] Transit key already exists: {} (type={}, version={})",
                            name, info.type(), info.latestVersion());
                    return;
                }
            } catch (Exception e) {
                log.debug("[VaultGlue] Could not read transit key '{}': {}", name, e.getMessage());
            }

            try {
                transitOperations.createKey(name, keyProps.getType());
                log.info("[VaultGlue] Transit key created: {} ({})", name, keyProps.getType().getValue());
            } catch (Exception e) {
                log.error("[VaultGlue] Failed to create transit key: {}", name, e);
            }
        });
    }
}

package io.vaultglue.transit;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault-glue.transit")
public class VaultGlueTransitProperties {

    private boolean enabled = false;
    private String backend = "transit";
    private String defaultKey;
    private boolean allowPlaintextRead = false;
    private Map<String, TransitKeyProperties> keys = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public String getDefaultKey() { return defaultKey; }
    public void setDefaultKey(String defaultKey) { this.defaultKey = defaultKey; }
    public boolean isAllowPlaintextRead() { return allowPlaintextRead; }
    public void setAllowPlaintextRead(boolean allowPlaintextRead) { this.allowPlaintextRead = allowPlaintextRead; }
    public Map<String, TransitKeyProperties> getKeys() { return keys; }
    public void setKeys(Map<String, TransitKeyProperties> keys) { this.keys = keys; }

    public static class TransitKeyProperties {
        private TransitKeyType type = TransitKeyType.AES256_GCM96;
        private boolean autoCreate = false;

        public TransitKeyType getType() { return type; }
        public void setType(TransitKeyType type) { this.type = type; }
        public boolean isAutoCreate() { return autoCreate; }
        public void setAutoCreate(boolean autoCreate) { this.autoCreate = autoCreate; }
    }
}

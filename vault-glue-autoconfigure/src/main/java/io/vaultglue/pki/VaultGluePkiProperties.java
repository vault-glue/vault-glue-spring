package io.vaultglue.pki;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault-glue.pki")
public class VaultGluePkiProperties {

    private boolean enabled = false;
    private String backend = "pki";
    private String role;
    private String commonName;
    private String ttl = "72h";
    private boolean autoRenew = true;
    private boolean configureSsl = false;
    private long checkInterval = 3_600_000;
    private long renewThresholdHours = 24;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCommonName() { return commonName; }
    public void setCommonName(String commonName) { this.commonName = commonName; }
    public String getTtl() { return ttl; }
    public void setTtl(String ttl) { this.ttl = ttl; }
    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
    public boolean isConfigureSsl() { return configureSsl; }
    public void setConfigureSsl(boolean configureSsl) { this.configureSsl = configureSsl; }
    public long getCheckInterval() { return checkInterval; }
    public void setCheckInterval(long checkInterval) { this.checkInterval = checkInterval; }
    public long getRenewThresholdHours() { return renewThresholdHours; }
    public void setRenewThresholdHours(long renewThresholdHours) { this.renewThresholdHours = renewThresholdHours; }
}

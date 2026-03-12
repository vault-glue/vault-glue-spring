package io.vaultglue.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault-glue.aws")
public class VaultGlueAwsProperties {

    private boolean enabled = false;
    private String backend = "aws";
    private String role;
    private String credentialType = "sts";
    private String ttl = "1h";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }
    public String getTtl() { return ttl; }
    public void setTtl(String ttl) { this.ttl = ttl; }
}

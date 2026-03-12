package io.vaultglue.kv;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault-glue.kv")
public class VaultGlueKvProperties {

    private boolean enabled = false;
    private String backend = "secret";
    private int version = 2;
    private String applicationName;
    private WatchProperties watch = new WatchProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public WatchProperties getWatch() { return watch; }
    public void setWatch(WatchProperties watch) { this.watch = watch; }

    public static class WatchProperties {
        private boolean enabled = false;
        private Duration interval = Duration.ofSeconds(30);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
    }
}

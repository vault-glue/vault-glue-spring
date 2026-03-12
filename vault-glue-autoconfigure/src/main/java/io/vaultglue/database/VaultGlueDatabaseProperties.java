package io.vaultglue.database;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault-glue.database")
public class VaultGlueDatabaseProperties {

    private Map<String, DataSourceProperties> sources = new LinkedHashMap<>();

    public Map<String, DataSourceProperties> getSources() {
        return sources;
    }

    public void setSources(Map<String, DataSourceProperties> sources) {
        this.sources = sources;
    }

    public static class DataSourceProperties {
        private boolean enabled = true;
        private boolean primary = false;
        private String type;  // "static" | "dynamic"
        private String role;
        private String backend = "db";
        private String jdbcUrl;
        private String driverClassName;
        private long refreshInterval = 18_000_000;  // 5 hours (static only)
        private HikariProperties hikari = new HikariProperties();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isPrimary() { return primary; }
        public void setPrimary(boolean primary) { this.primary = primary; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
        public long getRefreshInterval() { return refreshInterval; }
        public void setRefreshInterval(long refreshInterval) { this.refreshInterval = refreshInterval; }
        public HikariProperties getHikari() { return hikari; }
        public void setHikari(HikariProperties hikari) { this.hikari = hikari; }
    }

    public static class HikariProperties {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long maxLifetime = 1_800_000;
        private long idleTimeout = 600_000;
        private long connectionTimeout = 30_000;
        private long validationTimeout = 5_000;
        private long leakDetectionThreshold = 0;

        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
        public long getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }
        public long getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(long idleTimeout) { this.idleTimeout = idleTimeout; }
        public long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public long getValidationTimeout() { return validationTimeout; }
        public void setValidationTimeout(long validationTimeout) { this.validationTimeout = validationTimeout; }
        public long getLeakDetectionThreshold() { return leakDetectionThreshold; }
        public void setLeakDetectionThreshold(long leakDetectionThreshold) { this.leakDetectionThreshold = leakDetectionThreshold; }
    }
}

package io.vaultglue.core;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.vaultglue.autoconfigure.VaultGlueDatabaseAutoConfiguration.VaultGlueDataSources;
import io.vaultglue.database.VaultGlueDelegatingDataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

public class VaultGlueHealthIndicator implements HealthIndicator {

    private final VaultGlueDataSources dataSources;

    public VaultGlueHealthIndicator(VaultGlueDataSources dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean anyDown = false;

        for (var entry : dataSources.getAll().entrySet()) {
            String name = entry.getKey();
            VaultGlueDelegatingDataSource ds = entry.getValue();
            Map<String, Object> details = new LinkedHashMap<>();

            details.put("lastRotation", ds.getLastRotationTime().toString());
            details.put("currentUsername", ds.getCurrentUsername());

            if (ds.getDelegate() instanceof HikariDataSource hikari) {
                details.put("poolName", hikari.getPoolName());
                HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                if (pool != null) {
                    details.put("activeConnections", pool.getActiveConnections());
                    details.put("idleConnections", pool.getIdleConnections());
                    details.put("totalConnections", pool.getTotalConnections());
                    details.put("threadsAwaiting", pool.getThreadsAwaitingConnection());
                    details.put("status", "UP");
                } else {
                    details.put("status", "UNKNOWN");
                }

                if (hikari.isClosed()) {
                    details.put("status", "DOWN");
                    anyDown = true;
                }
            }

            builder.withDetail("database." + name, details);
        }

        return anyDown ? builder.down().build() : builder.build();
    }
}

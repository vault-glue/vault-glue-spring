package io.vaultglue.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.vaultglue.autoconfigure.VaultGlueDatabaseAutoConfiguration.VaultGlueDataSources;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.VaultGlueHealthIndicator;
import io.vaultglue.database.VaultGlueDelegatingDataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = VaultGlueDatabaseAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(VaultGlueEventPublisher.class)
@ConditionalOnProperty(prefix = "vault-glue.actuator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VaultGlueHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "vaultGlueHealthIndicator")
    public VaultGlueHealthIndicator vaultGlueHealthIndicator(
            ObjectProvider<VaultGlueDataSources> dataSourcesProvider) {
        VaultGlueHealthIndicator indicator = new VaultGlueHealthIndicator();

        VaultGlueDataSources dataSources = dataSourcesProvider.getIfAvailable();
        if (dataSources != null) {
            for (var entry : dataSources.getAll().entrySet()) {
                String name = entry.getKey();
                VaultGlueDelegatingDataSource ds = entry.getValue();
                indicator.addContributor("database." + name, () -> buildDatabaseHealth(ds));
            }
        }

        return indicator;
    }

    private Map<String, Object> buildDatabaseHealth(VaultGlueDelegatingDataSource ds) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("lastRotation", ds.getLastRotationTime());
        details.put("currentUsername", ds.getCurrentUsername());

        try {
            if (ds.getDelegate() instanceof HikariDataSource hikari) {
                details.put("poolName", hikari.getPoolName());
                if (hikari.isClosed()) {
                    details.put("status", "DOWN");
                    return details;
                }
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
            }
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }

        return details;
    }
}

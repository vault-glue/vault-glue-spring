package io.vaultglue.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

public class VaultGlueHealthIndicator implements HealthIndicator {

    private final Map<String, HealthContributor> contributors = new ConcurrentHashMap<>();

    public void addContributor(String name, HealthContributor contributor) {
        contributors.put(name, contributor);
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean anyDown = false;

        for (var entry : contributors.entrySet()) {
            Map<String, Object> details = entry.getValue().health();
            builder.withDetail(entry.getKey(), details);

            Object status = details.get("status");
            if ("DOWN".equals(status)) {
                anyDown = true;
            }
        }

        if (contributors.isEmpty()) {
            builder.withDetail("message", "No VaultGlue engines registered");
        }

        return anyDown ? builder.down().build() : builder.build();
    }

    @FunctionalInterface
    public interface HealthContributor {
        Map<String, Object> health();
    }
}

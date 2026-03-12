package io.vaultglue.autoconfigure;

import io.vaultglue.autoconfigure.VaultGlueDatabaseAutoConfiguration.VaultGlueDataSources;
import io.vaultglue.core.VaultGlueHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = VaultGlueDatabaseAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "vault-glue.actuator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VaultGlueHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "vaultGlueHealthIndicator")
    @ConditionalOnBean(VaultGlueDataSources.class)
    public VaultGlueHealthIndicator vaultGlueHealthIndicator(VaultGlueDataSources dataSources) {
        return new VaultGlueHealthIndicator(dataSources);
    }
}

package io.vaultglue.autoconfigure;

import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.VaultGlueProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;

@AutoConfiguration
@ConditionalOnClass(VaultTemplate.class)
@EnableConfigurationProperties(VaultGlueProperties.class)
public class VaultGlueCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultGlueEventPublisher vaultGlueEventPublisher(ApplicationEventPublisher publisher) {
        return new VaultGlueEventPublisher(publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public FailureStrategyHandler vaultGlueFailureStrategyHandler(
            VaultGlueProperties properties,
            VaultGlueEventPublisher eventPublisher,
            ConfigurableApplicationContext applicationContext) {
        return new FailureStrategyHandler(properties, eventPublisher, applicationContext);
    }
}

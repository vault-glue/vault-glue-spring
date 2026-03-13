package io.vaultglue.autoconfigure;

import io.vaultglue.transit.DefaultVaultTransitOperations;
import io.vaultglue.transit.TransitKeyInitializer;
import io.vaultglue.transit.VaultEncryptConverter;
import io.vaultglue.transit.VaultTransitOperations;
import io.vaultglue.transit.VaultGlueTransitProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;

@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "vault-glue.transit", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties(VaultGlueTransitProperties.class)
public class VaultGlueTransitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultTransitOperations vaultTransitOperations(VaultTemplate vaultTemplate,
                                                          VaultGlueTransitProperties properties) {
        return new DefaultVaultTransitOperations(vaultTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransitKeyInitializer transitKeyInitializer(VaultTransitOperations transitOperations,
                                                        VaultGlueTransitProperties properties) {
        return new TransitKeyInitializer(transitOperations, properties);
    }

    @Bean
    public VaultEncryptConverterInitializer vaultEncryptConverterInitializer(
            ApplicationContext applicationContext,
            VaultGlueTransitProperties properties) {
        return new VaultEncryptConverterInitializer(applicationContext, properties);
    }

    static class VaultEncryptConverterInitializer {
        VaultEncryptConverterInitializer(ApplicationContext applicationContext,
                                          VaultGlueTransitProperties properties) {
            // Use the first key as the default key
            String defaultKey = properties.getKeys().isEmpty()
                    ? "default"
                    : properties.getKeys().keySet().iterator().next();
            VaultEncryptConverter.initialize(applicationContext, defaultKey);
        }
    }
}

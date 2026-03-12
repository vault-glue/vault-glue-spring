package io.vaultglue.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vaultglue.kv.DefaultVaultKvOperations;
import io.vaultglue.kv.VaultKvOperations;
import io.vaultglue.kv.VaultKvWatcher;
import io.vaultglue.kv.VaultValueBeanPostProcessor;
import io.vaultglue.kv.VaultGlueKvProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;

@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "vault-glue.kv", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties(VaultGlueKvProperties.class)
public class VaultGlueKvAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultKvOperations vaultKvOperations(VaultTemplate vaultTemplate,
                                                VaultGlueKvProperties properties,
                                                ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new DefaultVaultKvOperations(vaultTemplate, properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public VaultValueBeanPostProcessor vaultValueBeanPostProcessor(VaultKvOperations kvOperations) {
        return new VaultValueBeanPostProcessor(kvOperations);
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vault-glue.kv.watch", name = "enabled", havingValue = "true")
    public VaultKvWatcher vaultKvWatcher(VaultKvOperations kvOperations,
                                         VaultValueBeanPostProcessor beanPostProcessor,
                                         VaultGlueKvProperties properties) {
        return new VaultKvWatcher(kvOperations, beanPostProcessor, properties);
    }
}

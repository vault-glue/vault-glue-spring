package io.vaultglue.autoconfigure;

import io.vaultglue.aws.VaultAwsCredentialProvider;
import io.vaultglue.aws.VaultGlueAwsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;

@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "vault-glue.aws", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties(VaultGlueAwsProperties.class)
public class VaultGlueAwsAutoConfiguration {

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public VaultAwsCredentialProvider vaultAwsCredentialProvider(VaultTemplate vaultTemplate,
                                                                  VaultGlueAwsProperties properties) {
        return new VaultAwsCredentialProvider(vaultTemplate, properties);
    }
}

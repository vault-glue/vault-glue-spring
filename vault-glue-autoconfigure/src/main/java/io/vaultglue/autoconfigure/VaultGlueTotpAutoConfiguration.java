package io.vaultglue.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;
import io.vaultglue.totp.DefaultVaultTotpOperations;
import io.vaultglue.totp.VaultTotpOperations;
import io.vaultglue.totp.VaultGlueTotpProperties;

@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "vault-glue.totp", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties(VaultGlueTotpProperties.class)
public class VaultGlueTotpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultTotpOperations vaultTotpOperations(VaultTemplate vaultTemplate,
                                                    VaultGlueTotpProperties properties) {
        return new DefaultVaultTotpOperations(vaultTemplate, properties);
    }
}

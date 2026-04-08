package io.vaultglue.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;
import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.pki.CertificateRenewalScheduler;
import io.vaultglue.pki.DefaultVaultPkiOperations;
import io.vaultglue.pki.VaultPkiOperations;
import io.vaultglue.pki.VaultGluePkiProperties;

@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "vault-glue.pki", name = "enabled", havingValue = "true")
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties(VaultGluePkiProperties.class)
public class VaultGluePkiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VaultPkiOperations vaultPkiOperations(VaultTemplate vaultTemplate,
                                                  VaultGluePkiProperties properties) {
        return new DefaultVaultPkiOperations(vaultTemplate, properties);
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vault-glue.pki", name = "auto-renew", havingValue = "true", matchIfMissing = true)
    public CertificateRenewalScheduler certificateRenewalScheduler(
            VaultPkiOperations pkiOperations,
            VaultGluePkiProperties properties,
            VaultGlueEventPublisher eventPublisher,
            FailureStrategyHandler failureStrategyHandler) {
        return new CertificateRenewalScheduler(pkiOperations, properties, eventPublisher, failureStrategyHandler);
    }
}

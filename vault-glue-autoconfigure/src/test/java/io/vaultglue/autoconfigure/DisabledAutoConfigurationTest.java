package io.vaultglue.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;
import io.vaultglue.kv.VaultKvOperations;
import io.vaultglue.transit.VaultTransitOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DisabledAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueKvAutoConfiguration.class,
                    VaultGlueTransitAutoConfiguration.class))
            .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class));

    @Test
    void allDisabled_shouldNotRegisterAnyEngineBeans() {
        contextRunner
                .withPropertyValues(
                        "vault-glue.kv.enabled=false",
                        "vault-glue.transit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultKvOperations.class);
                    assertThat(context).doesNotHaveBean(VaultTransitOperations.class);
                });
    }

    @Test
    void noVaultTemplate_shouldNotRegisterAnyBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        VaultGlueCoreAutoConfiguration.class,
                        VaultGlueKvAutoConfiguration.class,
                        VaultGlueTransitAutoConfiguration.class))
                .withPropertyValues(
                        "vault-glue.kv.enabled=true",
                        "vault-glue.transit.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultKvOperations.class);
                    assertThat(context).doesNotHaveBean(VaultTransitOperations.class);
                });
    }
}

package io.vaultglue.kv;

import io.vaultglue.autoconfigure.VaultGlueCoreAutoConfiguration;
import io.vaultglue.autoconfigure.VaultGlueKvAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class VaultGlueKvAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueKvAutoConfiguration.class
            ))
            .withBean(VaultTemplate.class, () -> Mockito.mock(VaultTemplate.class));

    @Test
    void kvEnabled_shouldRegisterBeans() {
        contextRunner
                .withPropertyValues(
                        "vault-glue.kv.enabled=true",
                        "vault-glue.kv.backend=app"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultKvOperations.class);
                    assertThat(context).hasSingleBean(VaultValueBeanPostProcessor.class);
                });
    }

    @Test
    void kvDisabled_shouldNotRegisterBeans() {
        contextRunner
                .withPropertyValues("vault-glue.kv.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultKvOperations.class);
                });
    }
}

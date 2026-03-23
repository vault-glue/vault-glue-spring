package io.vaultglue.transit;

import io.vaultglue.autoconfigure.VaultGlueCoreAutoConfiguration;
import io.vaultglue.autoconfigure.VaultGlueTransitAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class VaultGlueTransitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueTransitAutoConfiguration.class
            ))
            .withBean(VaultTemplate.class, () -> Mockito.mock(VaultTemplate.class));

    @Test
    void transitEnabled_shouldRegisterBeans() {
        contextRunner
                .withPropertyValues(
                        "vault-glue.transit.enabled=true",
                        "vault-glue.transit.backend=transit"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultTransitOperations.class);
                    assertThat(context).hasSingleBean(TransitKeyInitializer.class);
                });
    }

    @Test
    void transitDisabled_shouldNotRegisterBeans() {
        contextRunner
                .withPropertyValues("vault-glue.transit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultTransitOperations.class);
                });
    }
}

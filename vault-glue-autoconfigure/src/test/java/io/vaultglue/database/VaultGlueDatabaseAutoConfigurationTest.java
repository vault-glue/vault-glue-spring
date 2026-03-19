package io.vaultglue.database;

import com.zaxxer.hikari.HikariDataSource;
import io.vaultglue.autoconfigure.VaultGlueCoreAutoConfiguration;
import io.vaultglue.autoconfigure.VaultGlueDatabaseAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class VaultGlueDatabaseAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    VaultGlueCoreAutoConfiguration.class,
                    VaultGlueDatabaseAutoConfiguration.class
            ));

    @Test
    void withHikariAndVaultTemplate_noSources_shouldFailWithMessage() {
        contextRunner
                .withBean(VaultTemplate.class, () -> Mockito.mock(VaultTemplate.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("No DataSource sources configured");
                });
    }

    @Test
    void withoutHikari_shouldNotRegisterBeans() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(HikariDataSource.class))
                .withBean(VaultTemplate.class, () -> Mockito.mock(VaultTemplate.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DataSourceRotator.class);
                });
    }
}

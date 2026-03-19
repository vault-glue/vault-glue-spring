package io.vaultglue.database;

import io.vaultglue.autoconfigure.VaultGlueDatabaseAutoConfiguration.VaultGlueDataSources;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultGlueDataSourcesTest {

    @Test
    void get_unknownName_shouldThrowIllegalArgument() {
        DataSource primaryDs = Mockito.mock(DataSource.class);
        VaultGlueDelegatingDataSource primary =
                new VaultGlueDelegatingDataSource("primary", primaryDs, "user");

        VaultGlueDataSources sources = new VaultGlueDataSources(Map.of("primary", primary));

        assertThatThrownBy(() -> sources.get("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}

package io.vaultglue.database;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class VaultGlueDelegatingDataSourceTest {

    @Test
    void setDelegate_shouldSwapAndRouteConnections() throws SQLException {
        DataSource initialDs = Mockito.mock(DataSource.class);
        DataSource replacementDs = Mockito.mock(DataSource.class);
        Connection replacementConnection = Mockito.mock(Connection.class);

        Mockito.when(replacementDs.getConnection()).thenReturn(replacementConnection);

        VaultGlueDelegatingDataSource delegating =
                new VaultGlueDelegatingDataSource("primary", initialDs, "user_v1");

        assertThat(delegating.getCurrentUsername()).isEqualTo("user_v1");

        delegating.setDelegate(replacementDs, "user_v2");

        assertThat(delegating.getCurrentUsername()).isEqualTo("user_v2");
        assertThat(delegating.getLastRotationTime()).isNotNull();
        assertThat(delegating.getConnection()).isSameAs(replacementConnection);
    }
}

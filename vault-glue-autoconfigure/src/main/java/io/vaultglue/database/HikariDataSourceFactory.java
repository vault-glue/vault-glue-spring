package io.vaultglue.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.VaultGlueDatabaseProperties.HikariProperties;

public class HikariDataSourceFactory {

    private HikariDataSourceFactory() {}

    public static HikariDataSource create(String name, DataSourceProperties props, String username, String password) {
        return create(name, props, username, password, false);
    }

    public static HikariDataSource createPlaceholder(String name, DataSourceProperties props) {
        return create(name, props, "placeholder", "placeholder", true);
    }

    private static HikariDataSource create(String name, DataSourceProperties props,
                                            String username, String password, boolean placeholder) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getJdbcUrl());
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(props.getDriverClassName());

        if (placeholder) {
            config.setMinimumIdle(0);
            config.setMaximumPoolSize(1);
            config.setInitializationFailTimeout(-1);
            config.setConnectionTimeout(250);
        } else {
            HikariProperties hikari = props.getHikari();
            config.setMaximumPoolSize(hikari.getMaximumPoolSize());
            config.setMinimumIdle(hikari.getMinimumIdle());
            config.setMaxLifetime(hikari.getMaxLifetime());
            config.setIdleTimeout(hikari.getIdleTimeout());
            config.setConnectionTimeout(hikari.getConnectionTimeout());
            config.setValidationTimeout(hikari.getValidationTimeout());

            if (hikari.getLeakDetectionThreshold() > 0) {
                config.setLeakDetectionThreshold(hikari.getLeakDetectionThreshold());
            }
        }

        config.setPoolName("vault-glue-" + name);

        return new HikariDataSource(config);
    }
}

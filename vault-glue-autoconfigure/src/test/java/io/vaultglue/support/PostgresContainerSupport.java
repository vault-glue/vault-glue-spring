package io.vaultglue.support;

import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresContainerSupport {

    public static final String DB_NAME = "vaultglue_test";
    public static final String USERNAME = "postgres";
    public static final String PASSWORD = "test-password";
    public static final String STATIC_USERNAME = "static_user";
    public static final String STATIC_PASSWORD = "static_password";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName(DB_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withInitScript("db/postgres-init.sql");

    static {
        POSTGRES.start();
    }

    private PostgresContainerSupport() {
    }

    public static PostgreSQLContainer<?> getContainer() {
        return POSTGRES;
    }

    public static String getJdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    public static String getConnectionUrl() {
        return "postgresql://{{username}}:{{password}}@" + POSTGRES.getHost() + ":"
                + POSTGRES.getFirstMappedPort() + "/" + DB_NAME + "?sslmode=disable";
    }

    public static void initVaultRoles(VaultInitializer vault) {
        vault.configureDatabaseConnection("db", "postgres-test", "postgresql-database-plugin",
                getConnectionUrl(), "pg-static-role,pg-dynamic-role", USERNAME, PASSWORD);

        vault.createStaticRole("db", "pg-static-role", "postgres-test", STATIC_USERNAME, 86400);

        vault.createDynamicRole("db", "pg-dynamic-role", "postgres-test",
                "CREATE ROLE \\\"{{name}}\\\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \\\"{{name}}\\\";",
                "1h", "24h");
    }
}

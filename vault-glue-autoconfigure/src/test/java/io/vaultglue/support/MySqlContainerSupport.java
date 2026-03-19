package io.vaultglue.support;

import org.testcontainers.containers.MySQLContainer;

public final class MySqlContainerSupport {

    public static final String DB_NAME = "vaultglue_test";
    public static final String USERNAME = "root";
    public static final String PASSWORD = "test-password";
    public static final String STATIC_USERNAME = "static_user";
    public static final String STATIC_PASSWORD = "static_password";

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName(DB_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withInitScript("db/mysql-init.sql");

    static {
        MYSQL.start();
    }

    private MySqlContainerSupport() {
    }

    public static MySQLContainer<?> getContainer() {
        return MYSQL;
    }

    public static String getJdbcUrl() {
        return MYSQL.getJdbcUrl();
    }

    public static String getConnectionUrl() {
        return "{{username}}:{{password}}@tcp(" + MYSQL.getHost() + ":"
                + MYSQL.getFirstMappedPort() + ")/" + DB_NAME;
    }

    public static void initVaultRoles(VaultInitializer vault) {
        vault.configureDatabaseConnection("db", "mysql-test", "mysql-database-plugin",
                getConnectionUrl(), "mysql-static-role,mysql-dynamic-role", USERNAME, PASSWORD);

        vault.createStaticRole("db", "mysql-static-role", "mysql-test", STATIC_USERNAME, 86400);

        vault.createDynamicRole("db", "mysql-dynamic-role", "mysql-test",
                "CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON " + DB_NAME + ".* TO '{{name}}'@'%';",
                "1h", "24h");
    }
}

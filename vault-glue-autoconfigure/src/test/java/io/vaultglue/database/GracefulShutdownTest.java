package io.vaultglue.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class GracefulShutdownTest {

    @Test
    void execute_shouldCloseAfterActiveConnectionsDrain() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testshutdown;DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setPoolName("vault-glue-test-shutdown");
        HikariDataSource ds = new HikariDataSource(config);

        GracefulShutdown.execute(ds, 5);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(ds::isClosed);
    }
}

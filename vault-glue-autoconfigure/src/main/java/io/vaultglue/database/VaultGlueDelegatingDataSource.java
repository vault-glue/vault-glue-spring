package io.vaultglue.database;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class VaultGlueDelegatingDataSource implements DataSource {

    private volatile DataSource delegate;
    private final String name;
    private volatile Instant lastRotationTime;
    private volatile String currentUsername;

    public VaultGlueDelegatingDataSource(String name, DataSource initial, String username) {
        this.name = name;
        this.delegate = initial;
        this.lastRotationTime = Instant.now();
        this.currentUsername = username;
    }

    public void setDelegate(DataSource newDelegate, String newUsername) {
        this.delegate = newDelegate;
        this.currentUsername = newUsername;
        this.lastRotationTime = Instant.now();
    }

    public DataSource getDelegate() {
        return delegate;
    }

    public String getName() {
        return name;
    }

    public Instant getLastRotationTime() {
        return lastRotationTime;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}

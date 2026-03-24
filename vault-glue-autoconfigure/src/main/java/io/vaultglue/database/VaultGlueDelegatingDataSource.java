package io.vaultglue.database;

import com.zaxxer.hikari.HikariDataSource;
import java.io.Closeable;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class VaultGlueDelegatingDataSource implements DataSource, Closeable {

    private volatile DelegateHolder holder;
    private final String name;

    private record DelegateHolder(DataSource delegate, String username, Instant rotationTime) {}

    public VaultGlueDelegatingDataSource(String name, DataSource initial, String username) {
        this.name = name;
        this.holder = new DelegateHolder(initial, username, Instant.now());
    }

    public void setDelegate(DataSource newDelegate, String newUsername) {
        this.holder = new DelegateHolder(newDelegate, newUsername, Instant.now());
    }

    public DataSource getDelegate() {
        return holder.delegate();
    }

    public String getName() {
        return name;
    }

    public Instant getLastRotationTime() {
        return holder.rotationTime();
    }

    public String getCurrentUsername() {
        return holder.username();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return holder.delegate().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return holder.delegate().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return holder.delegate().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        holder.delegate().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        holder.delegate().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return holder.delegate().getLoginTimeout();
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
        return holder.delegate().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || holder.delegate().isWrapperFor(iface);
    }

    @Override
    public void close() {
        DataSource delegate = holder.delegate();
        if (delegate instanceof HikariDataSource hikari && !hikari.isClosed()) {
            hikari.close();
        }
    }
}

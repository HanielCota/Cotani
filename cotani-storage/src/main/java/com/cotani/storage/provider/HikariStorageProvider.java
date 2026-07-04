package com.cotani.storage.provider;

import com.cotani.storage.backend.MySqlCredentials;
import com.cotani.storage.error.ConnectionError;
import com.cotani.storage.error.StorageException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class HikariStorageProvider implements StorageProvider {

    private final String jdbcUrl;
    private final MySqlCredentials credentials;
    private final AtomicReference<@Nullable HikariDataSource> dataSource = new AtomicReference<>();

    public HikariStorageProvider(String jdbcUrl, MySqlCredentials credentials) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    @Override
    public void start() {
        var config = new HikariConfig();
        var pool = credentials.pool();
        var maxPoolSize = pool.maximumPoolSize();
        var minIdle = pool.minimumIdle();
        var connectionTimeout = pool.connectionTimeout().toMillis();
        var idleTimeout = pool.idleTimeout().toMillis();
        var maxLifetime = pool.maxLifetime().toMillis();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(credentials.username());
        config.setPassword(credentials.password());
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setPoolName("CotaniStoragePool-" + jdbcUrl.hashCode());
        var created = new HikariDataSource(config);
        if (!dataSource.compareAndSet(null, created)) {
            created.close();
            throw new IllegalStateException("HikariStorageProvider has already been started.");
        }
    }

    @Override
    public Connection connection() throws SQLException {
        var ds = dataSource.get();
        if (ds == null || ds.isClosed()) {
            throw new StorageException(new ConnectionError("Storage provider is not available.", null));
        }

        return ds.getConnection();
    }

    @Override
    public boolean available() {
        var current = dataSource.get();
        return current != null && !current.isClosed();
    }

    @Override
    public void close() {
        var current = dataSource.getAndSet(null);
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }
}

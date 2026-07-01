package br.com.cotani.storage.provider;

import br.com.cotani.storage.backend.MySqlCredentials;
import br.com.cotani.storage.error.ConnectionError;
import br.com.cotani.storage.error.StorageException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

public final class HikariStorageProvider implements StorageProvider {

    private final String jdbcUrl;
    private final MySqlCredentials credentials;
    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();

    public HikariStorageProvider(String jdbcUrl, MySqlCredentials credentials) {
        this.jdbcUrl = jdbcUrl;
        this.credentials = credentials;
    }

    @Override
    public void start() {
        HikariConfig config = new HikariConfig();
        MySqlCredentials.PoolSettings pool = credentials.pool();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(credentials.username());
        config.setPassword(credentials.password());
        config.setMaximumPoolSize(pool.maximumPoolSize());
        config.setMinimumIdle(pool.minimumIdle());
        config.setConnectionTimeout(pool.connectionTimeout().toMillis());
        config.setIdleTimeout(pool.idleTimeout().toMillis());
        config.setMaxLifetime(pool.maxLifetime().toMillis());
        config.setPoolName("CotaniStoragePool");
        this.dataSource.set(new HikariDataSource(config));
    }

    @Override
    public Connection connection() throws SQLException {
        if (!available()) {
            throw new StorageException(new ConnectionError("Storage provider is not available.", null));
        }

        return dataSource.get().getConnection();
    }

    @Override
    public boolean available() {
        HikariDataSource current = dataSource.get();
        return current != null && !current.isClosed();
    }

    @Override
    public void close() {
        HikariDataSource current = dataSource.getAndSet(null);
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }
}

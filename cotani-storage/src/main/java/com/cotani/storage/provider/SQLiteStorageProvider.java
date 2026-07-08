package com.cotani.storage.provider;

import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.error.ConnectionError;
import com.cotani.storage.error.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Single-connection SQLite provider.
 *
 * <p>SQLite WAL allows many readers but only one writer; sharing one connection avoids the cost of
 * opening the database and applying PRAGMAs on every query. The PRAGMAs are applied once through
 * the JDBC URL, so every returned connection is already configured.
 */
public final class SQLiteStorageProvider implements StorageProvider {

    private static final String PRAGMA_JOURNAL_MODE = "PRAGMA journal_mode = WAL";

    private final SQLiteCredentials credentials;
    private final AtomicReference<@Nullable Connection> realConnection = new AtomicReference<>();
    private final AtomicReference<@Nullable Connection> connection = new AtomicReference<>();

    public SQLiteStorageProvider(SQLiteCredentials credentials) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    @Override
    public void start() {
        if (connection.get() != null) {
            return;
        }
        try {
            var parent = credentials.path().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new StorageException(new ConnectionError("Could not create SQLite directory.", exception));
        }
        try {
            Connection opened = DriverManager.getConnection(configuredJdbcUrl());
            try (Statement statement = opened.createStatement()) {
                statement.execute(PRAGMA_JOURNAL_MODE);
            }
            Connection proxy = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (p, method, args) -> {
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        try {
                            return method.invoke(opened, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
            if (connection.compareAndSet(null, proxy)) {
                realConnection.set(opened);
            } else {
                closeQuietly(opened);
            }
        } catch (SQLException exception) {
            throw new StorageException(new ConnectionError("Could not open SQLite connection.", exception));
        }
    }

    @Override
    public Connection connection() throws SQLException {
        Connection current = connection.get();
        if (current == null || current.isClosed()) {
            throw new StorageException(new ConnectionError("SQLite provider is not available.", null));
        }
        return current;
    }

    @Override
    public boolean available() {
        Connection current = connection.get();
        return current != null;
    }

    @Override
    public void close() {
        Connection real = realConnection.getAndSet(null);
        connection.set(null);
        closeQuietly(real);
    }

    private String configuredJdbcUrl() {
        return credentials.jdbcUrl()
                + "?busy_timeout=30000"
                + "&foreign_keys=ON"
                + "&journal_mode=WAL"
                + "&synchronous=NORMAL";
    }

    private void closeQuietly(@Nullable Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // best-effort close
        }
    }
}

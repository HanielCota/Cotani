package br.com.cotani.storage.provider;

import br.com.cotani.storage.backend.SQLiteCredentials;
import br.com.cotani.storage.error.ConnectionError;
import br.com.cotani.storage.error.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SQLiteStorageProvider implements StorageProvider {

    private static final int MAX_RETRY = 3;

    private final SQLiteCredentials credentials;
    private final AtomicReference<Connection> connection = new AtomicReference<>();
    private final AtomicBoolean started = new AtomicBoolean();

    public SQLiteStorageProvider(SQLiteCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public void start() {
        try {
            Path parent = credentials.path().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Connection opened = DriverManager.getConnection(credentials.jdbcUrl());
            try (Statement statement = opened.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA foreign_keys = ON");
            }
            this.connection.set(opened);
            this.started.set(true);
        } catch (IOException exception) {
            throw new StorageException(new ConnectionError("Could not create SQLite directory.", exception));
        } catch (SQLException exception) {
            throw new StorageException(new ConnectionError("Could not open SQLite connection.", exception));
        }
    }

    @Override
    public Connection connection() throws SQLException {
        if (!available()) {
            throw new StorageException(new ConnectionError("SQLite provider is not available.", null));
        }

        Connection current = connection.get();
        if (current != null && !current.isClosed()) {
            return current;
        }

        SQLException failure = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                Connection reopened = DriverManager.getConnection(credentials.jdbcUrl());
                try (Statement statement = reopened.createStatement()) {
                    statement.execute("PRAGMA journal_mode = WAL");
                    statement.execute("PRAGMA synchronous = NORMAL");
                    statement.execute("PRAGMA foreign_keys = ON");
                }
                this.connection.set(reopened);
                return reopened;
            } catch (SQLException exception) {
                failure = exception;
            }
        }

        throw new StorageException(new ConnectionError("Could not reopen SQLite connection.", failure));
    }

    @Override
    public boolean available() {
        return started.get();
    }

    @Override
    public void close() {
        this.started.set(false);
        Connection current = connection.getAndSet(null);
        if (current != null) {
            try {
                current.close();
            } catch (SQLException exception) {
                throw new StorageException(new ConnectionError("Could not close SQLite connection.", exception));
            } finally {
                this.connection.set(null);
            }
        }
    }
}

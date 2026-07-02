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
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SQLiteStorageProvider implements StorageProvider {

    private static final String PRAGMA_JOURNAL_MODE = "PRAGMA journal_mode = WAL";
    private static final String PRAGMA_SYNCHRONOUS = "PRAGMA synchronous = NORMAL";
    private static final String PRAGMA_FOREIGN_KEYS = "PRAGMA foreign_keys = ON";
    private static final String PRAGMA_BUSY_TIMEOUT = "PRAGMA busy_timeout = 30000";

    private final SQLiteCredentials credentials;
    private final AtomicBoolean started = new AtomicBoolean();
    private final Set<Connection> openConnections =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public SQLiteStorageProvider(SQLiteCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            var parent = credentials.path().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            started.set(false);
            throw new StorageException(new ConnectionError("Could not create SQLite directory.", exception));
        }
        try (Connection opened = DriverManager.getConnection(credentials.jdbcUrl());
                Statement statement = opened.createStatement()) {
            statement.execute(PRAGMA_JOURNAL_MODE);
            openConnections.add(opened);
        } catch (SQLException exception) {
            started.set(false);
            throw new StorageException(new ConnectionError("Could not open SQLite connection.", exception));
        }
    }

    @Override
    public Connection connection() throws SQLException {
        if (!started.get()) {
            throw new StorageException(new ConnectionError("SQLite provider is not available.", null));
        }
        Connection opened = DriverManager.getConnection(credentials.jdbcUrl());
        try (Statement statement = opened.createStatement()) {
            statement.execute(PRAGMA_SYNCHRONOUS);
            statement.execute(PRAGMA_FOREIGN_KEYS);
            statement.execute(PRAGMA_BUSY_TIMEOUT);
        } catch (SQLException failure) {
            closeQuietly(opened);
            throw failure;
        }
        openConnections.add(opened);
        return opened;
    }

    @Override
    public boolean available() {
        return started.get();
    }

    @Override
    public void close() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        synchronized (openConnections) {
            for (Connection connection : openConnections) {
                closeQuietly(connection);
            }
            openConnections.clear();
        }
    }

    private void closeQuietly(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // best-effort close
        }
    }
}

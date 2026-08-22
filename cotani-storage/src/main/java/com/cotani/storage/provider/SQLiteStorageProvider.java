package com.cotani.storage.provider;

import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.error.ConnectionError;
import com.cotani.storage.error.StorageException;
import com.cotani.storage.security.Paths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class SQLiteStorageProvider implements StorageProvider {
    private static final String PRAGMA_JOURNAL_MODE = "PRAGMA journal_mode = WAL";

    private final SQLiteCredentials credentials;
    private final AtomicReference<@Nullable Connection> realConnection = new AtomicReference<>();
    private final AtomicReference<@Nullable Connection> connection = new AtomicReference<>();

    public static SQLiteStorageProvider create(SQLiteCredentials credentials) {
        return new SQLiteStorageProvider(credentials);
    }

    private SQLiteStorageProvider(SQLiteCredentials credentials) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    @Override
    public void start() {
        if (connection.get() != null) {
            return;
        }
        final BasicFileAttributes beforeOpen;
        try {
            var verifiedPath = Paths.requireNoSymbolicLinks(credentials.path());
            var parent = verifiedPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }
            Paths.requireNoSymbolicLinks(verifiedPath);
            if (!Files.exists(verifiedPath, LinkOption.NOFOLLOW_LINKS)) {
                try (var _ = Files.newByteChannel(
                        verifiedPath,
                        Set.<OpenOption>of(
                                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                    // Create the final path without following a link before JDBC opens it.
                }
            }
            beforeOpen = Files.readAttributes(verifiedPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (!beforeOpen.isRegularFile()) {
                throw new IOException("SQLite path is not a regular file: " + verifiedPath);
            }
        } catch (IOException exception) {
            throw new StorageException(new ConnectionError("Could not securely prepare SQLite path.", exception));
        } catch (IllegalArgumentException invalidPath) {
            throw new StorageException(new ConnectionError("SQLite path contains a symbolic link.", invalidPath));
        }

        @Nullable Connection opened = null;

        try {
            opened = DriverManager.getConnection(configuredJdbcUrl());
            var verifiedPath = Paths.requireNoSymbolicLinks(credentials.path());
            var afterOpen = Files.readAttributes(verifiedPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (!Objects.equals(beforeOpen.fileKey(), afterOpen.fileKey())) {
                throw new StorageException(new ConnectionError("SQLite path changed while it was being opened.", null));
            }

            try (Statement statement = opened.createStatement()) {
                statement.execute(PRAGMA_JOURNAL_MODE);
            }

            Connection proxy = new NonClosingConnection(opened);

            if (!connection.compareAndSet(null, proxy)) {
                closeQuietly(opened);
                return;
            }

            realConnection.set(opened);
        } catch (StorageException failure) {
            closeQuietly(opened);
            throw failure;
        } catch (SQLException | IOException | IllegalArgumentException exception) {
            closeQuietly(opened);
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

    private record NonClosingConnection(Connection delegate) implements Connection {
        private NonClosingConnection(Connection delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void close() {
            // intentionally no-op; the real connection is closed by the provider
        }

        @Override
        public Statement createStatement() throws SQLException {
            return delegate.createStatement();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return delegate.prepareStatement(sql);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return delegate.prepareCall(sql);
        }

        @Override
        public String nativeSQL(String sql) throws SQLException {
            return delegate.nativeSQL(sql);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            delegate.setAutoCommit(autoCommit);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            return delegate.getAutoCommit();
        }

        @Override
        public void commit() throws SQLException {
            delegate.commit();
        }

        @Override
        public void rollback() throws SQLException {
            delegate.rollback();
        }

        @Override
        public boolean isClosed() throws SQLException {
            return delegate.isClosed();
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            return delegate.getMetaData();
        }

        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            delegate.setReadOnly(readOnly);
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            return delegate.isReadOnly();
        }

        @Override
        public void setCatalog(String catalog) throws SQLException {
            delegate.setCatalog(catalog);
        }

        @Override
        public String getCatalog() throws SQLException {
            return delegate.getCatalog();
        }

        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            delegate.setTransactionIsolation(level);
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            return delegate.getTransactionIsolation();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            return delegate.getWarnings();
        }

        @Override
        public void clearWarnings() throws SQLException {
            delegate.clearWarnings();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
                throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
                throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            return delegate.getTypeMap();
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
            delegate.setTypeMap(map);
        }

        @Override
        public void setHoldability(int holdability) throws SQLException {
            delegate.setHoldability(holdability);
        }

        @Override
        public int getHoldability() throws SQLException {
            return delegate.getHoldability();
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            return delegate.setSavepoint();
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            return delegate.setSavepoint(name);
        }

        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            delegate.rollback(savepoint);
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            delegate.releaseSavepoint(savepoint);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                throws SQLException {
            return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(
                String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public CallableStatement prepareCall(
                String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return delegate.prepareStatement(sql, autoGeneratedKeys);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return delegate.prepareStatement(sql, columnIndexes);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return delegate.prepareStatement(sql, columnNames);
        }

        @Override
        public Clob createClob() throws SQLException {
            return delegate.createClob();
        }

        @Override
        public Blob createBlob() throws SQLException {
            return delegate.createBlob();
        }

        @Override
        public NClob createNClob() throws SQLException {
            return delegate.createNClob();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            return delegate.createSQLXML();
        }

        @Override
        public boolean isValid(int timeout) throws SQLException {
            return delegate.isValid(timeout);
        }

        @Override
        public void setClientInfo(String name, String value) {
            try {
                delegate.setClientInfo(name, value);
            } catch (SQLClientInfoException ignored) {
                // best-effort
            }
        }

        @Override
        public void setClientInfo(Properties properties) {
            try {
                delegate.setClientInfo(properties);
            } catch (SQLClientInfoException ignored) {
                // best-effort
            }
        }

        @Override
        public String getClientInfo(String name) throws SQLException {
            return delegate.getClientInfo(name);
        }

        @Override
        public Properties getClientInfo() throws SQLException {
            return delegate.getClientInfo();
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            return delegate.createArrayOf(typeName, elements);
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            return delegate.createStruct(typeName, attributes);
        }

        @Override
        public void setSchema(String schema) throws SQLException {
            delegate.setSchema(schema);
        }

        @Override
        public String getSchema() throws SQLException {
            return delegate.getSchema();
        }

        @Override
        public void abort(Executor executor) throws SQLException {
            delegate.abort(executor);
        }

        @Override
        public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
            delegate.setNetworkTimeout(executor, milliseconds);
        }

        @Override
        public int getNetworkTimeout() throws SQLException {
            return delegate.getNetworkTimeout();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}

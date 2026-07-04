package com.cotani.storage.executor;

import com.cotani.storage.error.QueryError;
import com.cotani.storage.error.StorageException;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.query.EntityMapper;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.Row;
import com.cotani.storage.query.SqlConsumer;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class QueryExecutor {

    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    private final StorageProvider provider;
    private final Executor executor;
    private final ValueSerializerRegistry serializers;
    private final int queryTimeoutSeconds;
    private final @Nullable Connection transactionConnection;

    public QueryExecutor(StorageProvider provider, Executor executor, ValueSerializerRegistry serializers) {
        this(provider, executor, serializers, DEFAULT_QUERY_TIMEOUT_SECONDS, null);
    }

    public QueryExecutor(
            StorageProvider provider, Executor executor, ValueSerializerRegistry serializers, int queryTimeoutSeconds) {
        this(provider, executor, serializers, queryTimeoutSeconds, null);
    }

    public QueryExecutor(
            StorageProvider provider,
            Executor executor,
            ValueSerializerRegistry serializers,
            int queryTimeoutSeconds,
            @Nullable Connection transactionConnection) {
        if (queryTimeoutSeconds < 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds must not be negative, got " + queryTimeoutSeconds);
        }
        this.provider = Objects.requireNonNull(provider, "provider");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.transactionConnection = transactionConnection;
    }

    public CompletionStage<Void> update(String sql, SqlConsumer<ParameterBinder> binder) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        return CompletableFuture.runAsync(() -> runUpdate(sql, binder), executor);
    }

    public <T> CompletionStage<Optional<T>> queryOne(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        Objects.requireNonNull(mapper, "mapper");
        return CompletableFuture.supplyAsync(() -> runQueryOne(sql, binder, mapper), executor);
    }

    public <T> CompletionStage<List<T>> queryMany(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        Objects.requireNonNull(mapper, "mapper");
        return CompletableFuture.supplyAsync(() -> runQueryMany(sql, binder, mapper), executor);
    }

    public CompletionStage<Boolean> exists(String sql, SqlConsumer<ParameterBinder> binder) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        return CompletableFuture.supplyAsync(() -> runExists(sql, binder), executor);
    }

    public CompletionStage<Void> batch(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binders, "binders");
        return CompletableFuture.runAsync(() -> runBatch(sql, binders), executor);
    }

    public <T> CompletionStage<T> transaction(Function<QueryExecutor, CompletionStage<T>> operation) {
        Objects.requireNonNull(operation, "operation");
        return CompletableFuture.supplyAsync(this::beginTransaction, executor).thenCompose(state -> {
            var transactional =
                    new QueryExecutor(provider, Runnable::run, serializers, queryTimeoutSeconds, state.connection);
            return operation.apply(transactional).whenComplete((_, error) -> finishTransaction(state, error));
        });
    }

    private void runUpdate(String sql, SqlConsumer<ParameterBinder> binder) {
        try (ConnectionScope scope = connectionScope();
                PreparedStatement statement = scope.connection().prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            binder.accept(new ParameterBinder(statement, serializers));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute update query.", exception));
        } catch (RuntimeException exception) {
            throw new StorageException(
                    new com.cotani.storage.error.MappingError("Could not bind update parameters.", exception));
        }
    }

    private <T> Optional<T> runQueryOne(String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        try (ConnectionScope scope = connectionScope();
                PreparedStatement statement = scope.connection().prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            binder.accept(new ParameterBinder(statement, serializers));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapper.map(new Row(resultSet, serializers)));
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute select query.", exception));
        } catch (RuntimeException exception) {
            throw new StorageException(
                    new com.cotani.storage.error.MappingError("Could not map result row.", exception));
        }
    }

    private <T> List<T> runQueryMany(String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        try (ConnectionScope scope = connectionScope();
                PreparedStatement statement = scope.connection().prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            binder.accept(new ParameterBinder(statement, serializers));
            try (ResultSet resultSet = statement.executeQuery()) {
                var values = new ArrayList<T>();
                while (resultSet.next()) {
                    values.add(mapper.map(new Row(resultSet, serializers)));
                }
                return values;
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute list query.", exception));
        } catch (RuntimeException exception) {
            throw new StorageException(
                    new com.cotani.storage.error.MappingError("Could not map result row.", exception));
        }
    }

    private boolean runExists(String sql, SqlConsumer<ParameterBinder> binder) {
        try (ConnectionScope scope = connectionScope();
                PreparedStatement statement = scope.connection().prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            binder.accept(new ParameterBinder(statement, serializers));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute exists query.", exception));
        } catch (RuntimeException exception) {
            throw new StorageException(
                    new com.cotani.storage.error.MappingError("Could not bind exists parameters.", exception));
        }
    }

    private void runBatch(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        if (transactionConnection != null) {
            runBatchInExistingTransaction(sql, binders);
            return;
        }

        try (Connection connection = provider.connection()) {
            var previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(queryTimeoutSeconds);
                int count = 0;
                for (var item : binders) {
                    item.accept(new ParameterBinder(statement, serializers));
                    statement.addBatch();
                    count++;
                    if (count % 1000 == 0) {
                        statement.executeBatch();
                    }
                }
                if (count % 1000 != 0) {
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof SQLException sqlException) {
                    throw new StorageException(new QueryError("Could not execute batch query.", sqlException));
                }
                throw new StorageException(
                        new com.cotani.storage.error.MappingError("Could not bind batch parameters.", failure));
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // best-effort restore; connection is about to be closed by try-with-resources
                }
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not acquire connection for batch.", exception));
        }
    }

    private void runBatchInExistingTransaction(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        Connection connection = java.util.Objects.requireNonNull(transactionConnection, "transactionConnection");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            int count = 0;
            for (var item : binders) {
                item.accept(new ParameterBinder(statement, serializers));
                statement.addBatch();
                count++;
                if (count % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            if (count % 1000 != 0) {
                statement.executeBatch();
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute batch query.", exception));
        } catch (RuntimeException exception) {
            throw new StorageException(
                    new com.cotani.storage.error.MappingError("Could not bind batch parameters.", exception));
        }
    }

    private TransactionState beginTransaction() {
        try {
            Connection connection = provider.connection();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            return new TransactionState(connection, previousAutoCommit);
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not acquire connection for transaction.", exception));
        }
    }

    private void finishTransaction(TransactionState state, @Nullable Throwable error) {
        try (Connection connection = state.connection) {
            if (error != null) {
                rollback(connection, error);
            } else {
                connection.commit();
            }
        } catch (SQLException failure) {
            var wrapped = new StorageException(
                    new com.cotani.storage.error.TransactionError("Could not finish transaction.", failure));
            if (error != null) {
                error.addSuppressed(wrapped);
            } else {
                throw wrapped;
            }
        } finally {
            restoreAutoCommit(state.connection, state.previousAutoCommit);
        }
    }

    private void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(new StorageException(
                    new com.cotani.storage.error.TransactionError("Could not rollback transaction.", rollbackFailure)));
        }
    }

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
            // best-effort restore; connection is about to be closed by try-with-resources
        }
    }

    private ConnectionScope connectionScope() throws SQLException {
        if (transactionConnection != null) {
            return new ConnectionScope(transactionConnection, false);
        }
        return new ConnectionScope(provider.connection(), true);
    }

    private record TransactionState(Connection connection, boolean previousAutoCommit) {}

    private record ConnectionScope(Connection connection, boolean closeOnExit) implements AutoCloseable {

        @Override
        public void close() throws SQLException {
            if (closeOnExit) {
                connection.close();
            }
        }
    }
}

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
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class QueryExecutor {

    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;
    private static final String BINDER_PARAM = "binder";

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
        Objects.requireNonNull(binder, BINDER_PARAM);
        return runAsync(() -> runUpdate(sql, binder));
    }

    public <T> CompletionStage<Optional<T>> queryOne(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, BINDER_PARAM);
        Objects.requireNonNull(mapper, "mapper");
        return supplyAsync(() -> runQueryOne(sql, binder, mapper));
    }

    public <T> CompletionStage<List<T>> queryMany(
            String sql, SqlConsumer<ParameterBinder> binder, EntityMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, BINDER_PARAM);
        Objects.requireNonNull(mapper, "mapper");
        return supplyAsync(() -> runQueryMany(sql, binder, mapper));
    }

    public CompletionStage<Boolean> exists(String sql, SqlConsumer<ParameterBinder> binder) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, BINDER_PARAM);
        return supplyAsync(() -> runExists(sql, binder));
    }

    public CompletionStage<Void> batch(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binders, "binders");
        var snapshot = List.copyOf(binders);
        return runAsync(() -> runBatch(sql, snapshot));
    }

    public <T> CompletionStage<T> transaction(Function<QueryExecutor, CompletionStage<T>> operation) {
        Objects.requireNonNull(operation, "operation");
        if (transactionConnection != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Nested transactions are not supported."));
        }
        return supplyAsync(this::beginTransaction).thenCompose(state -> executeTransactionOperation(state, operation));
    }

    private CompletionStage<Void> runAsync(Runnable operation) {
        return supplyAsync(() -> {
            operation.run();
            return null;
        });
    }

    private <T> CompletionStage<T> supplyAsync(Supplier<T> operation) {
        var result = new CompletableFuture<T>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(operation.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (Throwable schedulingFailure) {
            result.completeExceptionally(schedulingFailure);
        }
        return result;
    }

    private <T> CompletionStage<T> executeTransactionOperation(
            TransactionState state, Function<QueryExecutor, CompletionStage<T>> operation) {
        var transactional =
                new QueryExecutor(provider, Runnable::run, serializers, queryTimeoutSeconds, state.connection);
        final CompletionStage<T> stage;
        try {
            stage = Objects.requireNonNull(operation.apply(transactional), "transaction operation returned null");
        } catch (Throwable failure) {
            finishTransaction(state, failure);
            return CompletableFuture.failedFuture(failure);
        }
        if (!stage.toCompletableFuture().isDone()) {
            var failure = new IllegalStateException(
                    "Transaction callbacks must not wait for external asynchronous work; compose only operations from the transactional QueryExecutor.");
            finishTransaction(state, failure);
            return CompletableFuture.failedFuture(failure);
        }
        return stage.whenComplete((_, error) -> finishTransaction(state, error));
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
                return List.copyOf(values);
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
            try {
                executeBatchWork(connection, sql, binders);
            } catch (SQLException | RuntimeException failure) {
                safeRollback(connection, failure);
                if (failure instanceof SQLException sqlException) {
                    throw new StorageException(new QueryError("Could not execute batch query.", sqlException));
                }
                throw new StorageException(
                        new com.cotani.storage.error.MappingError("Could not bind batch parameters.", failure));
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not acquire connection for batch.", exception));
        }
    }

    private void executeBatchWork(Connection connection, String sql, List<SqlConsumer<ParameterBinder>> binders)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            int count = 0;
            for (var item : binders) {
                statement.clearParameters();
                item.accept(new ParameterBinder(statement, serializers));
                statement.addBatch();
                count++;
                if (count % 1000 == 0) {
                    statement.executeBatch();
                    statement.clearBatch();
                }
            }
            if (count % 1000 != 0) {
                statement.executeBatch();
                statement.clearBatch();
            }
            connection.commit();
        }
    }

    private void safeRollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void runBatchInExistingTransaction(String sql, List<SqlConsumer<ParameterBinder>> binders) {
        Connection connection = Objects.requireNonNull(transactionConnection, "transactionConnection");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            int count = 0;
            for (var item : binders) {
                statement.clearParameters();
                item.accept(new ParameterBinder(statement, serializers));
                statement.addBatch();
                count++;
                if (count % 1000 == 0) {
                    statement.executeBatch();
                    statement.clearBatch();
                }
            }
            if (count % 1000 != 0) {
                statement.executeBatch();
                statement.clearBatch();
            }
        } catch (SQLException exception) {
            throw new StorageException(new QueryError("Could not execute batch query.", exception));
        } catch (RuntimeException exception) {
            throw new StorageException(
                    new com.cotani.storage.error.MappingError("Could not bind batch parameters.", exception));
        }
    }

    private TransactionState beginTransaction() {
        @Nullable Connection connection = null;
        try {
            connection = provider.connection();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            return new TransactionState(connection, previousAutoCommit);
        } catch (SQLException exception) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            throw new StorageException(new QueryError("Could not acquire connection for transaction.", exception));
        }
    }

    private void finishTransaction(TransactionState state, @Nullable Throwable error) {
        Connection connection = state.connection;
        try {
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
            try {
                restoreAutoCommit(connection, state.previousAutoCommit);
            } finally {
                closeQuietly(connection, error);
            }
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
            // best-effort restore before returning the connection to the pool
        }
    }

    private void closeQuietly(Connection connection, @Nullable Throwable error) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            var wrapped = new StorageException(new com.cotani.storage.error.TransactionError(
                    "Could not close transaction connection.", closeFailure));
            if (error != null) {
                error.addSuppressed(wrapped);
                return;
            }
            throw wrapped;
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

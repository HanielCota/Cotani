package com.cotani.storage.transaction;

import com.cotani.storage.error.StorageException;
import com.cotani.storage.error.TransactionError;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class TransactionManager {

    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    private final StorageProvider provider;
    private final Executor executor;
    private final ValueSerializerRegistry serializers;
    private final int queryTimeoutSeconds;

    public TransactionManager(StorageProvider provider, Executor executor, ValueSerializerRegistry serializers) {
        this(provider, executor, serializers, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    public TransactionManager(
            StorageProvider provider, Executor executor, ValueSerializerRegistry serializers, int queryTimeoutSeconds) {
        if (queryTimeoutSeconds < 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds must not be negative, got " + queryTimeoutSeconds);
        }
        this.provider = Objects.requireNonNull(provider, "provider");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public <T> CompletionStage<T> run(Function<TransactionContext, CompletionStage<T>> operation) {
        return runAsync(operation);
    }

    public <T> CompletionStage<T> runAsync(Function<TransactionContext, CompletionStage<T>> operation) {
        Objects.requireNonNull(operation, "operation");
        return CompletableFuture.supplyAsync(this::beginTransaction, executor)
                .thenCompose(state -> executeOperation(state, operation));
    }

    private <T> CompletionStage<T> executeOperation(
            TransactionState state, Function<TransactionContext, CompletionStage<T>> operation) {
        final CompletionStage<T> stage;
        try {
            stage = Objects.requireNonNull(operation.apply(state.context), "transaction operation returned null");
        } catch (Throwable failure) {
            finishTransaction(state, failure);
            return CompletableFuture.failedFuture(failure);
        }
        if (!stage.toCompletableFuture().isDone()) {
            var failure = new IllegalStateException(
                    "Transaction callbacks must not wait for external asynchronous work; compose only operations from the provided TransactionContext.");
            finishTransaction(state, failure);
            return CompletableFuture.failedFuture(failure);
        }
        return stage.whenComplete((_, error) -> finishTransaction(state, error));
    }

    private TransactionState beginTransaction() {
        @Nullable Connection connection = null;
        try {
            connection = provider.connection();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            var transactional =
                    new QueryExecutor(provider, Runnable::run, serializers, queryTimeoutSeconds, connection);
            return new TransactionState(connection, previousAutoCommit, new TransactionContext(transactional));
        } catch (SQLException exception) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            throw new StorageException(
                    new TransactionError("Could not acquire connection for transaction.", exception));
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
            var wrapped = new StorageException(new TransactionError("Could not finish transaction.", failure));
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
            failure.addSuppressed(
                    new StorageException(new TransactionError("Could not rollback transaction.", rollbackFailure)));
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
            var wrapped =
                    new StorageException(new TransactionError("Could not close transaction connection.", closeFailure));
            if (error != null) {
                error.addSuppressed(wrapped);
                return;
            }
            throw wrapped;
        }
    }

    private record TransactionState(Connection connection, boolean previousAutoCommit, TransactionContext context) {}
}

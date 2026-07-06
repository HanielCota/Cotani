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
        return CompletableFuture.supplyAsync(this::beginTransaction, executor)
                .thenCompose(state ->
                        operation.apply(state.context).whenComplete((_, error) -> finishTransaction(state, error)));
    }

    private TransactionState beginTransaction() {
        try {
            Connection connection = provider.connection();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            var transactional =
                    new QueryExecutor(provider, Runnable::run, serializers, queryTimeoutSeconds, connection);
            return new TransactionState(connection, previousAutoCommit, new TransactionContext(transactional));
        } catch (SQLException exception) {
            throw new StorageException(
                    new TransactionError("Could not acquire connection for transaction.", exception));
        }
    }

    private void finishTransaction(TransactionState state, @Nullable Throwable error) {
        try (Connection connection = state.connection) {
            if (error != null) {
                rollback(connection, error);
                return;
            }
            connection.commit();
        } catch (SQLException failure) {
            var wrapped = new StorageException(new TransactionError("Could not finish transaction.", failure));
            if (error != null) {
                error.addSuppressed(wrapped);
                return;
            }
            throw wrapped;
        } finally {
            restoreAutoCommit(state.connection, state.previousAutoCommit);
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
            // best-effort restore; connection is about to be closed by try-with-resources
        }
    }

    private record TransactionState(Connection connection, boolean previousAutoCommit, TransactionContext context) {}
}

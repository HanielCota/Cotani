package br.com.cotani.storage.transaction;

import br.com.cotani.storage.error.StorageException;
import br.com.cotani.storage.error.TransactionError;
import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.provider.StorageProvider;
import br.com.cotani.storage.query.SqlFunction;
import br.com.cotani.storage.scheduler.CotaniScheduler;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TransactionManager {

    private final StorageProvider provider;
    private final Executor executor;
    private final CotaniScheduler scheduler;

    public TransactionManager(StorageProvider provider, Executor executor, CotaniScheduler scheduler) {
        this.provider = provider;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    public <T> StorageFuture<T> run(SqlFunction<TransactionContext, T> operation) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> execute(operation), executor);
        return new StorageFuture<>(future, scheduler);
    }

    private <T> T execute(SqlFunction<TransactionContext, T> operation) {
        try (Connection connection = provider.connection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T value = operation.apply(new TransactionContext(connection));
                connection.commit();
                return value;
            } catch (Exception exception) {
                rollback(connection);
                throw exception;
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new StorageException(new TransactionError("Could not execute transaction.", exception));
        }
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            throw new StorageException(new TransactionError("Could not rollback transaction.", exception));
        }
    }

    private void restoreAutoCommit(Connection connection, boolean previousAutoCommit) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            throw new StorageException(new TransactionError("Could not restore autoCommit.", exception));
        }
    }
}

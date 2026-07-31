package com.cotani.storage.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.storage.error.StorageException;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@SuppressWarnings("NullAway")
class TransactionManagerTest {
    private final StorageProvider provider = mock(StorageProvider.class);
    private final Connection connection = mock(Connection.class);
    private final TransactionManager transactions =
            new TransactionManager(provider, Runnable::run, new ValueSerializerRegistry());

    @BeforeEach
    void setUp() throws Exception {
        when(provider.connection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
    }

    @Test
    void synchronousOperationFailureRollsBackRestoresAndCloses() throws Exception {
        IllegalStateException failure = new IllegalStateException("boom");

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> transactions
                        .run(_ -> {
                            throw failure;
                        })
                        .toCompletableFuture()
                        .join());

        assertSame(failure, thrown.getCause());
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).close();
        verify(connection, never()).commit();
    }

    @Test
    void nullOperationStageRollsBackRestoresAndCloses() throws Exception {
        assertThrows(
                CompletionException.class,
                () -> transactions.run(_ -> null).toCompletableFuture().join());

        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).close();
        verify(connection, never()).commit();
    }

    @Test
    void incompleteExternalStageIsRejectedAndConnectionIsReleased() throws Exception {
        var external = new CompletableFuture<String>();

        var failure = assertThrows(
                CompletionException.class,
                () -> transactions.run(_ -> external).toCompletableFuture().join());

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).close();
        verify(connection, never()).commit();
    }

    @Test
    void commitFailureStillRestoresAndClosesConnection() throws Exception {
        var commitFailure = new SQLException("commit failed");
        Mockito.doThrow(commitFailure).when(connection).commit();

        var failure = assertThrows(
                CompletionException.class,
                () -> transactions
                        .run(_ -> CompletableFuture.completedFuture("ok"))
                        .toCompletableFuture()
                        .join());

        assertInstanceOf(StorageException.class, failure.getCause());
        verify(connection).setAutoCommit(true);
        verify(connection).close();
    }

    @Test
    void rollbackFailureIsSuppressedOnOriginalFailure() throws Exception {
        var operationFailure = new IllegalStateException("operation failed");
        Mockito.doThrow(new SQLException("rollback failed")).when(connection).rollback();

        var failure = assertThrows(
                CompletionException.class,
                () -> transactions
                        .run(_ -> CompletableFuture.failedFuture(operationFailure))
                        .toCompletableFuture()
                        .join());

        assertSame(operationFailure, failure.getCause());
        assertEquals(1, operationFailure.getSuppressed().length);
        verify(connection).setAutoCommit(true);
        verify(connection).close();
    }
}

package com.cotani.storage.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.SqlConsumer;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class QueryExecutorTest {
    private final StorageProvider provider = mock(StorageProvider.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);

    @BeforeEach
    void setUp() throws Exception {
        when(provider.connection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.getAutoCommit()).thenReturn(true);
    }

    @Test
    void batchCopiesCallerListAndClearsParametersBetweenBinders() throws Exception {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        Executor controlled = queued::set;
        QueryExecutor executor = QueryExecutor.create(provider, controlled, new ValueSerializerRegistry());
        List<SqlConsumer<ParameterBinder>> binders = new ArrayList<>();
        binders.add(binder -> binder.set("first").set("second"));
        binders.add(binder -> binder.set("third"));

        CompletableFuture<Void> result = executor.batch("INSERT", binders).toCompletableFuture();
        binders.clear();
        Objects.requireNonNull(queued.get()).run();

        result.join();

        verify(statement, times(2)).clearParameters();
        verify(statement, times(3)).setObject(ArgumentMatchers.anyInt(), ArgumentMatchers.any());
        verify(statement).executeBatch();
        verify(statement).clearBatch();
    }

    @Test
    void batchClearsDriverBatchAfterEachThousandRows() throws Exception {
        QueryExecutor executor = QueryExecutor.create(provider, Runnable::run, new ValueSerializerRegistry());
        List<SqlConsumer<ParameterBinder>> binders = new ArrayList<>();

        for (int index = 0; index < 1_001; index++) {
            binders.add(binder -> binder.set("value"));
        }

        executor.batch("INSERT", binders).toCompletableFuture().join();

        verify(statement, times(1_001)).clearParameters();
        verify(statement, times(2)).executeBatch();
        verify(statement, times(2)).clearBatch();
    }

    @Test
    void queryManyReturnsImmutableList() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        QueryExecutor executor = QueryExecutor.create(provider, Runnable::run, new ValueSerializerRegistry());

        List<String> values = executor.queryMany("SELECT", _ -> {}, _ -> "value")
                .toCompletableFuture()
                .join();

        assertEquals(List.of("value"), values);
        assertThrows(UnsupportedOperationException.class, () -> values.add("other"));
        assertTrue(values.getClass().getName().contains("Immutable"));
    }
}

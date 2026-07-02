package com.cotani.storage.query;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import org.junit.jupiter.api.Test;

class DeleteQueryTest {

    @Test
    void executeRejectsMissingWhereWithoutAll() {
        var executor = createExecutor();
        var query = new DeleteQuery("test_table", executor);
        var future = query.execute();
        assertTrue(future.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void executeAcceptsWithWhere() {
        var executor = createExecutor();
        var query = new DeleteQuery("test_table", executor);
        query.where("id", 1);
        assertDoesNotThrow(() -> query.execute());
    }

    @Test
    void executeAcceptsAll() {
        var executor = createExecutor();
        var query = new DeleteQuery("test_table", executor);
        query.all();
        assertDoesNotThrow(() -> query.execute());
    }

    private static QueryExecutor createExecutor() {
        var provider = org.mockito.Mockito.mock(StorageProvider.class);
        org.mockito.Mockito.when(provider.available()).thenReturn(true);
        return new QueryExecutor(provider, Runnable::run, new ValueSerializerRegistry());
    }
}

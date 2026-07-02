package com.cotani.storage.query;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import org.junit.jupiter.api.Test;

class UpdateQueryTest {

    @Test
    void executeRejectsMissingWhereWithoutAll() {
        var executor = createExecutor();
        var query = new UpdateQuery("test_table", executor);
        query.set("name", "new");
        var future = query.execute();
        assertTrue(future.toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void executeAcceptsWithWhere() {
        var executor = createExecutor();
        var query = new UpdateQuery("test_table", executor);
        query.set("name", "new").where("id", 1);
        assertDoesNotThrow(() -> query.execute());
    }

    @Test
    void executeAcceptsAll() {
        var executor = createExecutor();
        var query = new UpdateQuery("test_table", executor);
        query.set("name", "new").all();
        assertDoesNotThrow(() -> query.execute());
    }

    private static QueryExecutor createExecutor() {
        var provider = org.mockito.Mockito.mock(StorageProvider.class);
        org.mockito.Mockito.when(provider.available()).thenReturn(true);
        return new QueryExecutor(provider, Runnable::run, new ValueSerializerRegistry());
    }
}

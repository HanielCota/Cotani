package com.cotani.storage.query;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.storage.dialect.SQLiteDialect;
import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import org.junit.jupiter.api.Test;

class UpsertQueryTest {

    @Test
    void executeAcceptsEmptyUpdates() {
        var executor = createExecutor();
        var dialect = new SQLiteDialect();
        var query = new UpsertQuery("test", executor, dialect);
        query.value("id", 1).value("name", "hello").conflict("id");
        assertDoesNotThrow(() -> query.execute());
    }

    @Test
    void executeRejectsUnknownConflictColumn() {
        var executor = createExecutor();
        var dialect = new SQLiteDialect();
        var query = new UpsertQuery("test", executor, dialect);
        query.value("id", 1).value("name", "hello").conflict("missing");
        assertThrows(IllegalArgumentException.class, () -> query.execute());
    }

    @Test
    void executeRejectsUnknownUpdateColumn() {
        var executor = createExecutor();
        var dialect = new SQLiteDialect();
        var query = new UpsertQuery("test", executor, dialect);
        query.value("id", 1).value("name", "hello").conflict("id").update("missing");
        assertThrows(IllegalArgumentException.class, () -> query.execute());
    }

    private static QueryExecutor createExecutor() {
        var provider = org.mockito.Mockito.mock(StorageProvider.class);
        org.mockito.Mockito.when(provider.available()).thenReturn(true);
        return new QueryExecutor(provider, Runnable::run, new ValueSerializerRegistry());
    }
}

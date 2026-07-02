package com.cotani.storage.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.storage.executor.QueryExecutor;
import com.cotani.storage.provider.StorageProvider;
import com.cotani.storage.schema.Schema;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class MigrationRunnerTest {

    private final QueryExecutor executor = createExecutor();
    private final Schema schema =
            new Schema(executor, org.mockito.Mockito.mock(com.cotani.storage.dialect.SqlDialect.class));

    @Test
    void rejectsDuplicateVersions() {
        var runner = new MigrationRunner(executor, schema);
        runner.add(migration(1, "first"));
        assertThrows(IllegalArgumentException.class, () -> runner.add(migration(1, "duplicate")));
    }

    @Test
    void acceptsDifferentVersions() {
        var runner = new MigrationRunner(executor, schema);
        runner.add(migration(1, "first"));
        assertDoesNotThrow(() -> runner.add(migration(2, "second")));
    }

    @SuppressWarnings("NullAway")
    private static Migration migration(int version, String description) {
        return new Migration() {
            @Override
            public int version() {
                return version;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public CompletionStage<Void> migrate(Schema schema) {
                return CompletableFuture.completedStage(null);
            }
        };
    }

    private static QueryExecutor createExecutor() {
        var provider = org.mockito.Mockito.mock(StorageProvider.class);
        return new QueryExecutor(provider, Runnable::run, new ValueSerializerRegistry());
    }
}
